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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class TrainSummaryIndex {

        private final ScheduleSnapshotService scheduleSnapshotService;
        private final JourneyDayCalculator journeyDayCalculator;

        /**
         * @param stationCodes every station on the route, for fast "stops at" checks
         * @param stopSequence the same stations in route order, for direction ("from X to Y")
         */
        record TrainSummary(
                        String trainNumber, String trainName, Set<String> stationCodes, List<String> stopSequence,
                        int distanceKm, long journeyMinutes, int halts) {

                /** True if the train calls at {@code from} and later at {@code to}, i.e. runs in that direction. */
                boolean runsFromTo(String from, String to) {
                        return runsFromAnyToAny(Set.of(from), Set.of(to));
                }

                /** True if the train calls at one of {@code from} and later at one of {@code to} (city to city). */
                boolean runsFromAnyToAny(Set<String> from, Set<String> to) {
                        boolean boarded = false;
                        for (String code : stopSequence) {
                                if (boarded && to.contains(code)) {
                                        return true;
                                }
                                boarded |= from.contains(code);
                        }
                        return false;
                }
        }

        @Cacheable(cacheNames = CacheConfig.SEARCH_INDEX_CACHE, key = "'trainSummaries'", sync = true)
        List<TrainSummary> buildIndex() {

                Map<Long, List<TrainSchedule>> schedulesByTrainId = scheduleSnapshotService
                                .getAllOrderedByTrainThenSequence()
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

                        List<String> stopSequence = route.stream()
                                        .map(schedule -> schedule.getStation().getStationCode())
                                        .toList();
                        Set<String> stationCodes = new HashSet<>(stopSequence);

                        summaries.add(new TrainSummary(
                                        first.getTrain().getTrainNumber(), first.getTrain().getTrainName(),
                                        stationCodes, stopSequence, distanceKm, journeyMinutes, Math.max(0, route.size() - 2)));
                }

                return summaries;
        }
}
