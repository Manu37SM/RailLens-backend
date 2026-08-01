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

    /**
     * A single already-parsed CSV row, handed off to
     * {@link RailwayImportBatchService} for persistence. Parsing (this
     * class) and persisting (the batch service, one {@code REQUIRES_NEW}
     * transaction per {@link AppConstants#IMPORT_BATCH_SIZE} rows) are
     * deliberately separate steps now - see RailwayImportBatchService's
     * javadoc for why.
     */
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

    /**
     * Imports {@code data/train_dataset.csv}. This used to return {@code
     * void} and swallow any top-level failure with {@code
     * e.printStackTrace()} - the caller (see {@code
     * RailwayDataImportController}) had no way to tell a successful import
     * from a silently failed one; it always returned the same hardcoded
     * "Import Started" string. This now returns a result the controller can
     * actually report.
     *
     * Parses rows here, then hands each {@code AppConstants.IMPORT_BATCH_SIZE}
     * chunk to {@link RailwayImportBatchService#importBatch} for persistence
     * in its own committed transaction - see that class's javadoc for why
     * the whole import no longer runs as one transaction (this used to be a
     * single {@code @Transactional} method with a periodic {@code
     * entityManager.flush()/clear()} to keep Hibernate's persistence context
     * from holding all ~300k entities as "managed" at once; per-batch
     * commits now solve both that memory concern and the single-connection/
     * all-or-nothing-rollback concerns the flush-only approach didn't).
     */
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
                    log.error("Failed to parse row: {}", record.toString(), ex);
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

            // Final partial batch (fewer than IMPORT_BATCH_SIZE rows) that
            // the loop above never reached the threshold for.
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

            // Even a failed run may have written some rows before hitting the
            // error (per-row/per-batch failures are caught and skipped
            // above; this catch is for something failing outside that, e.g.
            // the CSV resource itself being unreadable). Evict rather than
            // risk serving stale cached routes for whatever did get
            // written - each completed batch is already committed by this
            // point, unlike the old single-transaction version where a
            // failure here would have rolled everything back.
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

        // SEQ drives stop ordering, so unlike Distance a missing/unparseable value is a
        // hard failure rather than falling back to null. Still tolerant of a trailing
        // ".0" (e.g. "4.0"), same as Distance, since both come from the same source data.
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

        // Some source CSVs include seconds (e.g. "18:05:00") — strip that part before
        // parsing, so we always end up with plain 24-hour H:mm. The pattern only
        // matches when there are two colon-separated groups after the hour (i.e.
        // an actual trailing ":ss"), so a plain "8:05" or "18:05" is left alone -
        // an earlier, looser version of this regex (":\d{2}$") also matched plain
        // H:mm's own minutes and silently truncated "8:05" down to "8", which
        // then failed to parse. Handles both single- and double-digit hours.
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
            // Some source CSVs write whole numbers with a trailing decimal (e.g. "245.0").
            // Parse as a double and round, rather than failing on the decimal point.
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
