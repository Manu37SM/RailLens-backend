package com.labs.train.train_db.service;

import java.sql.Connection;
import java.sql.Savepoint;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.labs.train.train_db.entity.Station;
import com.labs.train.train_db.entity.Train;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Persists one already-parsed batch of {@link RailwayDataImportService}'s
 * CSV rows in its own {@code REQUIRES_NEW} transaction (one commit per
 * {@code AppConstants.IMPORT_BATCH_SIZE}-row batch, ~236 for the full CSV) -
 * with per-row failure isolation from a manually managed raw JDBC {@link
 * Savepoint}, not a Spring-managed nested transaction.
 *
 * This is the third design for per-row isolation, after two that each
 * failed differently - keeping the history here since the wrong one is an
 * easy trap to fall back into:
 *
 * <ol>
 *   <li><b>{@code Propagation.NESTED} per row</b> (2026-08-02): failed
 *   outright in production with {@code NestedTransactionNotSupportedException:
 *   JpaDialect does not support savepoints}. Spring's {@code
 *   JpaTransactionManager} can only back NESTED with a real savepoint if the
 *   configured {@code JpaDialect} exposes the underlying JDBC {@code
 *   Connection}, and the Hibernate 7 dialect this project runs on doesn't.
 *   Not fixable from application code - a hard limitation of this stack.</li>
 *   <li><b>{@code Propagation.REQUIRES_NEW} per row</b> (2026-08-03): worked
 *   correctly, but a production run showed each successive 10,000-row chunk
 *   taking noticeably longer than the last (roughly 1 minute per 10k rows
 *   early on, over 12 minutes per 10k rows after 90k rows) - a compounding,
 *   not flat, per-row cost, on top of ~235k individual transaction commits
 *   instead of ~236. Most likely cause: Spring Boot's Open-Session-In-View
 *   (on by default) binding one Hibernate session to the entire multi-hour
 *   HTTP request, with entities accumulating in it as "managed" for the
 *   whole run rather than being released - see {@code
 *   spring.jpa.open-in-view=false} in render.yaml and
 *   application.properties.example, turned off alongside this fix.</li>
 *   <li><b>This version</b> (2026-08-04): restores the original batch-level
 *   transaction for speed, and gets per-row isolation from a raw JDBC
 *   savepoint taken directly on the underlying {@code Connection} via
 *   {@code Session.doReturningWork}/{@code doWork} - sidestepping
 *   {@code JpaDialect} entirely, since this never goes through Spring's
 *   savepoint abstraction. {@code entityManager.clear()} after every single
 *   row (success or failure) is the direct fix for attempt 2's compounding
 *   slowdown: it guarantees this batch's persistence context never holds
 *   more than one row's worth of managed entities at a time, regardless of
 *   how many rows this batch or the overall import has processed so far.</li>
 * </ol>
 *
 * Must be a separate Spring bean, not a private method on {@code
 * RailwayDataImportService} - {@code @Transactional} only takes effect
 * through Spring's proxy, which self-invocation (a method calling another
 * method on {@code this}) bypasses entirely.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RailwayImportBatchService {

        private final RailwayImportRowService rowService;

        @PersistenceContext
        private EntityManager entityManager;

        public record BatchImportResult(int succeeded, int failed) {
        }

        /**
         * {@code stationCache}/{@code trainCache}/{@code processedTrains} are
         * shared, mutated in place, and passed in from {@code
         * RailwayDataImportService} across every batch call - entities put into
         * {@code stationCache}/{@code trainCache} are detached the moment this
         * method's per-row {@code entityManager.clear()} runs, so later
         * rows/batches only ever see them as detached references. That's fine -
         * only their already-assigned ID (assigned immediately at {@code save()}
         * time, since every entity here uses {@code GenerationType.IDENTITY})
         * is ever needed, to build a foreign key column, never their full
         * managed state.
         */
        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public BatchImportResult importBatch(
                        List<RailwayDataImportService.ParsedRow> rows,
                        Map<String, Station> stationCache,
                        Map<String, Train> trainCache,
                        Set<String> processedTrains) {

                Session session = entityManager.unwrap(Session.class);

                int succeeded = 0;
                int failed = 0;

                for (RailwayDataImportService.ParsedRow row : rows) {

                        // Taken before this row's work so a failure can roll back just
                        // this row's statements, not the rest of the batch - see this
                        // class's javadoc for why this is raw JDBC rather than Spring's
                        // Propagation.NESTED (unsupported on this stack) or a second
                        // REQUIRES_NEW transaction (correct, but too slow at CSV scale).
                        // Explicit lambda rather than Connection::setSavepoint - avoids
                        // the JDT null analyzer's "unchecked conversion for the
                        // receiver" warning on the method-reference form (same
                        // reasoning as the other fixes in this codebase); same
                        // behavior either way.
                        Savepoint savepoint = session.doReturningWork(connection -> connection.setSavepoint());

                        // Decided here, not after the fact: if this row's save fails and
                        // rolls back, processedTrains must NOT have already been marked
                        // for this train, or the delete (which rolled back along with
                        // everything else at this row's savepoint) would never be
                        // retried on a later row.
                        boolean deleteExistingSchedule = !processedTrains.contains(row.trainNo());

                        try {

                                RailwayImportRowService.RowSaveResult result = rowService.saveRow(
                                                row, stationCache, trainCache, deleteExistingSchedule);

                                // Forces this row's SQL to execute now, inside this row's own
                                // try block, rather than being deferred to whenever Hibernate
                                // next decides to flush - which could span several rows and
                                // attribute a failure to the wrong one, or to none at all
                                // until much later. Also mostly redundant in practice, since
                                // every entity here uses GenerationType.IDENTITY (forces an
                                // immediate INSERT at save() time) and deleteByTrain() is a
                                // derived bulk-delete query (already immediate, not deferred)
                                // - but the explicit flush makes that guarantee obvious rather
                                // than implicit, and keeps this method correct even if a
                                // future entity here used a different generation strategy.
                                entityManager.flush();

                                session.doWork(connection -> connection.releaseSavepoint(savepoint));

                                // Cache/bookkeeping writes only happen here, after the flush
                                // above has actually succeeded - see RailwayImportRowService's
                                // javadoc for why writing them any earlier would be unsafe.
                                stationCache.put(row.stationCode(), result.station());
                                trainCache.put(row.trainNo(), result.train());

                                if (deleteExistingSchedule) {
                                        processedTrains.add(row.trainNo());
                                }

                                succeeded++;

                        } catch (Exception ex) {

                                failed++;
                                log.warn("Failed to import row {}: {}", row.rawRecord(), ex.toString());

                                // Rolling back to the savepoint is what actually lifts
                                // Postgres out of the "current transaction is aborted" state
                                // a failed statement leaves it in - without this, every later
                                // row in the batch would fail too (the exact 2026-08-01
                                // incident this design exists to prevent). If the rollback
                                // itself fails, the connection is in an unknown state and
                                // continuing would risk silently corrupting later rows, so
                                // this rethrows rather than swallowing it.
                                try {
                                        session.doWork(connection -> connection.rollback(savepoint));
                                } catch (Exception rollbackEx) {
                                        log.error(
                                                        "Failed to roll back savepoint for row {} - aborting the rest of this batch",
                                                        row.rawRecord(), rollbackEx);
                                        throw rollbackEx;
                                }

                        } finally {

                                // Detaches everything this row touched, whether it succeeded
                                // or failed - see this class's javadoc (design 3) for why:
                                // without this, the batch's persistence context keeps every
                                // row's entities "managed" for the rest of the batch, and
                                // Hibernate's dirty-checking cost at each flush grows with
                                // however many rows have accumulated so far in this batch.
                                entityManager.clear();
                        }
                }

                return new BatchImportResult(succeeded, failed);
        }
}
