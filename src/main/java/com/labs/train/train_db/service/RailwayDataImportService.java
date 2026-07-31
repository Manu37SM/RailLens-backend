package com.labs.train.train_db.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.labs.train.train_db.common.AppConstants;
import com.labs.train.train_db.repository.StationRepository;
import com.labs.train.train_db.repository.TrainRepository;
import com.labs.train.train_db.repository.TrainScheduleRepository;

import com.labs.train.train_db.config.CacheConfig;
import com.labs.train.train_db.model.ImportResult;
import com.labs.train.train_db.entity.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RailwayDataImportService {

    private final StationRepository stationRepository;
    private final TrainRepository trainRepository;
    private final TrainScheduleRepository trainScheduleRepository;
    private final CacheManager cacheManager;
    private final EntityManager entityManager;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("H:mm");

    /**
     * Imports {@code data/train_dataset.csv}. This used to return {@code
     * void} and swallow any top-level failure with {@code
     * e.printStackTrace()} - the caller (see {@code
     * RailwayDataImportController}) had no way to tell a successful import
     * from a silently failed one; it always returned the same hardcoded
     * "Import Started" string. This now returns a result the controller can
     * actually report.
     *
     * Batched: every {@code AppConstants.IMPORT_BATCH_SIZE} rows, the
     * persistence context is flushed and cleared (see the loop below).
     * Without this, Hibernate keeps every one of up to ~300k inserted
     * TrainSchedule entities as "managed" (dirty-checking, identity map)
     * for the entire transaction - on a 512MB deployment target that's a
     * real OOM risk on the one pathway that writes bulk production data,
     * not just a performance nit. {@code stationCache}/{@code trainCache}
     * intentionally keep holding their Station/Train references across a
     * clear() - those become detached, but that's safe here: TrainSchedule
     * has no cascade on its {@code @ManyToOne} associations, so Hibernate
     * only ever needs the detached entity's already-loaded {@code id} to
     * populate the foreign key column, and any later {@code save()} on a
     * detached Station/Train (the "keep names up-to-date" branches below)
     * goes through Spring Data's merge-on-save path, which re-attaches and
     * returns a fresh managed instance - already how this code re-caches
     * the result of every such save.
     */
    public ImportResult importCsv() {

        Map<String, Station> stationCache = new HashMap<>();
        Map<String, Train> trainCache = new HashMap<>();
        Set<String> processedTrains = new HashSet<>();

        int count = 0;
        int failedCount = 0;

        try {

            log.info("Starting railway data import...");

            stationRepository.findAll()
                    .forEach(station -> stationCache.put(
                            station.getStationCode(),
                            station));

            trainRepository.findAll()
                    .forEach(train -> trainCache.put(
                            train.getTrainNumber(),
                            train));

            ClassPathResource resource = new ClassPathResource("data/train_dataset.csv");

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream()));

            CSVParser parser = CSVFormat.DEFAULT
                    .builder()
                    .setHeader(
                            "Train No",
                            "Train Name",
                            "SEQ",
                            "Station Code",
                            "Station Name",
                            "Arrival Time",
                            "Departure Time",
                            "Distance",
                            "Source Station",
                            "Source Station Name",
                            "Destination Station",
                            "Destination Station Name")
                    .setSkipHeaderRecord(true)
                    .get()
                    .parse(reader);

            for (CSVRecord record : parser) {

                try {

                    String trainNo = cleanText(record.get("Train No"));
                    String trainName = cleanText(record.get("Train Name"));

                    Integer sequenceNo = Integer.parseInt(cleanText(record.get("SEQ")));

                    String stationCode = cleanText(record.get("Station Code"));
                    String stationName = cleanText(record.get("Station Name"));

                    LocalTime arrivalTime = parseTime(cleanText(record.get("Arrival Time")));
                    LocalTime departureTime = parseTime(cleanText(record.get("Departure Time")));

                    Integer distance = parseInteger(cleanText(record.get("Distance")));

                    // Station
                    Station station = stationCache.get(stationCode);

                    if (station == null) {

                        station = new Station();
                        station.setStationCode(stationCode);
                        station.setStationName(stationName);

                        station = stationRepository.save(station);

                        stationCache.put(stationCode, station);

                    } else {

                        // Keep station names up-to-date
                        if (!stationName.equals(station.getStationName())) {
                            station.setStationName(stationName);
                            station = stationRepository.save(station);
                            stationCache.put(stationCode, station);
                        }
                    }

                    // Train
                    Train train = trainCache.get(trainNo);

                    if (train == null) {

                        train = new Train();
                        train.setTrainNumber(trainNo);
                        train.setTrainName(trainName);

                        train = trainRepository.save(train);

                        trainCache.put(trainNo, train);

                    } else {

                        if (!trainName.equals(train.getTrainName())) {
                            train.setTrainName(trainName);
                            train = trainRepository.save(train);
                            trainCache.put(trainNo, train);
                        }
                    }

                    // Delete schedule only for existing trains, only once
                    if (processedTrains.add(trainNo)) {
                        trainScheduleRepository.deleteByTrain(train);
                    }

                    // Schedule
                    TrainSchedule schedule = new TrainSchedule();

                    schedule.setTrain(train);
                    schedule.setStation(station);

                    schedule.setSequenceNo(sequenceNo);
                    schedule.setArrivalTime(arrivalTime);
                    schedule.setDepartureTime(departureTime);
                    schedule.setDistance(distance);

                    trainScheduleRepository.save(schedule);

                    count++;

                    if (count % 10000 == 0) {
                        log.info("Imported {} rows", count);
                    }

                    // Release the persistence context's accumulated
                    // TrainSchedule entities periodically instead of
                    // letting all ~300k of them sit as "managed" for the
                    // whole transaction - see this method's javadoc for
                    // why detaching stationCache/trainCache's entities
                    // here is safe.
                    if (count % AppConstants.IMPORT_BATCH_SIZE == 0) {
                        entityManager.flush();
                        entityManager.clear();
                    }
                } catch (Exception ex) {
                    failedCount++;
                    log.error("Failed to import row: {}", record.toString(), ex);
                }

            }

            reader.close();

            log.info("Import finished: {} rows imported, {} rows failed", count, failedCount);

            evictCachesIfAnyRowsChanged(count);

            return new ImportResult(
                            true,
                            count,
                            failedCount,
                            "Import completed: %d row(s) imported, %d row(s) failed"
                                            .formatted(count, failedCount));

        } catch (Exception e) {

            log.error("Railway data import failed", e);

            // Even a failed run may have written some rows before hitting the
            // error (per-row failures are caught and skipped above; this
            // catch is for something failing outside that loop). Evict
            // rather than risk serving stale cached routes for whatever did
            // get written.
            evictCachesIfAnyRowsChanged(count);

            return new ImportResult(
                            false,
                            count,
                            failedCount,
                            "Import failed: " + e.getMessage());
        }
    }

    /**
     * A bulk import can touch an arbitrary, potentially large number of
     * distinct trains and stations in one run - tracking exactly which
     * ones changed just to evict them individually isn't worth the extra
     * bookkeeping for an admin-triggered, infrequent operation. Clearing
     * every cache outright is simpler and correctness-first; the next
     * lookup for any train/station/aggregate just repopulates the cache.
     *
     * Evicts all 8 named caches (see CacheConfig) - this list previously
     * only covered the original 4 and predated the 4 Railway Intelligence
     * caches (NETWORK_CACHE/RANKINGS_CACHE/FUN_STATS_CACHE/
     * ACHIEVEMENTS_CACHE), which meant a CSV import could leave those
     * endpoints serving stale data for up to the 15-minute TTL. Fixed per
     * the backend architecture review's "bulk-import cache eviction is
     * incomplete" finding - AdminService.clearAllCaches() (the manual
     * cache-clear endpoint) already evicted all 8, so this brings the
     * import path in line with it.
     */
    private void evictCachesIfAnyRowsChanged(int rowsImported) {

        if (rowsImported == 0) {
            return;
        }

        clearCache(CacheConfig.TRAIN_DETAILS_CACHE);
        clearCache(CacheConfig.STATION_DETAILS_CACHE);
        clearCache(CacheConfig.STATS_CACHE);
        clearCache(CacheConfig.SEARCH_INDEX_CACHE);
        clearCache(CacheConfig.NETWORK_CACHE);
        clearCache(CacheConfig.RANKINGS_CACHE);
        clearCache(CacheConfig.FUN_STATS_CACHE);
        clearCache(CacheConfig.ACHIEVEMENTS_CACHE);
        clearCache(CacheConfig.SCHEDULE_SNAPSHOT_CACHE);
    }

    private void clearCache(String cacheName) {

        Cache cache = cacheManager.getCache(cacheName);

        if (cache != null) {
            cache.clear();
        }
    }

    private LocalTime parseTime(String value) {

        if (value == null || value.isBlank() || value.equalsIgnoreCase("NA")) {
            return null;
        }

        try {
            return LocalTime.parse(value, TIME_FORMATTER);
        } catch (Exception e) {
            log.warn("Invalid time: {}", value);
            return null;
        }
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("NA")) {
            return null;
        }
        return Integer.parseInt(value);
    }

    private String cleanText(String value) {

        if (value == null) {
            return null;
        }

        value = value.trim();

        if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }

        while (value.endsWith(",")) {
            value = value.substring(0, value.length() - 1).trim();
        }

        return value;
    }
}