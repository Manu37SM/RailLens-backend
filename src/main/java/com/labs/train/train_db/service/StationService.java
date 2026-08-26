package com.labs.train.train_db.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.labs.train.train_db.common.AppConstants;
import com.labs.train.train_db.common.FuzzyMatch;
import com.labs.train.train_db.config.CacheConfig;
import com.labs.train.train_db.entity.Station;
import com.labs.train.train_db.entity.TrainSchedule;
import com.labs.train.train_db.exception.ResourceNotFoundException;
import com.labs.train.train_db.model.CreateStationRequest;
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

        @Transactional
        public StationSearchResponse createStation(CreateStationRequest request) {

                log.info("Creating station {}", request.stationCode());

                Station station = new Station();
                station.setStationCode(request.stationCode());
                station.setStationName(request.stationName());

                Station saved = stationRepository.save(station);

                return new StationSearchResponse(
                                saved.getStationCode(),
                                saved.getStationName());
        }

        public Page<StationSearchResponse> getAllStations(Pageable pageable) {

                log.info("Listing stations, page {} size {}", pageable.getPageNumber(), pageable.getPageSize());

                return stationRepository.findAll(pageable)
                                .map(station -> new StationSearchResponse(
                                                station.getStationCode(),
                                                station.getStationName()));
        }

        public List<StationSearchResponse> searchStations(String query) {

                query = query.trim();

                if (query.isBlank()) {
                        return List.of();
                }

                log.info("Searching stations with query '{}'", query);

                Pageable pageable = PageRequest.of(0, AppConstants.SEARCH_PAGE_SIZE);

                List<StationSearchResponse> result = stationRepository.search(query, pageable)
                                .stream()
                                .map(station -> new StationSearchResponse(
                                                station.getStationCode(),
                                                station.getStationName()))
                                .toList();

                if (result.isEmpty()) {
                        result = fuzzySearch(query);
                        log.info("No exact matches for '{}', found {} fuzzy matches", query, result.size());
                }

                return result;
        }

        private List<StationSearchResponse> fuzzySearch(String query) {

                String queryLower = query.toLowerCase(Locale.ROOT);
                int maxDistance = FuzzyMatch.maxDistanceFor(queryLower.length());

                return fuzzySearchIndex().stream()
                                .map(station -> Map.entry(station, fuzzyScore(station, queryLower)))
                                .filter(entry -> entry.getValue() <= maxDistance)
                                .sorted(Comparator.comparingInt(entry -> entry.getValue()))
                                .limit(AppConstants.SEARCH_PAGE_SIZE)
                                .map(entry -> entry.getKey())
                                .toList();
        }

        private int fuzzyScore(StationSearchResponse station, String queryLower) {

                int best = FuzzyMatch.distance(station.stationCode().toLowerCase(Locale.ROOT), queryLower);

                for (String word : station.stationName().toLowerCase(Locale.ROOT).split("\\s+")) {
                        best = Math.min(best, FuzzyMatch.distance(word, queryLower));
                }

                return best;
        }

        @Cacheable(cacheNames = CacheConfig.SEARCH_INDEX_CACHE, key = "'stations'")
        public List<StationSearchResponse> fuzzySearchIndex() {
                return stationRepository.findAllSearchKeys();
        }

        @Cacheable(cacheNames = CacheConfig.STATION_DETAILS_CACHE, key = "#stationCode")
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
