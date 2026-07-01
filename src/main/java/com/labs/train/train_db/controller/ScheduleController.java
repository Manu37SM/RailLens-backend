package com.labs.train.train_db.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.labs.train.train_db.entity.Station;
import com.labs.train.train_db.entity.Train;
import com.labs.train.train_db.entity.TrainSchedule;
import com.labs.train.train_db.model.ScheduleRequest;
import com.labs.train.train_db.repository.StationRepository;
import com.labs.train.train_db.repository.TrainRepository;
import com.labs.train.train_db.repository.TrainScheduleRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class ScheduleController {

        private final TrainRepository trainRepository;
        private final StationRepository stationRepository;
        private final TrainScheduleRepository scheduleRepository;

        @PostMapping
        public TrainSchedule createSchedule(
                        @RequestBody ScheduleRequest request) {

                Train train = trainRepository
                                .findByTrainNumber(request.getTrainNumber())
                                .orElseThrow();

                Station station = stationRepository
                                .findByStationCode(request.getStationCode())
                                .orElseThrow();

                TrainSchedule schedule = new TrainSchedule();

                schedule.setTrain(train);
                schedule.setStation(station);
                schedule.setSequenceNo(request.getSequenceNo());
                schedule.setArrivalTime(request.getArrivalTime());
                schedule.setDepartureTime(request.getDepartureTime());

                return scheduleRepository.save(schedule);
        }
}