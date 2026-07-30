package com.labs.train.train_db.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.labs.train.train_db.config.CacheConfig;
import com.labs.train.train_db.entity.TrainSchedule;
import com.labs.train.train_db.repository.TrainScheduleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Every train's stop set, distance, journey duration, and halt count, built
 * once from a single full-table scan - backs SmartSearchService's
 * filtering. A separate bean (rather than a private method on
 * SmartSearchService) specifically so {@link #buildIndex()}'s
 * {@code @Cacheable} actually takes effect: Spring's caching is proxy-
 * based, so a method calling its own {@code @Cacheable} method
 * (self-invocation) bypasses the proxy and silently never caches - the same
 * reason RailwayNetworkService.buildSnapshot() is its own bean rather than
 * a private method other services call internally.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class TrainSummaryIndex {

        private final TrainScheduleRepository trainScheduleRepository;
        private final JourneyDayCalculator journeyDayCalculator;

        record TrainSummary(
                        String trainNumber, String trainName, Set<String> stationCodes,
                        int distanceKm, long journeyMinutes, int halts) {
        }

        /**
         * Cached under SEARCH_INDEX_CACHE (the "small, bounded, whole-table
         * candidate list" cache already used for the fuzzy-search fallback)
         * rather than a new cache - this is the same kind of thing, just for
         * train filtering instead of name matching.
         */
        @Cacheable(cacheNames = CacheConfig.SEARCH_INDEX_CACHE, key = "'trainSummaries'")
        List<TrainSummary> buildIndex() {

                Map<Long, List<TrainSchedule>> schedulesByTrainId = trainScheduleRepository
                                .findAllByOrderByTrain_IdAscSequenceNoAsc()
                                .stream()
                                .collect(Collectors.groupingBy(schedule -> schedule.getTrain().getId()));

                List<TrainSummary> summaries = new ArrayList<>();

                for (List<TrainSchedule> route : schedulesByTrainId.values()) {

                        if (route.isEmpty()) {
                                continue;
                        }

                        TrainSchedule first = route.get(0);
                        TrainSchedule last = route.get(route.size() - 1);

                        int distanceKm = (first.getDistance() != null && last.getDistance() != null)
                                        ? Math.max(0, last.getDistance() - first.getDistance())
                                        : 0;

                        long journeyMinutes = 0;

                        if (first.getDepartureTime() != null && last.getArrivalTime() != null) {

                                List<Integer> journeyDays = journeyDayCalculator.computeJourneyDays(route);

                                journeyMinutes = Math.max(0, journeyDayCalculator.minutesBetween(
                                                journeyDays.get(0), first.getDepartureTime(),
                                                journeyDays.get(journeyDays.size() - 1), last.getArrivalTime()));
                        }

                        Set<String> stationCodes = route.stream()
                                        .map(schedule -> schedule.getStation().getStationCode())
                                        .collect(Collectors.toCollection(HashSet::new));

                        summaries.add(new TrainSummary(
                                        first.getTrain().getTrainNumber(), first.getTrain().getTrainName(),
                                        stationCodes, distanceKm, journeyMinutes, Math.max(0, route.size() - 2)));
                }

                return summaries;
        }
}
