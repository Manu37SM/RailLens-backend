package com.labs.train.train_db.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.labs.train.train_db.config.CacheConfig;
import com.labs.train.train_db.entity.TrainSchedule;
import com.labs.train.train_db.model.AchievementsResponse;
import com.labs.train.train_db.model.AchievementsResponse.HiddenGemEntry;
import com.labs.train.train_db.model.AchievementsResponse.RareRouteEntry;
import com.labs.train.train_db.model.AchievementsResponse.SuperExpressEntry;
import com.labs.train.train_db.model.RouteDistanceProjection;
import com.labs.train.train_db.model.TrainSpeedProjection;
import com.labs.train.train_db.repository.TrainScheduleRepository;
import com.labs.train.train_db.service.network.RailwayNetworkSnapshot;
import com.labs.train.train_db.service.network.RailwayNetworkService;
import com.labs.train.train_db.service.network.StationNetworkNode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AchievementsService {

        private static final int TOP_LIST_SIZE = 100;
        private static final int AWARD_LIST_SIZE = 10;
        private static final int MEGA_ROUTE_THRESHOLD_KM = 3000;

        private final TrainScheduleRepository trainScheduleRepository;
        private final JourneyDayCalculator journeyDayCalculator;
        private final RailwayNetworkService railwayNetworkService;
        private final ScheduleSnapshotService scheduleSnapshotService;

        @Cacheable(cacheNames = CacheConfig.ACHIEVEMENTS_CACHE)
        public AchievementsResponse getAchievements() {

                List<RouteDistanceProjection> longestRoutes = trainScheduleRepository
                                .findRouteDistancesDescending(PageRequest.of(0, TOP_LIST_SIZE));

                List<TrainSchedule> allSchedules = scheduleSnapshotService.getAllOrderedByTrainThenSequence();

                List<TrainSpeedProjection> trainSpeeds = TrainSpeedCalculator.computeAll(
                                allSchedules, journeyDayCalculator);

                List<TrainSpeedProjection> fastestTrains = trainSpeeds.stream()
                                .sorted(Comparator.comparingDouble((TrainSpeedProjection p) -> p.averageSpeedKmh()).reversed())
                                .limit(TOP_LIST_SIZE)
                                .toList();

                List<RouteDistanceProjection> megaRoutes = longestRoutes.stream()
                                .filter(route -> route.distanceKm() != null && route.distanceKm() > MEGA_ROUTE_THRESHOLD_KM)
                                .toList();

                Map<Long, List<TrainSchedule>> routesByTrainId = allSchedules
                                .stream()
                                .collect(Collectors.groupingBy(schedule -> schedule.getTrain().getId()));

                List<SuperExpressEntry> superExpress = superExpressRankings(routesByTrainId);

                RailwayNetworkSnapshot network = railwayNetworkService.buildSnapshot();
                List<RareRouteEntry> rareRoutes = rareRoutes(routesByTrainId, network);

                List<HiddenGemEntry> hiddenGems = hiddenGems(trainSpeeds);

                return new AchievementsResponse(
                                longestRoutes, fastestTrains, megaRoutes, superExpress, rareRoutes, hiddenGems);
        }

        private List<SuperExpressEntry> superExpressRankings(Map<Long, List<TrainSchedule>> routesByTrainId) {

                List<SuperExpressEntry> entries = new ArrayList<>();

                for (List<TrainSchedule> route : routesByTrainId.values()) {

                        if (route.size() < 3) {
                                continue;
                        }

                        Integer firstDistance = route.get(0).getDistance();
                        Integer lastDistance = route.get(route.size() - 1).getDistance();

                        if (firstDistance == null || lastDistance == null) {
                                continue;
                        }

                        int distanceKm = lastDistance - firstDistance;
                        int halts = route.size() - 2;

                        if (distanceKm <= 0 || halts <= 0) {
                                continue;
                        }

                        entries.add(new SuperExpressEntry(
                                        route.get(0).getTrain().getTrainNumber(),
                                        route.get(0).getTrain().getTrainName(),
                                        Math.round((double) distanceKm / halts * 10.0) / 10.0));
                }

                return entries.stream()
                                .sorted(Comparator.comparingDouble((SuperExpressEntry entry) -> entry.kmPerHalt()).reversed())
                                .limit(AWARD_LIST_SIZE)
                                .toList();
        }

        private List<RareRouteEntry> rareRoutes(
                        Map<Long, List<TrainSchedule>> routesByTrainId, RailwayNetworkSnapshot network) {

                List<RareRouteEntry> entries = new ArrayList<>();

                for (List<TrainSchedule> route : routesByTrainId.values()) {

                        if (route.size() < 2) {
                                continue;
                        }

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

                                sum += trainsOnHop;
                                hops++;
                        }

                        if (hops == 0) {
                                continue;
                        }

                        entries.add(new RareRouteEntry(
                                        route.get(0).getTrain().getTrainNumber(),
                                        route.get(0).getTrain().getTrainName(),
                                        Math.round(sum / hops * 100.0) / 100.0));
                }

                return entries.stream()
                                .sorted(Comparator.comparingDouble((RareRouteEntry entry) -> entry.averageTrainsPerHop()))
                                .limit(AWARD_LIST_SIZE)
                                .toList();
        }

        private List<HiddenGemEntry> hiddenGems(List<TrainSpeedProjection> trainSpeeds) {

                if (trainSpeeds.isEmpty()) {
                        return List.of();
                }

                double averageSpeed = trainSpeeds.stream()
                                .mapToDouble((TrainSpeedProjection p) -> p.averageSpeedKmh())
                                .average()
                                .orElse(0.0);

                double averageDistance = trainSpeeds.stream()
                                .mapToInt((TrainSpeedProjection p) -> p.distanceKm())
                                .average()
                                .orElse(0.0);

                Set<String> topFastest = trainSpeeds.stream()
                                .sorted(Comparator.comparingDouble((TrainSpeedProjection p) -> p.averageSpeedKmh()).reversed())
                                .limit(AWARD_LIST_SIZE)
                                .map(p -> p.trainNumber())
                                .collect(Collectors.toCollection(HashSet::new));

                Set<String> topLongest = trainSpeeds.stream()
                                .sorted(Comparator.comparingInt((TrainSpeedProjection p) -> p.distanceKm()).reversed())
                                .limit(AWARD_LIST_SIZE)
                                .map(p -> p.trainNumber())
                                .collect(Collectors.toCollection(HashSet::new));

                return trainSpeeds.stream()
                                .filter(p -> p.averageSpeedKmh() > averageSpeed && p.distanceKm() > averageDistance)
                                .filter(p -> !topFastest.contains(p.trainNumber()) && !topLongest.contains(p.trainNumber()))
                                .sorted(Comparator.comparingDouble(
                                                (TrainSpeedProjection p) -> p.averageSpeedKmh() * p.distanceKm()).reversed())
                                .limit(AWARD_LIST_SIZE)
                                .map(p -> new HiddenGemEntry(p.trainNumber(), p.trainName(), p.distanceKm(), p.averageSpeedKmh()))
                                .toList();
        }
}
