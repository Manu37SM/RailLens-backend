package com.labs.train.train_db.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.labs.train.train_db.entity.Station;
import com.labs.train.train_db.entity.Train;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Loops over one already-parsed batch of {@link RailwayDataImportService}'s
 * CSV rows, delegating each row's actual persistence to {@link
 * RailwayImportRowService#saveRow}.
 *
 * Not itself {@code @Transactional} - it used to be (each batch committed as
 * its own {@code REQUIRES_NEW} transaction, see git history), but every
 * row's persistence now already runs in its own independent {@code
 * REQUIRES_NEW} transaction (see {@code RailwayImportRowService}'s javadoc
 * for why: a batch-wide transaction meant one row's constraint violation
 * aborted the whole thing, silently failing every other row in that batch
 * too). Once every row suspends and runs its own transaction regardless, a
 * transaction annotation here would only hold an unused connection idle for
 * the whole batch - this class is now purely a loop and a running tally,
 * with no database transaction of its own.
 *
 * Must be a separate Spring bean, not a private method on {@code
 * RailwayDataImportService} - {@code @Transactional} (on {@code
 * RailwayImportRowService#saveRow}, which this class calls) only takes
 * effect through Spring's proxy, which self-invocation (a method calling
 * another method on {@code this}) bypasses entirely.
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
         * shared, mutated in place, and passed in from {@code
         * RailwayDataImportService} across every batch call - same "detached
         * entity is fine, only its already-loaded id is needed for the FK
         * column" reasoning {@code RailwayImportRowService} relies on, since
         * every row's save happens in its own transaction and detaches
         * immediately on return.
         */
        public BatchImportResult importBatch(
                        List<RailwayDataImportService.ParsedRow> rows,
                        Map<String, Station> stationCache,
                        Map<String, Train> trainCache,
                        Set<String> processedTrains) {

                int succeeded = 0;
                int failed = 0;

                for (RailwayDataImportService.ParsedRow row : rows) {

                        // Decided here, not inside saveRow's own transaction: if this
                        // row's save fails and rolls back, processedTrains must NOT
                        // have already been marked for this train, or the delete
                        // (which rolled back along with everything else in that row's
                        // transaction) would never be retried on a later row - see
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
