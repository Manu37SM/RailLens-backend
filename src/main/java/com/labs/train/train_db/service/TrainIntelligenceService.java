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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrainIntelligenceService {

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

        private double routeComplexityScore(List<TrainSchedule> route, int totalDistanceKm, long journeyMinutes) {

                double raw = route.size() * 1.5
                                + totalDistanceKm / 100.0
                                + journeyMinutes / 60.0 * 2.0;

                return round1(Math.min(100.0, raw));
        }

        private double expressnessScore(List<TrainSchedule> route, int totalDistanceKm) {

                int halts = route.size() - 1;
                return halts <= 0 ? 0.0 : round1((double) totalDistanceKm / halts);
        }

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

        private double journeyEfficiencyIndex(int totalDistanceKm, long journeyMinutes) {

                if (journeyMinutes <= 0) {
                        return 0.0;
                }

                double averageSpeedKmh = totalDistanceKm / (journeyMinutes / 60.0);

                return Math.min(100.0, averageSpeedKmh / REFERENCE_MAX_SPEED_KMH * 100.0);
        }

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
