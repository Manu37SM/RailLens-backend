package com.labs.train.train_db.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.Savepoint;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.persistence.EntityManager;

import com.labs.train.train_db.entity.Station;
import com.labs.train.train_db.entity.Train;
import com.labs.train.train_db.repository.StationRepository;
import com.labs.train.train_db.repository.TrainRepository;
import com.labs.train.train_db.repository.TrainScheduleRepository;
import com.labs.train.train_db.service.RailwayDataImportService.ParsedRow;
import com.labs.train.train_db.service.RailwayImportBatchService.BatchImportResult;

/**
 * Covers the per-row persistence logic in {@code RailwayImportBatchService}/
 * {@code RailwayImportRowService} - new station/train creation, existing
 * station/train name updates, the delete-schedule-once-per-train rule
 * (shared across batches via {@code processedTrains}), and that a single
 * row's persistence failure is caught, rolled back to its own savepoint, and
 * counted rather than propagating and failing the whole batch.
 *
 * {@code entityManager}/{@code session}/{@code connection} are mocked rather
 * than provided by a real Spring/Hibernate context - this stays a plain,
 * fast Mockito unit test. The mocked {@code session.doReturningWork}/{@code
 * doWork} answers actually invoke the callback they're given against the
 * mocked {@code connection}, so the production code's real savepoint/
 * rollback/release call sequence is genuinely exercised, not just assumed.
 * {@code entityManager} is injected via {@code ReflectionTestUtils} because
 * it's a {@code @PersistenceContext} field, not a constructor parameter -
 * there's no public seam for it otherwise in a class only ever meant to be
 * constructed by Spring in production.
 */
@ExtendWith(MockitoExtension.class)
class RailwayImportBatchServiceTest {

        @Mock
        private StationRepository stationRepository;

        @Mock
        private TrainRepository trainRepository;

        @Mock
        private TrainScheduleRepository trainScheduleRepository;

        @Mock
        private EntityManager entityManager;

        @Mock
        private Session session;

        @Mock
        private Connection connection;

        @Mock
        private Savepoint savepoint;

        @BeforeEach
        void wireUpJdbcSavepointMocks() throws Exception {

                when(entityManager.unwrap(Session.class)).thenReturn(session);
                when(session.doReturningWork(any())).thenAnswer(
                                invocation -> invocation.<org.hibernate.jdbc.ReturningWork<?>>getArgument(0)
                                                .execute(connection));
                doAnswer(invocation -> {
                        invocation.<org.hibernate.jdbc.Work>getArgument(0).execute(connection);
                        return null;
                }).when(session).doWork(any());
                when(connection.setSavepoint()).thenReturn(savepoint);
        }

        private RailwayImportBatchService service() {
                RailwayImportRowService rowService = new RailwayImportRowService(
                                stationRepository, trainRepository, trainScheduleRepository);
                RailwayImportBatchService batchService = new RailwayImportBatchService(rowService);
                ReflectionTestUtils.setField(batchService, "entityManager", entityManager);
                return batchService;
        }

        private ParsedRow row(String trainNo, int seq, String stationCode) {
                return new ParsedRow(
                                trainNo, "Rajdhani Express", seq, stationCode, "Station " + stationCode,
                                LocalTime.of(8, 0), LocalTime.of(8, 5), 100, "raw:" + trainNo + ":" + seq);
        }

        @Test
        void createsNewStationsAndTrainsAndCachesThem() {

                Map<String, Station> stationCache = new HashMap<>();
                Map<String, Train> trainCache = new HashMap<>();
                Set<String> processedTrains = new HashSet<>();

                Station savedStation = new Station();
                savedStation.setStationCode("NDLS");
                savedStation.setStationName("Station NDLS");

                Train savedTrain = new Train();
                savedTrain.setTrainNumber("12301");
                savedTrain.setTrainName("Rajdhani Express");

                when(stationRepository.save(any())).thenReturn(savedStation);
                when(trainRepository.save(any())).thenReturn(savedTrain);

                BatchImportResult result = service().importBatch(
                                List.of(row("12301", 1, "NDLS")), stationCache, trainCache, processedTrains);

                assertThat(result.succeeded()).isEqualTo(1);
                assertThat(result.failed()).isZero();
                assertThat(stationCache).containsKey("NDLS");
                assertThat(trainCache).containsKey("12301");
                verify(trainScheduleRepository).save(any());
                verify(session).doWork(any()); // the savepoint release
        }

        @Test
        void updatesStationNameWhenItChangedSinceLastImport() {

                Station cached = new Station();
                cached.setStationCode("NDLS");
                cached.setStationName("Old Name");

                Map<String, Station> stationCache = new HashMap<>(Map.of("NDLS", cached));
                Map<String, Train> trainCache = new HashMap<>();
                Set<String> processedTrains = new HashSet<>();

                Train cachedTrain = new Train();
                cachedTrain.setTrainNumber("12301");
                cachedTrain.setTrainName("Rajdhani Express");
                trainCache.put("12301", cachedTrain);

                when(stationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                service().importBatch(
                                List.of(row("12301", 1, "NDLS")), stationCache, trainCache, processedTrains);

                verify(stationRepository).save(any());
                assertThat(stationCache.get("NDLS").getStationName()).isEqualTo("Station NDLS");
        }

        @Test
        void deletesExistingScheduleOnlyOnceForTheFirstRowOfEachTrainAcrossBatches() {

                Train cachedTrain = new Train();
                cachedTrain.setTrainNumber("12301");
                cachedTrain.setTrainName("Rajdhani Express");

                Station cachedStation = new Station();
                cachedStation.setStationCode("NDLS");
                cachedStation.setStationName("Station NDLS");

                Map<String, Station> stationCache = new HashMap<>(Map.of("NDLS", cachedStation));
                Map<String, Train> trainCache = new HashMap<>(Map.of("12301", cachedTrain));

                // Simulates the shared, cross-batch state RailwayDataImportService
                // passes in - a train already marked processed by an earlier
                // batch must not have its schedule deleted again.
                Set<String> processedTrains = new HashSet<>(Set.of("12301"));

                service().importBatch(
                                List.of(row("12301", 2, "NDLS")), stationCache, trainCache, processedTrains);

                verify(trainScheduleRepository, never()).deleteByTrain(any());
                verify(trainScheduleRepository).save(any());
        }

        @Test
        void aFailingRowIsCountedAsFailedWithoutFailingTheRestOfTheBatch() {

                Station cachedStation = new Station();
                cachedStation.setStationCode("NDLS");
                cachedStation.setStationName("Station NDLS");

                Train cachedTrain = new Train();
                cachedTrain.setTrainNumber("12301");
                cachedTrain.setTrainName("Rajdhani Express");

                Map<String, Station> stationCache = new HashMap<>(Map.of("NDLS", cachedStation));
                Map<String, Train> trainCache = new HashMap<>(Map.of("12301", cachedTrain));
                Set<String> processedTrains = new HashSet<>(Set.of("12301"));

                doThrow(new RuntimeException("constraint violation"))
                                .when(trainScheduleRepository).save(any());

                BatchImportResult result = service().importBatch(
                                List.of(row("12301", 2, "NDLS"), row("12301", 3, "NDLS")),
                                stationCache, trainCache, processedTrains);

                assertThat(result.failed()).isEqualTo(2);
                assertThat(result.succeeded()).isZero();
                verify(trainScheduleRepository, times(2)).save(any());
                // Each failing row rolls back to its own savepoint rather than
                // propagating - two rows, two rollbacks.
                verify(session, times(2)).doWork(any());
        }
}
