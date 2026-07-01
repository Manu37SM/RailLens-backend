package com.labs.train.train_db.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.labs.train.train_db.common.AppConstants;
import com.labs.train.train_db.entity.Train;
import com.labs.train.train_db.entity.TrainSchedule;
import com.labs.train.train_db.exception.ResourceNotFoundException;
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

                log.info("Found {} trains", result.size());

                return result;
        }

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

                int journeyDay = 1;

                LocalTime previousDeparture = schedules.getFirst().getDepartureTime();

                for (int i = 0; i < schedules.size(); i++) {

                        TrainSchedule schedule = schedules.get(i);

                        if (i > 0 &&
                                        schedule.getArrivalTime() != null &&
                                        schedule.getArrivalTime().isBefore(previousDeparture)) {

                                journeyDay++;
                        }

                        int distanceFromPrevious = (i == 0)
                                        ? 0
                                        : schedule.getDistance() - schedules.get(i - 1).getDistance();

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

                        if (schedule.getDepartureTime() != null) {
                                previousDeparture = schedule.getDepartureTime();
                        }
                }

                int totalStops = route.size();
                int journeyDistance = route.getLast().distance();

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

                LocalDate baseDate = LocalDate.of(2000, 1, 1);

                LocalDateTime departure = LocalDateTime.of(
                                baseDate.plusDays(first.journeyDay() - 1),
                                schedules.getFirst().getDepartureTime());

                LocalDateTime arrival = LocalDateTime.of(
                                baseDate.plusDays(last.journeyDay() - 1),
                                schedules.getLast().getArrivalTime());

                return Duration.between(departure, arrival).toMinutes();
        }
}