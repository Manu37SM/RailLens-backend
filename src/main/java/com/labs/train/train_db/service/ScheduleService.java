package com.labs.train.train_db.service;

import org.springframework.cache.annotation.CacheEvict;
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

        // A new stop changes routes, stats and the network, not just this train and station: clear everything.
        @CacheEvict(allEntries = true, cacheNames = {
                        CacheConfig.TRAIN_DETAILS_CACHE, CacheConfig.STATION_DETAILS_CACHE, CacheConfig.STATS_CACHE,
                        CacheConfig.SEARCH_INDEX_CACHE, CacheConfig.NETWORK_CACHE, CacheConfig.RANKINGS_CACHE,
                        CacheConfig.FUN_STATS_CACHE, CacheConfig.ACHIEVEMENTS_CACHE, CacheConfig.SCHEDULE_SNAPSHOT_CACHE,
                        CacheConfig.STATION_INTELLIGENCE_CACHE, CacheConfig.TRAIN_INTELLIGENCE_CACHE })
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
