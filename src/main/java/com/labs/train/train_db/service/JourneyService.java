package com.labs.train.train_db.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.labs.train.train_db.entity.TrainSchedule;
import com.labs.train.train_db.model.JourneySearchResponse;
import com.labs.train.train_db.model.JourneyTrainResponse;
import com.labs.train.train_db.repository.TrainScheduleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JourneyService {

        private final TrainScheduleRepository trainScheduleRepository;

        public JourneySearchResponse search(String from, String to) {

                log.info("Searching journeys from {} to {}", from, to);
                from = from.trim().toUpperCase();
                to = to.trim().toUpperCase();

                List<TrainSchedule> sourceSchedules = trainScheduleRepository.findByStation_StationCode(from);

                List<TrainSchedule> destinationSchedules = trainScheduleRepository.findByStation_StationCode(to);

                log.info("Source schedules: {}", sourceSchedules.size());
                log.info("Destination schedules: {}", destinationSchedules.size());

                Map<Long, TrainSchedule> destinationMap = destinationSchedules.stream()
                                .collect(Collectors.toMap(
                                                schedule -> schedule.getTrain().getId(),
                                                Function.identity()));

                List<JourneyTrainResponse> journeys = new ArrayList<>();

                for (TrainSchedule source : sourceSchedules) {

                        TrainSchedule destination = destinationMap.get(source.getTrain().getId());

                        if (destination == null) {
                                continue;
                        }

                        if (source.getSequenceNo() >= destination.getSequenceNo()) {
                                continue;
                        }

                        int distance = destination.getDistance() - source.getDistance();

                        List<TrainSchedule> route = trainScheduleRepository.findByTrain_TrainNumberOrderBySequenceNo(
                                        source.getTrain().getTrainNumber());

                        journeys.add(
                                        new JourneyTrainResponse(
                                                        source.getTrain().getTrainNumber(),
                                                        source.getTrain().getTrainName(),
                                                        source.getDepartureTime(),
                                                        destination.getArrivalTime(),
                                                        calculateDuration(route, source, destination),
                                                        distance));
                }

                return new JourneySearchResponse(
                                from,
                                to,
                                journeys.size(),
                                journeys);
        }

        private String calculateDuration(
                        List<TrainSchedule> route,
                        TrainSchedule source,
                        TrainSchedule destination) {

                int journeyDay = 1;
                LocalTime previousDeparture = route.getFirst().getDepartureTime();

                int sourceDay = 1;
                int destinationDay = 1;

                for (int i = 0; i < route.size(); i++) {

                        TrainSchedule schedule = route.get(i);

                        if (i > 0
                                        && schedule.getArrivalTime() != null
                                        && schedule.getArrivalTime().isBefore(previousDeparture)) {

                                journeyDay++;
                        }

                        if (schedule.getSequenceNo().equals(source.getSequenceNo())) {
                                sourceDay = journeyDay;
                        }

                        if (schedule.getSequenceNo().equals(destination.getSequenceNo())) {
                                destinationDay = journeyDay;
                        }

                        if (schedule.getDepartureTime() != null) {
                                previousDeparture = schedule.getDepartureTime();
                        }
                }

                if (source.getDepartureTime() == null || destination.getArrivalTime() == null) {
                        return "";
                }

                LocalDate baseDate = LocalDate.of(2000, 1, 1);

                LocalDateTime departure = LocalDateTime.of(
                                baseDate.plusDays(sourceDay - 1),
                                source.getDepartureTime());

                LocalDateTime arrival = LocalDateTime.of(
                                baseDate.plusDays(destinationDay - 1),
                                destination.getArrivalTime());

                long minutes = Duration.between(departure, arrival).toMinutes();

                long hours = minutes / 60;
                long remainingMinutes = minutes % 60;

                return String.format("%dh %02dm", hours, remainingMinutes);
        }

}