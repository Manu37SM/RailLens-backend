package com.labs.train.train_db.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.labs.train.train_db.entity.Station;
import com.labs.train.train_db.entity.Train;
import com.labs.train.train_db.repository.StationRepository;
import com.labs.train.train_db.repository.TrainRepository;
import com.labs.train.train_db.repository.TrainScheduleRepository;
import com.labs.train.train_db.service.RailwayDataImportService.ParsedRow;
import com.labs.train.train_db.service.RailwayImportBatchService.BatchImportResult;

/**
 * Covers the per-row persistence logic moved out of
 * {@code RailwayDataImportService} by the transaction-per-batch refactor
 * (see this class's javadoc) - new station/train creation, existing
 * station/train name updates, the delete-schedule-once-per-train rule
 * (shared across batches via {@code processedTrains}), and that a single
 * row's persistence failure is caught and counted rather than propagating
 * and failing the whole batch.
 */
@ExtendWith(MockitoExtension.class)
class RailwayImportBatchServiceTest {

        @Mock
        private StationRepository stationRepository;

        @Mock
        private TrainRepository trainRepository;

        @Mock
        private TrainScheduleRepository trainScheduleRepository;

        private RailwayImportBatchService service() {
                return new RailwayImportBatchService(stationRepository, trainRepository, trainScheduleRepository);
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
        }
}
