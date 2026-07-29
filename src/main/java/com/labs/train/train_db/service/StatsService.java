package com.labs.train.train_db.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.labs.train.train_db.config.CacheConfig;
import com.labs.train.train_db.entity.TrainSchedule;
import com.labs.train.train_db.model.RouteDistanceProjection;
import com.labs.train.train_db.model.StationTrafficProjection;
import com.labs.train.train_db.model.StatsResponse;
import com.labs.train.train_db.model.TrainSpeedProjection;
import com.labs.train.train_db.repository.StationRepository;
import com.labs.train.train_db.repository.TrainRepository;
import com.labs.train.train_db.repository.TrainScheduleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {

        // Ranked lists are capped rather than returning everything - this is a
        // public, unauthenticated, rate-limited endpoint, and nobody scrolling
        // a "fastest trains" list needs all 8,000+ of them.
        private static final int RANKED_LIST_SIZE = 10;

        private final TrainRepository trainRepository;
        private final StationRepository stationRepository;
        private final TrainScheduleRepository trainScheduleRepository;
        private final JourneyDayCalculator journeyDayCalculator;

        @Cacheable(cacheNames = CacheConfig.STATS_CACHE)
        public StatsResponse getStats() {

                RouteDistanceProjection longestRoute = firstOrNull(
                                trainScheduleRepository.findRouteDistancesDescending(PageRequest.of(0, 1)));

                RouteDistanceProjection shortestRoute = firstOrNull(
                                trainScheduleRepository.findRouteDistancesAscending(PageRequest.of(0, 1)));

                List<StationTrafficProjection> busiestStations = trainScheduleRepository
                                .findBusiestStations(PageRequest.of(0, RANKED_LIST_SIZE));

                StationTrafficProjection busiestStation = firstOrNull(busiestStations);

                List<TrainSpeedProjection> trainSpeeds = computeTrainSpeeds();

                List<TrainSpeedProjection> fastestTrains = trainSpeeds.stream()
                                .sorted(Comparator.comparingDouble(TrainSpeedProjection::averageSpeedKmh).reversed())
                                .limit(RANKED_LIST_SIZE)
                                .toList();

                List<TrainSpeedProjection> slowestTrains = trainSpeeds.stream()
                                .sorted(Comparator.comparingDouble(TrainSpeedProjection::averageSpeedKmh))
                                .limit(RANKED_LIST_SIZE)
                                .toList();

                return new StatsResponse(
                                trainRepository.count(),
                                stationRepository.count(),
                                longestRoute,
                                shortestRoute,
                                busiestStation,
                                busiestStations,
                                fastestTrains,
                                slowestTrains);
        }

        /**
         * Average speed per train, computed the same way
         * TrainService#getTrainDetails computes it for a single train
         * (distance / hours, correctly handling a journey that crosses
         * midnight via JourneyDayCalculator) - just applied to every train in
         * one pass instead of one JPA call per train. Trains with fewer than
         * two stops, a missing first-departure/last-arrival time, or a
         * missing distance anywhere on their route are skipped rather than
         * guessed at - see the per-field null checks below, mirroring how
         * TrainService treats the same missing-data cases.
         */
        private List<TrainSpeedProjection> computeTrainSpeeds() {

                Map<Long, List<TrainSchedule>> schedulesByTrainId = trainScheduleRepository
                                .findAllByOrderByTrain_IdAscSequenceNoAsc()
                                .stream()
                                .collect(Collectors.groupingBy(schedule -> schedule.getTrain().getId()));

                List<TrainSpeedProjection> speeds = new ArrayList<>();

                for (List<TrainSchedule> schedules : schedulesByTrainId.values()) {

                        if (schedules.size() < 2) {
                                continue;
                        }

                        TrainSchedule first = schedules.get(0);
                        TrainSchedule last = schedules.get(schedules.size() - 1);

                        if (first.getDepartureTime() == null
                                        || last.getArrivalTime() == null
                                        || first.getDistance() == null
                                        || last.getDistance() == null) {
                                continue;
                        }

                        int distanceKm = last.getDistance() - first.getDistance();

                        if (distanceKm <= 0) {
                                continue;
                        }

                        List<Integer> journeyDays = journeyDayCalculator.computeJourneyDays(schedules);

                        long durationMinutes = journeyDayCalculator.minutesBetween(
                                        journeyDays.get(0), first.getDepartureTime(),
                                        journeyDays.get(journeyDays.size() - 1), last.getArrivalTime());

                        if (durationMinutes <= 0) {
                                continue;
                        }

                        double averageSpeedKmh = distanceKm / (durationMinutes / 60.0);

                        speeds.add(new TrainSpeedProjection(
                                        first.getTrain().getTrainNumber(),
                                        first.getTrain().getTrainName(),
                                        Math.round(averageSpeedKmh * 10.0) / 10.0,
                                        distanceKm,
                                        durationMinutes));
                }

                return speeds;
        }

        private <T> T firstOrNull(List<T> results) {
                return results.isEmpty() ? null : results.get(0);
        }
}
