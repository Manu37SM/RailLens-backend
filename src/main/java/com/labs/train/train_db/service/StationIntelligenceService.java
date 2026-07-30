package com.labs.train.train_db.service;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.labs.train.train_db.entity.Station;
import com.labs.train.train_db.entity.TrainSchedule;
import com.labs.train.train_db.exception.ResourceNotFoundException;
import com.labs.train.train_db.model.StationIntelligenceResponse;
import com.labs.train.train_db.repository.StationRepository;
import com.labs.train.train_db.repository.TrainScheduleRepository;
import com.labs.train.train_db.service.network.RailwayNetworkService;
import com.labs.train.train_db.service.network.RailwayNetworkSnapshot;
import com.labs.train.train_db.service.network.StationNetworkNode;

import lombok.RequiredArgsConstructor;

/**
 * "Station Intelligence" scores (FEATURE.md) for a single station - the
 * per-station counterpart to TrainIntelligenceService. Connectivity/
 * centrality/rank reuse the shared network snapshot (RailwayNetworkService)
 * directly; average speed and the hourly departure/arrival histograms are
 * this station's own concern, since the network snapshot only records
 * adjacency (which stations connect), not per-hop distance/timing, and
 * isn't the right place to carry that - other network features don't need
 * it, and adding it there would mean recomputing it on every snapshot
 * build/cache-miss instead of only when a station is actually looked up.
 *
 * Like TrainIntelligenceService, computed on demand per station rather than
 * batch-precomputed - only ever requested for the one station a user is
 * currently viewing.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StationIntelligenceService {

        private final StationRepository stationRepository;
        private final TrainScheduleRepository trainScheduleRepository;
        private final RailwayNetworkService railwayNetworkService;

        public StationIntelligenceResponse getIntelligence(String stationCode) {

                Station station = stationRepository.findByStationCode(stationCode)
                                .orElseThrow(() -> new ResourceNotFoundException("Station not found: " + stationCode));

                RailwayNetworkSnapshot network = railwayNetworkService.buildSnapshot();
                StationNetworkNode node = network.station(stationCode);

                List<TrainSchedule> ownSchedules = trainScheduleRepository
                                .findByStation_StationCodeOrderByArrivalTime(stationCode);

                int[] departureByHour = new int[24];
                int[] arrivalByHour = new int[24];

                for (TrainSchedule schedule : ownSchedules) {
                        if (schedule.getDepartureTime() != null) {
                                departureByHour[schedule.getDepartureTime().getHour()]++;
                        }
                        if (schedule.getArrivalTime() != null) {
                                arrivalByHour[schedule.getArrivalTime().getHour()]++;
                        }
                }

                Double averageSpeed = averageTrainSpeedThroughStation(stationCode, ownSchedules);

                if (node == null) {
                        // Station exists in the stations table but has no schedule rows
                        // (e.g. just created via POST /stations) - real record, just
                        // nothing to analyze yet. Zeros rather than an error, same
                        // reasoning as StationResponse.trains being empty rather than
                        // getStation throwing.
                        return new StationIntelligenceResponse(
                                        station.getStationCode(), station.getStationName(),
                                        null, network.totalStations(),
                                        0.0, 0.0, 0.0, 0,
                                        0, 0, 0, 0,
                                        0.0, 0.0, 0.0,
                                        0.0, null,
                                        0.0,
                                        departureByHour, arrivalByHour);
                }

                int maxDegree = network.stations.values().stream()
                                .mapToInt((StationNetworkNode candidate) -> candidate.degree())
                                .max()
                                .orElse(0);

                double connectivityScore = maxDegree == 0
                                ? 0.0
                                : round1(Math.min(100.0, node.degree() * 100.0 / maxDegree));

                Integer rank = networkRank(network, stationCode);

                int totalStops = node.stopCount;
                double originPercent = percent(node.originCount, totalStops);
                double destinationPercent = percent(node.destinationCount, totalStops);
                double transitPercent = percent(node.transitCount, totalStops);

                int maxStopCount = network.stations.values().stream()
                                .mapToInt(candidate -> candidate.stopCount)
                                .max()
                                .orElse(0);

                double trafficScore = maxStopCount == 0 ? 0.0 : Math.min(100.0, totalStops * 100.0 / maxStopCount);
                double closenessScore = Math.min(100.0, node.closenessCentrality * 100.0);

                // Composite, documented judgment call (same spirit as
                // TrainIntelligenceService's routeComplexityScore): weighted
                // toward raw connectivity (how many distinct stations this one
                // links to directly) since that's the most concrete/comparable
                // signal, with reach (closeness) and traffic volume as
                // secondary factors.
                double importanceScore = round1(Math.min(100.0,
                                connectivityScore * 0.5 + closenessScore * 0.25 + trafficScore * 0.25));

                return new StationIntelligenceResponse(
                                station.getStationCode(), station.getStationName(),
                                rank, network.totalStations(),
                                connectivityScore,
                                round1(node.betweennessCentrality),
                                Math.round(node.closenessCentrality * 10000.0) / 10000.0,
                                node.degree(),
                                totalStops, node.originCount, node.destinationCount, node.transitCount,
                                originPercent, destinationPercent, transitPercent,
                                round1(node.averageHaltMinutes()),
                                averageSpeed,
                                importanceScore,
                                departureByHour, arrivalByHour);
        }

        // ------------------------------------------------------------

        private Integer networkRank(RailwayNetworkSnapshot network, String stationCode) {

                List<String> orderedByBetweenness = network.stations.values().stream()
                                .sorted(Comparator.comparingDouble(
                                                (StationNetworkNode candidate) -> candidate.betweennessCentrality).reversed())
                                .map(candidate -> candidate.stationCode)
                                .toList();

                int index = orderedByBetweenness.indexOf(stationCode);
                return index < 0 ? null : index + 1;
        }

        private double percent(int part, int total) {
                return total == 0 ? 0.0 : round1(part * 100.0 / total);
        }

        /**
         * Averages the speed of the segment immediately before and/or after
         * this station on every train that serves it - a station has no
         * speed of its own, only the hops touching it, so "average train
         * speed through this station" is necessarily an average over those
         * adjacent segments rather than a single measured value. Segments
         * crossing midnight are handled by adding 24h when the arrival clock
         * time is earlier than the departure clock time - a simplification
         * (it assumes at most a single day-rollover per segment, which holds
         * for any realistic single hop, unlike a full multi-day journey -
         * see JourneyDayCalculator for the more careful version used there).
         */
        private Double averageTrainSpeedThroughStation(String stationCode, List<TrainSchedule> ownSchedules) {

                if (ownSchedules.isEmpty()) {
                        return null;
                }

                List<Long> trainIds = ownSchedules.stream()
                                .map(schedule -> schedule.getTrain().getId())
                                .distinct()
                                .toList();

                Map<Long, List<TrainSchedule>> routesByTrain = trainScheduleRepository
                                .findByTrain_IdInOrderByTrain_IdAscSequenceNoAsc(trainIds)
                                .stream()
                                .collect(Collectors.groupingBy(schedule -> schedule.getTrain().getId()));

                double speedSum = 0.0;
                int speedCount = 0;

                for (TrainSchedule stationStop : ownSchedules) {

                        List<TrainSchedule> route = routesByTrain.get(stationStop.getTrain().getId());
                        int index = indexById(route, stationStop.getId());

                        if (index < 0) {
                                continue;
                        }

                        if (index > 0) {
                                Double speed = segmentSpeedKmh(route.get(index - 1), stationStop);
                                if (speed != null) {
                                        speedSum += speed;
                                        speedCount++;
                                }
                        }

                        if (index < route.size() - 1) {
                                Double speed = segmentSpeedKmh(stationStop, route.get(index + 1));
                                if (speed != null) {
                                        speedSum += speed;
                                        speedCount++;
                                }
                        }
                }

                return speedCount == 0 ? null : round1(speedSum / speedCount);
        }

        /**
         * Matches by entity id rather than {@code List#indexOf} on the entity
         * itself - both lists come from separate queries, and relying on
         * JPA's first-level-cache identity map to hand back the exact same
         * object instance across two different query methods (even within
         * one transaction) is more fragile than just comparing ids.
         */
        private int indexById(List<TrainSchedule> route, Long id) {

                for (int i = 0; i < route.size(); i++) {
                        if (route.get(i).getId().equals(id)) {
                                return i;
                        }
                }

                return -1;
        }

        private Double segmentSpeedKmh(TrainSchedule from, TrainSchedule to) {

                if (from.getDistance() == null || to.getDistance() == null
                                || from.getDepartureTime() == null || to.getArrivalTime() == null) {
                        return null;
                }

                int distanceKm = to.getDistance() - from.getDistance();

                if (distanceKm <= 0) {
                        return null;
                }

                LocalTime departure = from.getDepartureTime();
                LocalTime arrival = to.getArrivalTime();

                long minutes = Duration.between(departure, arrival).toMinutes();
                if (minutes < 0) {
                        minutes += Duration.ofHours(24).toMinutes();
                }

                if (minutes <= 0) {
                        return null;
                }

                return distanceKm / (minutes / 60.0);
        }

        private double round1(double value) {
                return Math.round(value * 10.0) / 10.0;
        }
}
