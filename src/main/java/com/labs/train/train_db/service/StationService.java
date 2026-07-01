package com.labs.train.train_db.service;

import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.labs.train.train_db.common.AppConstants;
import com.labs.train.train_db.entity.Station;
import com.labs.train.train_db.entity.TrainSchedule;
import com.labs.train.train_db.exception.ResourceNotFoundException;
import com.labs.train.train_db.model.StationResponse;
import com.labs.train.train_db.model.StationSearchResponse;
import com.labs.train.train_db.model.StationTrainResponse;
import com.labs.train.train_db.repository.StationRepository;
import com.labs.train.train_db.repository.TrainScheduleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StationService {

        private final StationRepository stationRepository;
        private final TrainScheduleRepository trainScheduleRepository;

        public List<StationSearchResponse> searchStations(String query) {

                query = query.trim();

                if (query.isBlank()) {
                        return List.of();
                }

                log.info("Searching stations with query '{}'", query);

                Pageable pageable = PageRequest.of(0, AppConstants.SEARCH_PAGE_SIZE);

                return stationRepository.search(query, pageable)
                                .stream()
                                .map(station -> new StationSearchResponse(
                                                station.getStationCode(),
                                                station.getStationName()))
                                .toList();
        }

        public StationResponse getStation(String stationCode) {

                log.info("Fetching station details for {}", stationCode);

                Station station = stationRepository.findByStationCode(stationCode)
                                .orElseThrow(() -> new ResourceNotFoundException("Station not found: " + stationCode));

                List<TrainSchedule> schedules = trainScheduleRepository
                                .findByStation_StationCodeOrderByArrivalTime(stationCode);
                // TODO: Improve station timetable ordering when journey day/service date is
                // available.

                List<StationTrainResponse> trains = schedules.stream()
                                .map(schedule -> {

                                        TrainSchedule firstStop = trainScheduleRepository
                                                        .findFirstByTrainOrderBySequenceNoAsc(schedule.getTrain())
                                                        .orElseThrow();

                                        TrainSchedule lastStop = trainScheduleRepository
                                                        .findFirstByTrainOrderBySequenceNoDesc(schedule.getTrain())
                                                        .orElseThrow();

                                        boolean isOrigin = schedule.getSequenceNo().equals(firstStop.getSequenceNo());

                                        boolean isDestination = schedule.getSequenceNo()
                                                        .equals(lastStop.getSequenceNo());

                                        return new StationTrainResponse(
                                                        schedule.getTrain().getTrainNumber(),
                                                        schedule.getTrain().getTrainName(),
                                                        schedule.getArrivalTime(),
                                                        schedule.getDepartureTime(),
                                                        schedule.getDistance(),
                                                        schedule.getSequenceNo(),
                                                        isOrigin,
                                                        isDestination);
                                })
                                .toList();

                return new StationResponse(
                                station.getStationCode(),
                                station.getStationName(),
                                trains.size(),
                                trains);
        }
}
