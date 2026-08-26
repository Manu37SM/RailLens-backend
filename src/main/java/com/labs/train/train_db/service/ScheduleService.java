package com.labs.train.train_db.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.labs.train.train_db.config.CacheConfig;
import com.labs.train.train_db.entity.Station;
import com.labs.train.train_db.entity.Train;
import com.labs.train.train_db.entity.TrainSchedule;
import com.labs.train.train_db.exception.ResourceNotFoundException;
import com.labs.train.train_db.model.ScheduleRequest;
import com.labs.train.train_db.model.ScheduleResponse;
import com.labs.train.train_db.repository.StationRepository;
import com.labs.train.train_db.repository.TrainRepository;
import com.labs.train.train_db.repository.TrainScheduleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ScheduleService {

        private final TrainRepository trainRepository;
        private final StationRepository stationRepository;
        private final TrainScheduleRepository trainScheduleRepository;

        @Caching(evict = {
                        @CacheEvict(cacheNames = CacheConfig.TRAIN_DETAILS_CACHE, key = "#request.trainNumber"),
                        @CacheEvict(cacheNames = CacheConfig.STATION_DETAILS_CACHE, key = "#request.stationCode")
        })
        public ScheduleResponse createSchedule(ScheduleRequest request) {

                log.info(
                                "Creating schedule entry for train {} at station {}",
                                request.trainNumber(),
                                request.stationCode());

                Train train = trainRepository
                                .findByTrainNumber(request.trainNumber())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Train not found: " + request.trainNumber()));

                Station station = stationRepository
                                .findByStationCode(request.stationCode())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Station not found: " + request.stationCode()));

                TrainSchedule schedule = new TrainSchedule();

                schedule.setTrain(train);
                schedule.setStation(station);
                schedule.setSequenceNo(request.sequenceNo());
                schedule.setArrivalTime(request.arrivalTime());
                schedule.setDepartureTime(request.departureTime());

                TrainSchedule saved = trainScheduleRepository.save(schedule);

                return new ScheduleResponse(
                                train.getTrainNumber(),
                                station.getStationCode(),
                                saved.getSequenceNo(),
                                saved.getArrivalTime(),
                                saved.getDepartureTime(),
                                saved.getDistance());
        }
}
