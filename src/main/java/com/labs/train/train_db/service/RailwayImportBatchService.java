package com.labs.train.train_db.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.labs.train.train_db.entity.Station;
import com.labs.train.train_db.entity.Train;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Persists one already-parsed batch of {@link RailwayDataImportService}'s
 * CSV rows in its own, independently committed transaction.
 *
 * Split out of {@code RailwayDataImportService} specifically so each batch
 * gets {@code REQUIRES_NEW} - a fresh {@link jakarta.persistence.EntityManager}
 * per call, committed and disposed of when the call returns - rather than
 * the whole ~300k-row import living in one open transaction. That previous
 * shape had two real problems on a resource-capped deployment: it pinned
 * one of a small (10-connection) pool for the entire import, and a
 * late-in-the-run failure (e.g. a flush-deferred constraint violation)
 * rolled back rows that {@code importCsv()}'s per-row loop had already
 * counted as successful, so the reported count could lie.
 *
 * Trade-off worth knowing: with per-batch commits, the import is no longer
 * atomic as a whole - only per batch. In the (rare, since a single train's
 * route is at most a few dozen rows) case where one train's schedule
 * straddles a batch boundary, there's a brief window between batches where
 * that train's old schedule has been deleted but its new rows haven't
 * landed yet. For an admin-triggered, low-concurrency import this is an
 * acceptable trade against no longer risking a full-run rollback silently
 * misreporting success counts, or holding a connection for the whole run.
 *
 * Must be a separate Spring bean, not a private method on
 * {@code RailwayDataImportService} - {@code @Transactional} only takes
 * effect through Spring's proxy, which self-invocation (a method calling
 * another method on {@code this}) bypasses entirely.
 *
 * Each row's actual persistence is further delegated to {@link
 * RailwayImportRowService#saveRow}, which runs in its own {@code
 * Propagation.NESTED} transaction (a JDBC savepoint inside this batch's
 * transaction) - see that class's javadoc for why: without it, one row's
 * constraint violation aborted the whole Postgres transaction and silently
 * failed every other row in the same batch too.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RailwayImportBatchService {

        private final RailwayImportRowService rowService;

        public record BatchImportResult(int succeeded, int failed) {
        }

        /**
         * {@code stationCache}/{@code trainCache}/{@code processedTrains} are
         * shared, mutated in place, and passed in from
         * {@code RailwayDataImportService} across every batch call - same
         * "detached entity is fine, only its already-loaded id is needed for
         * the FK column" reasoning the previous single-transaction version
         * relied on (see that class's javadoc), now additionally true
         * because entities saved/loaded in a prior batch's transaction are
         * detached the moment that batch's {@code REQUIRES_NEW} transaction
         * commits and its {@code EntityManager} closes.
         */
        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public BatchImportResult importBatch(
                        List<RailwayDataImportService.ParsedRow> rows,
                        Map<String, Station> stationCache,
                        Map<String, Train> trainCache,
                        Set<String> processedTrains) {

                int succeeded = 0;
                int failed = 0;

                for (RailwayDataImportService.ParsedRow row : rows) {

                        // Decided here, not inside the NESTED transaction below: if
                        // this row's save fails and rolls back, processedTrains must
                        // NOT have already been marked for this train, or the delete
                        // (which rolled back along with everything else in that
                        // savepoint) would never be retried on a later row - see
                        // RailwayImportRowService's javadoc.
                        boolean deleteExistingSchedule = !processedTrains.contains(row.trainNo());

                        try {

                                RailwayImportRowService.RowSaveResult result = rowService.saveRow(
                                                row, stationCache, trainCache, deleteExistingSchedule);

                                // Cache/bookkeeping writes only happen here, after saveRow
                                // has returned successfully - see that method's javadoc for
                                // why writing them from inside its own (possibly
                                // rolled-back) transaction would be unsafe.
                                stationCache.put(row.stationCode(), result.station());
                                trainCache.put(row.trainNo(), result.train());

                                if (deleteExistingSchedule) {
                                        processedTrains.add(row.trainNo());
                                }

                                succeeded++;

                        } catch (Exception ex) {
                                failed++;
                                log.error("Failed to import row: {}", row.rawRecord(), ex);
                        }
                }

                return new BatchImportResult(succeeded, failed);
        }
}
