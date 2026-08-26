package com.labs.train.train_db.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.labs.train.train_db.common.AppConstants;
import com.labs.train.train_db.repository.StationRepository;
import com.labs.train.train_db.repository.TrainRepository;

import com.labs.train.train_db.config.CacheConfig;
import com.labs.train.train_db.model.ImportResult;
import com.labs.train.train_db.entity.*;
import com.labs.train.train_db.service.RailwayImportBatchService.BatchImportResult;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

@Slf4j
@Service
@RequiredArgsConstructor
public class RailwayDataImportService {

    private final StationRepository stationRepository;
    private final TrainRepository trainRepository;
    private final CacheManager cacheManager;
    private final RailwayImportBatchService batchService;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("H:mm");

    public record ParsedRow(
                    String trainNo,
                    String trainName,
                    Integer sequenceNo,
                    String stationCode,
                    String stationName,
                    LocalTime arrivalTime,
                    LocalTime departureTime,
                    Integer distance,
                    String rawRecord) {
    }

    public ImportResult importCsv() {

        Map<String, Station> stationCache = new HashMap<>();
        Map<String, Train> trainCache = new HashMap<>();
        Set<String> processedTrains = new HashSet<>();

        int count = 0;
        int failedCount = 0;

        List<ParsedRow> batch = new ArrayList<>(AppConstants.IMPORT_BATCH_SIZE);
        int batchesProcessed = 0;

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

                    batch.add(parseRow(record));

                } catch (Exception ex) {
                    failedCount++;
                    log.warn("Failed to parse row {}: {}", record.toString(), ex.toString());
                    continue;
                }

                if (batch.size() >= AppConstants.IMPORT_BATCH_SIZE) {
                    BatchImportResult result = batchService.importBatch(
                                    batch, stationCache, trainCache, processedTrains);
                    count += result.succeeded();
                    failedCount += result.failed();
                    batch.clear();
                    batchesProcessed++;

                    if (batchesProcessed % 10 == 0) {
                        log.info("Imported {} rows", count);
                    }
                }
            }

            reader.close();

            if (!batch.isEmpty()) {
                BatchImportResult result = batchService.importBatch(
                                batch, stationCache, trainCache, processedTrains);
                count += result.succeeded();
                failedCount += result.failed();
            }

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

            evictCachesIfAnyRowsChanged(count);

            return new ImportResult(
                            false,
                            count,
                            failedCount,
                            "Import failed: " + e.getMessage());
        }
    }

    private ParsedRow parseRow(CSVRecord record) {

        String trainNo = cleanText(record.get("Train No"));
        String trainName = cleanText(record.get("Train Name"));

        Integer sequenceNo = parseInteger(cleanText(record.get("SEQ")));
        if (sequenceNo == null) {
            throw new IllegalArgumentException("Missing or invalid SEQ value in row: " + record);
        }

        String stationCode = cleanText(record.get("Station Code"));
        String stationName = cleanText(record.get("Station Name"));

        LocalTime arrivalTime = parseTime(cleanText(record.get("Arrival Time")));
        LocalTime departureTime = parseTime(cleanText(record.get("Departure Time")));

        Integer distance = parseInteger(cleanText(record.get("Distance")));

        return new ParsedRow(
                        trainNo, trainName, sequenceNo, stationCode, stationName,
                        arrivalTime, departureTime, distance, record.toString());
    }

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

        String trimmed = value.replaceFirst("^(\\d{1,2}:\\d{2}):\\d{2}$", "$1");

        try {
            return LocalTime.parse(trimmed, TIME_FORMATTER);
        } catch (Exception e) {
            log.warn("Invalid time: {}", value);
            return null;
        }
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("NA")) {
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(value));
        } catch (NumberFormatException e) {
            log.warn("Invalid integer: {}", value);
            return null;
        }
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
