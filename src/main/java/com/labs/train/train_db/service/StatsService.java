package com.labs.train.train_db.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.labs.train.train_db.config.CacheConfig;
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

                List<TrainSpeedProjection> trainSpeeds = TrainSpeedCalculator.computeAll(
                                trainScheduleRepository.findAllByOrderByTrain_IdAscSequenceNoAsc(), journeyDayCalculator);

                // Explicit lambdas rather than TrainSpeedProjection::averageSpeedKmh -
                // the method-reference form trips the JDT null analyzer's
                // "unchecked conversion for the receiver" warning on a record
                // accessor used as a ToDoubleFunction; behaviorally identical,
                // just avoids the false-positive warning.
                List<TrainSpeedProjection> fastestTrains = trainSpeeds.stream()
                                .sorted(Comparator.comparingDouble((TrainSpeedProjection p) -> p.averageSpeedKmh()).reversed())
                                .limit(RANKED_LIST_SIZE)
                                .toList();

                List<TrainSpeedProjection> slowestTrains = trainSpeeds.stream()
                                .sorted(Comparator.comparingDouble((TrainSpeedProjection p) -> p.averageSpeedKmh()))
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

        private <T> T firstOrNull(List<T> results) {
                return results.isEmpty() ? null : results.get(0);
        }
}
