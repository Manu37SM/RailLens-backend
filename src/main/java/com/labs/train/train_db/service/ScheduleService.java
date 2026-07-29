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

        /**
         * Adding a stop changes the route TrainService#getTrainDetails and
         * StationService#getStation would have cached for this train/station,
         * so both entries are evicted immediately rather than waiting out
         * CacheConfig's TTL - a stale route is exactly the kind of bug that's
         * hard to notice and confusing to debug.
         */
        @Caching(evict = {
                        @CacheEvict(cacheNames = CacheConfig.TRAIN_DETAILS_CACHE, key = "#request.trainNumber"),
                        @CacheEvict(cacheNames = CacheConfig.STATION_DETAILS_CACHE, key = "#request.stationCode")
        })
        public ScheduleResponse createSchedule(ScheduleRequest request) {

                log.info(
                                "Creating schedule entry for train {} at station {}",
                                request.getTrainNumber(),
                                request.getStationCode());

                Train train = trainRepository
                                .findByTrainNumber(request.getTrainNumber())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Train not found: " + request.getTrainNumber()));

                Station station = stationRepository
                                .findByStationCode(request.getStationCode())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Station not found: " + request.getStationCode()));

                TrainSchedule schedule = new TrainSchedule();

                schedule.setTrain(train);
                schedule.setStation(station);
                schedule.setSequenceNo(request.getSequenceNo());
                schedule.setArrivalTime(request.getArrivalTime());
                schedule.setDepartureTime(request.getDepartureTime());

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
