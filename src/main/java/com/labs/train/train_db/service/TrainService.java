package com.labs.train.train_db.service;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.labs.train.train_db.common.AppConstants;
import com.labs.train.train_db.common.FuzzyMatch;
import com.labs.train.train_db.config.CacheConfig;
import com.labs.train.train_db.entity.Train;
import com.labs.train.train_db.entity.TrainSchedule;
import com.labs.train.train_db.exception.ResourceNotFoundException;
import com.labs.train.train_db.model.CreateTrainRequest;
import com.labs.train.train_db.model.RouteStopResponse;
import com.labs.train.train_db.model.TrainDetailsResponse;
import com.labs.train.train_db.model.TrainSearchResponse;
import com.labs.train.train_db.repository.TrainRepository;
import com.labs.train.train_db.repository.TrainScheduleRepository;

import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrainService {

        private final TrainRepository trainRepository;
        private final TrainScheduleRepository trainScheduleRepository;
        private final JourneyDayCalculator journeyDayCalculator;

        @Transactional
        public TrainSearchResponse createTrain(CreateTrainRequest request) {

                log.info("Creating train {}", request.trainNumber());

                Train train = new Train();
                train.setTrainNumber(request.trainNumber());
                train.setTrainName(request.trainName());

                Train saved = trainRepository.save(train);

                return new TrainSearchResponse(
                                saved.getTrainNumber(),
                                saved.getTrainName());
        }

        public Page<TrainSearchResponse> getAllTrains(Pageable pageable) {

                log.info("Listing trains, page {} size {}", pageable.getPageNumber(), pageable.getPageSize());

                return trainRepository.findAll(pageable)
                                .map(train -> new TrainSearchResponse(
                                                train.getTrainNumber(),
                                                train.getTrainName()));
        }

        public List<TrainSearchResponse> search(String query) {

                query = query.trim();

                if (query.isBlank()) {
                        return List.of();
                }

                log.info("Searching trains with query: {}", query);

                Pageable pageable = PageRequest.of(0, AppConstants.SEARCH_PAGE_SIZE);

                List<TrainSearchResponse> result = trainRepository.search(query, pageable)
                                .stream()
                                .map(train -> new TrainSearchResponse(
                                                train.getTrainNumber(),
                                                train.getTrainName()))
                                .toList();

                if (result.isEmpty()) {
                        result = fuzzySearch(query);
                        log.info("No exact matches for '{}', found {} fuzzy matches", query, result.size());
                } else {
                        log.info("Found {} trains", result.size());
                }

                return result;
        }

        private List<TrainSearchResponse> fuzzySearch(String query) {

                String queryLower = query.toLowerCase(Locale.ROOT);
                int maxDistance = FuzzyMatch.maxDistanceFor(queryLower.length());

                return fuzzySearchIndex().stream()
                                .map(train -> Map.entry(train, fuzzyScore(train, queryLower)))
                                .filter(entry -> entry.getValue() <= maxDistance)
                                .sorted(Comparator.comparingInt(entry -> entry.getValue()))
                                .limit(AppConstants.SEARCH_PAGE_SIZE)
                                .map(entry -> entry.getKey())
                                .toList();
        }

        private int fuzzyScore(TrainSearchResponse train, String queryLower) {

                int best = FuzzyMatch.distance(train.trainNumber().toLowerCase(Locale.ROOT), queryLower);

                for (String word : train.trainName().toLowerCase(Locale.ROOT).split("\\s+")) {
                        best = Math.min(best, FuzzyMatch.distance(word, queryLower));
                }

                return best;
        }

        @Cacheable(cacheNames = CacheConfig.SEARCH_INDEX_CACHE, key = "'trains'")
        public List<TrainSearchResponse> fuzzySearchIndex() {
                return trainRepository.findAllSearchKeys();
        }

        @Cacheable(cacheNames = CacheConfig.TRAIN_DETAILS_CACHE, key = "#trainNumber")
        public TrainDetailsResponse getTrainDetails(String trainNumber) {

                log.info("Fetching route for train {}", trainNumber);

                List<TrainSchedule> schedules = trainScheduleRepository
                                .findByTrain_TrainNumberOrderBySequenceNo(trainNumber);

                if (schedules.isEmpty()) {
                        log.warn("Train not found: {}", trainNumber);
                        throw new ResourceNotFoundException(
                                        "Train not found: " + trainNumber);
                }

                Train train = schedules.getFirst().getTrain();

                List<RouteStopResponse> route = new java.util.ArrayList<>();

                List<Integer> journeyDays = journeyDayCalculator.computeJourneyDays(schedules);

                for (int i = 0; i < schedules.size(); i++) {

                        TrainSchedule schedule = schedules.get(i);

                        int journeyDay = journeyDays.get(i);

                        Integer distanceFromPrevious = null;

                        if (i == 0) {
                                distanceFromPrevious = 0;
                        } else if (schedule.getDistance() != null &&
                                        schedules.get(i - 1).getDistance() != null) {

                                distanceFromPrevious = schedule.getDistance() - schedules.get(i - 1).getDistance();
                        }

                        int haltMinutes = 0;

                        if (i != 0 &&
                                        i != schedules.size() - 1 &&
                                        schedule.getArrivalTime() != null &&
                                        schedule.getDepartureTime() != null) {

                                haltMinutes = (int) Duration
                                                .between(
                                                                schedule.getArrivalTime(),
                                                                schedule.getDepartureTime())
                                                .toMinutes();
                        }

                        LocalTime arrival = schedule.getArrivalTime();
                        LocalTime departure = schedule.getDepartureTime();

                        if (i == 0) {
                                arrival = null;
                        }

                        if (i == schedules.size() - 1) {
                                departure = null;
                        }

                        route.add(
                                        new RouteStopResponse(
                                                        schedule.getSequenceNo(),
                                                        schedule.getStation().getStationCode(),
                                                        schedule.getStation().getStationName(),
                                                        arrival,
                                                        departure,
                                                        schedule.getDistance(),
                                                        distanceFromPrevious,
                                                        haltMinutes,
                                                        journeyDay,
                                                        i == 0,
                                                        i == schedules.size() - 1));
                }

                int totalStops = route.size();
                Integer journeyDistance = route.getLast().distance();

                if (journeyDistance == null) {
                        journeyDistance = 0;
                }

                long journeyMinutes = calculateJourneyMinutes(schedules, route);

                double averageSpeed = journeyMinutes == 0
                                ? 0
                                : journeyDistance / (journeyMinutes / 60.0);

                String sourceStationName = route.getFirst().stationName();
                String destinationStationName = route.getLast().stationName();

                return new TrainDetailsResponse(
                                train.getTrainNumber(),
                                train.getTrainName(),

                                totalStops,
                                journeyDistance,
                                journeyMinutes,
                                Math.round(averageSpeed * 10.0) / 10.0,

                                route,

                                sourceStationName,
                                destinationStationName);
        }

        private long calculateJourneyMinutes(List<TrainSchedule> schedules,
                        List<RouteStopResponse> route) {

                if (schedules.isEmpty()
                                || schedules.getFirst().getDepartureTime() == null
                                || schedules.getLast().getArrivalTime() == null) {
                        return 0;
                }

                RouteStopResponse first = route.getFirst();
                RouteStopResponse last = route.getLast();

                return journeyDayCalculator.minutesBetween(
                                first.journeyDay(),
                                schedules.getFirst().getDepartureTime(),
                                last.journeyDay(),
                                schedules.getLast().getArrivalTime());
        }
}