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

        record TrainSummary(
                        String trainNumber, String trainName, Set<String> stationCodes,
                        int distanceKm, long journeyMinutes, int halts) {
        }

        @Cacheable(cacheNames = CacheConfig.SEARCH_INDEX_CACHE, key = "'trainSummaries'")
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
