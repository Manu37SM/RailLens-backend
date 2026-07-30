package com.labs.train.train_db.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.labs.train.train_db.config.CacheConfig;
import com.labs.train.train_db.entity.TrainSchedule;
import com.labs.train.train_db.model.RankingsResponse;
import com.labs.train.train_db.model.RankingsResponse.HaltCountEntry;
import com.labs.train.train_db.model.RankingsResponse.HaltDurationEntry;
import com.labs.train.train_db.model.RankingsResponse.StationCountEntry;
import com.labs.train.train_db.repository.TrainScheduleRepository;
import com.labs.train.train_db.service.network.RailwayNetworkSnapshot;
import com.labs.train.train_db.service.network.RailwayNetworkService;
import com.labs.train.train_db.service.network.StationNetworkNode;

import lombok.RequiredArgsConstructor;

/**
 * "Rankings" leaderboards (FEATURE.md) - most/fewest halts per train,
 * longest/shortest individual halt, most popular origin stations, and most
 * connected stations. See StatsService for the pre-existing fastest/
 * slowest-train and busiest-station leaderboards this deliberately doesn't
 * duplicate.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingsService {

        // Same reasoning as StatsService.RANKED_LIST_SIZE - a public,
        // rate-limited endpoint, nobody needs more than a top-10 view.
        private static final int RANKED_LIST_SIZE = 10;

        private final TrainScheduleRepository trainScheduleRepository;
        private final RailwayNetworkService railwayNetworkService;

        @Cacheable(cacheNames = CacheConfig.RANKINGS_CACHE)
        public RankingsResponse getRankings() {

                Map<Long, List<TrainSchedule>> schedulesByTrainId = trainScheduleRepository
                                .findAllByOrderByTrain_IdAscSequenceNoAsc()
                                .stream()
                                .collect(Collectors.groupingBy(schedule -> schedule.getTrain().getId()));

                List<HaltCountEntry> haltCounts = new ArrayList<>();
                List<HaltDurationEntry> haltDurations = new ArrayList<>();

                for (List<TrainSchedule> route : schedulesByTrainId.values()) {

                        if (route.isEmpty()) {
                                continue;
                        }

                        TrainSchedule first = route.get(0);

                        int haltCount = Math.max(0, route.size() - 2);
                        haltCounts.add(new HaltCountEntry(
                                        first.getTrain().getTrainNumber(), first.getTrain().getTrainName(), haltCount));

                        for (int i = 1; i < route.size() - 1; i++) {

                                TrainSchedule stop = route.get(i);

                                if (stop.getArrivalTime() == null || stop.getDepartureTime() == null
                                                || stop.getDepartureTime().isBefore(stop.getArrivalTime())) {
                                        continue;
                                }

                                long minutes = Duration.between(stop.getArrivalTime(), stop.getDepartureTime()).toMinutes();

                                haltDurations.add(new HaltDurationEntry(
                                                first.getTrain().getTrainNumber(), first.getTrain().getTrainName(),
                                                stop.getStation().getStationCode(), stop.getStation().getStationName(),
                                                minutes));
                        }
                }

                // Explicit lambdas rather than HaltCountEntry::haltCount /
                // HaltDurationEntry::minutes - same JDT null-analyzer
                // "unchecked conversion for the receiver" false positive on
                // record-accessor method references noted elsewhere in this
                // codebase (see StatsService); behaviorally identical.
                List<HaltCountEntry> mostHalts = haltCounts.stream()
                                .sorted(Comparator.comparingInt((HaltCountEntry entry) -> entry.haltCount()).reversed())
                                .limit(RANKED_LIST_SIZE)
                                .toList();

                List<HaltCountEntry> fewestHalts = haltCounts.stream()
                                .sorted(Comparator.comparingInt((HaltCountEntry entry) -> entry.haltCount()))
                                .limit(RANKED_LIST_SIZE)
                                .toList();

                List<HaltDurationEntry> longestHalts = haltDurations.stream()
                                .sorted(Comparator.comparingLong((HaltDurationEntry entry) -> entry.minutes()).reversed())
                                .limit(RANKED_LIST_SIZE)
                                .toList();

                // Zero-minute "technical halts" (arrival == departure) are
                // excluded from "shortest halt" - they're not a meaningful stop
                // duration a passenger experiences, just noise that would
                // otherwise dominate this particular leaderboard.
                List<HaltDurationEntry> shortestHalts = haltDurations.stream()
                                .filter(entry -> entry.minutes() > 0)
                                .sorted(Comparator.comparingLong((HaltDurationEntry entry) -> entry.minutes()))
                                .limit(RANKED_LIST_SIZE)
                                .toList();

                RailwayNetworkSnapshot network = railwayNetworkService.buildSnapshot();

                List<StationCountEntry> mostPopularOrigins = network.stations.values().stream()
                                .sorted(Comparator.comparingInt(
                                                (StationNetworkNode node) -> node.originCount).reversed())
                                .limit(RANKED_LIST_SIZE)
                                .map(node -> new StationCountEntry(node.stationCode, node.stationName, node.originCount))
                                .toList();

                List<StationCountEntry> mostConnected = network.stations.values().stream()
                                .sorted(Comparator.comparingInt(
                                                (StationNetworkNode node) -> node.degree()).reversed())
                                .limit(RANKED_LIST_SIZE)
                                .map(node -> new StationCountEntry(node.stationCode, node.stationName, node.degree()))
                                .toList();

                return new RankingsResponse(
                                mostHalts, fewestHalts, longestHalts, shortestHalts, mostPopularOrigins, mostConnected);
        }
}
