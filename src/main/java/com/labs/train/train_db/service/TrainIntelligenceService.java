package com.labs.train.train_db.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.labs.train.train_db.entity.TrainSchedule;
import com.labs.train.train_db.exception.ResourceNotFoundException;
import com.labs.train.train_db.model.TrainIntelligenceResponse;
import com.labs.train.train_db.repository.TrainScheduleRepository;
import com.labs.train.train_db.service.network.RailwayNetworkService;
import com.labs.train.train_db.service.network.RailwayNetworkSnapshot;
import com.labs.train.train_db.service.network.StationNetworkNode;

import lombok.RequiredArgsConstructor;

/**
 * "Train Intelligence" scores (FEATURE.md) for one train at a time - unlike
 * RailwayNetworkService's snapshot (built once for the whole network),
 * these are computed on demand per train, since they're only ever requested
 * for the one train a user is currently looking at (train details page),
 * not batch-precomputed for all ~8,000+ trains up front.
 *
 * Several of these metrics (route complexity, journey efficiency, train
 * uniqueness, station skipping) have no single agreed-upon definition -
 * where that's true, the formula used is a documented judgment call, not a
 * claim of statistical rigor. Grounded, comparable-across-trains numbers
 * (night/day %, halt duration, longest non-stop segment) are computed
 * exactly from the schedule data.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrainIntelligenceService {

        // Typical top speed for Indian mail/express services - used only to
        // normalize journeyEfficiencyIndex onto a 0-100 scale relative to
        // "about as fast as trains on this network get," not a claim about
        // any specific train's engineering limit.
        private static final double REFERENCE_MAX_SPEED_KMH = 130.0;

        private final TrainScheduleRepository trainScheduleRepository;
        private final JourneyDayCalculator journeyDayCalculator;
        private final RailwayNetworkService railwayNetworkService;

        public TrainIntelligenceResponse getIntelligence(String trainNumber) {

                List<TrainSchedule> route = trainScheduleRepository
                                .findByTrain_TrainNumberOrderBySequenceNo(trainNumber);

                if (route.isEmpty()) {
                        throw new ResourceNotFoundException("Train not found: " + trainNumber);
                }

                String trainName = route.get(0).getTrain().getTrainName();

                List<Integer> journeyDays = journeyDayCalculator.computeJourneyDays(route);

                int totalDistanceKm = totalDistance(route);
                long journeyMinutes = totalJourneyMinutes(route, journeyDays);

                double routeComplexity = routeComplexityScore(route, totalDistanceKm, journeyMinutes);
                double expressness = expressnessScore(route, totalDistanceKm);
                double[] nightDaySplit = nightAndDayPercent(route, journeyDays);
                LongestSegment longestSegment = longestNonStopSegment(route);
                double avgHalt = averageHaltMinutes(route);
                double efficiency = journeyEfficiencyIndex(totalDistanceKm, journeyMinutes);

                RailwayNetworkSnapshot network = railwayNetworkService.buildSnapshot();
                double uniqueness = trainUniquenessScore(route, network);
                List<String> skipped = possiblySkippedStations(route, network);

                boolean isCircular = route.get(0).getStation().getStationCode()
                                .equals(route.get(route.size() - 1).getStation().getStationCode());

                return new TrainIntelligenceResponse(
                                trainNumber,
                                trainName,
                                routeComplexity,
                                uniqueness,
                                expressness,
                                round1(nightDaySplit[0]),
                                round1(nightDaySplit[1]),
                                longestSegment.distanceKm(),
                                longestSegment.fromStation(),
                                longestSegment.toStation(),
                                round1(avgHalt),
                                round1(efficiency),
                                isCircular,
                                skipped);
        }

        // ------------------------------------------------------------

        private int totalDistance(List<TrainSchedule> route) {

                Integer first = route.get(0).getDistance();
                Integer last = route.get(route.size() - 1).getDistance();

                if (first == null || last == null) {
                        return 0;
                }

                return Math.max(0, last - first);
        }

        private long totalJourneyMinutes(List<TrainSchedule> route, List<Integer> journeyDays) {

                TrainSchedule first = route.get(0);
                TrainSchedule last = route.get(route.size() - 1);

                if (first.getDepartureTime() == null || last.getArrivalTime() == null) {
                        return 0;
                }

                long minutes = journeyDayCalculator.minutesBetween(
                                journeyDays.get(0), first.getDepartureTime(),
                                journeyDays.get(journeyDays.size() - 1), last.getArrivalTime());

                return Math.max(0, minutes);
        }

        /**
         * A single 0-100 "how involved is this journey" number, weighted
         * toward stop count (the thing a passenger most directly experiences)
         * with distance and duration as secondary factors. Not a scientific
         * formula - a documented judgment call, capped at 100 so an
         * exceptionally long train (e.g. a multi-day route) doesn't produce
         * a number that reads as a bug.
         */
        private double routeComplexityScore(List<TrainSchedule> route, int totalDistanceKm, long journeyMinutes) {

                double raw = route.size() * 1.5
                                + totalDistanceKm / 100.0
                                + journeyMinutes / 60.0 * 2.0;

                return round1(Math.min(100.0, raw));
        }

        /**
         * Average distance covered between consecutive halts - a simple,
         * unambiguous proxy for "how non-stop does this train feel": a train
         * that covers a lot of ground between each stop reads as more
         * "express" than one that stops every few kilometers, independent of
         * its absolute speed.
         */
        private double expressnessScore(List<TrainSchedule> route, int totalDistanceKm) {

                int halts = route.size() - 1;
                return halts <= 0 ? 0.0 : round1((double) totalDistanceKm / halts);
        }

        /**
         * Walks every leg (halt-to-halt, not stop-to-stop - the "moving"
         * time between one departure and the next arrival) and sums how many
         * minutes of each fall inside night hours (21:00-06:00, a
         * conventional definition - IRCTC and most Indian journey planners
         * use a similar 21:00/22:00-06:00 window for "night travel"
         * warnings). Multi-day legs are handled by checking each calendar
         * day the leg spans against that day's own night windows, not just
         * the first/last day - see #nightMinutesInRange.
         */
        private double[] nightAndDayPercent(List<TrainSchedule> route, List<Integer> journeyDays) {

                long totalMinutes = 0;
                long nightMinutes = 0;

                LocalDate base = LocalDate.of(2000, 1, 1);

                for (int i = 0; i < route.size() - 1; i++) {

                        TrainSchedule from = route.get(i);
                        TrainSchedule to = route.get(i + 1);

                        if (from.getDepartureTime() == null || to.getArrivalTime() == null) {
                                continue;
                        }

                        LocalDateTime legStart = LocalDateTime.of(
                                        base.plusDays(journeyDays.get(i) - 1), from.getDepartureTime());
                        LocalDateTime legEnd = LocalDateTime.of(
                                        base.plusDays(journeyDays.get(i + 1) - 1), to.getArrivalTime());

                        if (!legEnd.isAfter(legStart)) {
                                continue;
                        }

                        totalMinutes += Duration.between(legStart, legEnd).toMinutes();
                        nightMinutes += NightWindowCalculator.nightMinutesInRange(legStart, legEnd);
                }

                if (totalMinutes == 0) {
                        return new double[] { 0.0, 0.0 };
                }

                double nightPercent = nightMinutes * 100.0 / totalMinutes;
                return new double[] { nightPercent, 100.0 - nightPercent };
        }

        private record LongestSegment(Integer distanceKm, String fromStation, String toStation) {
        }

        private LongestSegment longestNonStopSegment(List<TrainSchedule> route) {

                Integer bestDistance = null;
                String fromStation = null;
                String toStation = null;

                for (int i = 0; i < route.size() - 1; i++) {

                        TrainSchedule from = route.get(i);
                        TrainSchedule to = route.get(i + 1);

                        if (from.getDistance() == null || to.getDistance() == null) {
                                continue;
                        }

                        int segmentDistance = to.getDistance() - from.getDistance();

                        if (bestDistance == null || segmentDistance > bestDistance) {
                                bestDistance = segmentDistance;
                                fromStation = from.getStation().getStationCode();
                                toStation = to.getStation().getStationCode();
                        }
                }

                return new LongestSegment(bestDistance, fromStation, toStation);
        }

        private double averageHaltMinutes(List<TrainSchedule> route) {

                long sum = 0;
                int count = 0;

                for (int i = 1; i < route.size() - 1; i++) {

                        TrainSchedule stop = route.get(i);

                        if (stop.getArrivalTime() == null || stop.getDepartureTime() == null
                                        || stop.getDepartureTime().isBefore(stop.getArrivalTime())) {
                                continue;
                        }

                        sum += Duration.between(stop.getArrivalTime(), stop.getDepartureTime()).toMinutes();
                        count++;
                }

                return count == 0 ? 0.0 : (double) sum / count;
        }

        /**
         * Average speed normalized against a fixed reference ceiling (see
         * REFERENCE_MAX_SPEED_KMH) rather than against other trains in the
         * dataset - stable across dataset updates/imports, unlike a
         * percentile-based score that would silently shift every time the
         * fastest train in the data changes.
         */
        private double journeyEfficiencyIndex(int totalDistanceKm, long journeyMinutes) {

                if (journeyMinutes <= 0) {
                        return 0.0;
                }

                double averageSpeedKmh = totalDistanceKm / (journeyMinutes / 60.0);

                return Math.min(100.0, averageSpeedKmh / REFERENCE_MAX_SPEED_KMH * 100.0);
        }

        /**
         * How "exclusive" this train's route is: for every direct hop
         * (consecutive pair of stops), look up how many distinct trains
         * (from the shared network snapshot) make that exact hop, including
         * this one. A hop only this train makes scores 1.0 (fully unique); a
         * hop shared by N trains scores 1/N. Averaged across every hop, then
         * scaled to 0-100. A train whose entire route is shared with many
         * others (e.g. a short suburban hop repeated by dozens of services)
         * scores low; a train serving an otherwise-unconnected corridor
         * scores high.
         */
        private double trainUniquenessScore(List<TrainSchedule> route, RailwayNetworkSnapshot network) {

                double sum = 0.0;
                int hops = 0;

                for (int i = 0; i < route.size() - 1; i++) {

                        String fromCode = route.get(i).getStation().getStationCode();
                        String toCode = route.get(i + 1).getStation().getStationCode();

                        StationNetworkNode fromNode = network.station(fromCode);

                        if (fromNode == null) {
                                continue;
                        }

                        Integer trainsOnHop = fromNode.neighborTrainCounts.get(toCode);

                        if (trainsOnHop == null || trainsOnHop <= 0) {
                                continue;
                        }

                        sum += 1.0 / trainsOnHop;
                        hops++;
                }

                return hops == 0 ? 0.0 : round1(sum / hops * 100.0);
        }

        /**
         * Heuristic, not a guarantee: for each direct hop A -> B this train
         * makes, a station C is flagged as "possibly skipped" if C is a
         * direct network neighbor of BOTH A and B (i.e. some other train(s)
         * stop at C right after leaving A, and some other train(s) stop at C
         * right before reaching B) - meaning C plausibly sits between A and
         * B geographically, and this train's nonstop hop bypasses it. This
         * does not confirm C is literally between A and B on the same
         * physical track (the network graph has no geo-coordinates to check
         * that against - see the Maps section of FEATURE.md), so it's
         * presented as "possibly skipped," not a certainty.
         */
        private List<String> possiblySkippedStations(List<TrainSchedule> route, RailwayNetworkSnapshot network) {

                Set<String> skipped = new LinkedHashSet<>();

                Set<String> onThisRoute = new LinkedHashSet<>();
                for (TrainSchedule stop : route) {
                        onThisRoute.add(stop.getStation().getStationCode());
                }

                for (int i = 0; i < route.size() - 1; i++) {

                        String fromCode = route.get(i).getStation().getStationCode();
                        String toCode = route.get(i + 1).getStation().getStationCode();

                        StationNetworkNode fromNode = network.station(fromCode);
                        StationNetworkNode toNode = network.station(toCode);

                        if (fromNode == null || toNode == null) {
                                continue;
                        }

                        for (String candidate : fromNode.neighborTrainCounts.keySet()) {

                                if (onThisRoute.contains(candidate)) {
                                        continue;
                                }

                                if (toNode.neighborTrainCounts.containsKey(candidate)) {
                                        skipped.add(candidate);
                                }
                        }
                }

                return new ArrayList<>(skipped);
        }

        private double round1(double value) {
                return Math.round(value * 10.0) / 10.0;
        }
}
