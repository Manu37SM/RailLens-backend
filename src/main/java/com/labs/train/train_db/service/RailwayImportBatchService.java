package com.labs.train.train_db.service;

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

@Slf4j
@Service
@RequiredArgsConstructor
public class RailwayImportBatchService {

        private final RailwayImportRowService rowService;

        @PersistenceContext
        private EntityManager entityManager;

        public record BatchImportResult(int succeeded, int failed) {
        }

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

                        Savepoint savepoint = session.doReturningWork(connection -> connection.setSavepoint());

                        boolean deleteExistingSchedule = !processedTrains.contains(row.trainNo());

                        try {

                                RailwayImportRowService.RowSaveResult result = rowService.saveRow(
                                                row, stationCache, trainCache, deleteExistingSchedule);

                                entityManager.flush();

                                session.doWork(connection -> connection.releaseSavepoint(savepoint));

                                stationCache.put(row.stationCode(), result.station());
                                trainCache.put(row.trainNo(), result.train());

                                if (deleteExistingSchedule) {
                                        processedTrains.add(row.trainNo());
                                }

                                succeeded++;

                        } catch (Exception ex) {

                                failed++;
                                log.warn("Failed to import row {}: {}", row.rawRecord(), ex.toString());

                                try {
                                        session.doWork(connection -> connection.rollback(savepoint));
                                } catch (Exception rollbackEx) {
                                        log.error(
                                                        "Failed to roll back savepoint for row {} - aborting the rest of this batch",
                                                        row.rawRecord(), rollbackEx);
                                        throw rollbackEx;
                                }

                        } finally {

                                entityManager.clear();
                        }
                }

                return new BatchImportResult(succeeded, failed);
        }
}
