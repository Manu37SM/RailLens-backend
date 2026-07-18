package com.labs.train.train_db.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

                List<StationTrainResponse> trains = buildStationTrains(schedules);

                return new StationResponse(
                                station.getStationCode(),
                                station.getStationName(),
                                trains.size(),
                                trains);
        }

        /**
         * Builds the list of trains serving a station without firing per-train
         * queries (the earlier version did two queries per train to establish
         * origin/destination). All schedules for the affected trains are loaded
         * in a single query and grouped by train id, so a station with N trains
         * costs O(1) round trips instead of O(2N).
         */
        private List<StationTrainResponse> buildStationTrains(List<TrainSchedule> stationSchedules) {

                if (stationSchedules.isEmpty()) {
                        return List.of();
                }

                List<Long> trainIds = stationSchedules.stream()
                                .map(schedule -> schedule.getTrain().getId())
                                .distinct()
                                .toList();

                Map<Long, List<TrainSchedule>> schedulesByTrain = trainScheduleRepository
                                .findByTrain_IdInOrderByTrain_IdAscSequenceNoAsc(trainIds)
                                .stream()
                                .collect(Collectors.groupingBy(
                                                schedule -> schedule.getTrain().getId()));

                return stationSchedules.stream()
                                .map(schedule -> {

                                        List<TrainSchedule> stops = schedulesByTrain
                                                        .get(schedule.getTrain().getId());

                                        int firstSeq = stops.get(0).getSequenceNo();
                                        int lastSeq = stops.get(stops.size() - 1).getSequenceNo();

                                        boolean isOrigin = schedule.getSequenceNo().equals(firstSeq);
                                        boolean isDestination = schedule.getSequenceNo().equals(lastSeq);

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
        }
}
