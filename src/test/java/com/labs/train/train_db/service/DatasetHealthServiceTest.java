package com.labs.train.train_db.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.labs.train.train_db.entity.Station;
import com.labs.train.train_db.entity.Train;
import com.labs.train.train_db.entity.TrainSchedule;
import com.labs.train.train_db.model.DatasetHealthResponse;
import com.labs.train.train_db.repository.StationRepository;
import com.labs.train.train_db.repository.TrainScheduleRepository;

@ExtendWith(MockitoExtension.class)
class DatasetHealthServiceTest {

        @Mock
        private StationRepository stationRepository;

        @Mock
        private TrainScheduleRepository trainScheduleRepository;

        private DatasetHealthService service() {
                return new DatasetHealthService(stationRepository, trainScheduleRepository);
        }

        private static Train train(long id, String number) {
                Train train = new Train();
                train.setId(id);
                train.setTrainNumber(number);
                train.setTrainName("Train " + number);
                return train;
        }

        private static Station station(String code) {
                Station station = new Station();
                station.setStationCode(code);
                station.setStationName(code + " Station");
                return station;
        }

        private static TrainSchedule schedule(
                        Train train, Station station, int sequenceNo, LocalTime arrival, LocalTime departure, Integer distance) {

                TrainSchedule schedule = new TrainSchedule();
                schedule.setTrain(train);
                schedule.setStation(station);
                schedule.setSequenceNo(sequenceNo);
                schedule.setArrivalTime(arrival);
                schedule.setDepartureTime(departure);
                schedule.setDistance(distance);
                return schedule;
        }

        @Test
        void reportsNoIssuesForACleanDataset() {

                Train t = train(1L, "9001");
                Station a = station("A");
                Station b = station("B");

                List<TrainSchedule> route = List.of(
                                schedule(t, a, 1, null, LocalTime.of(8, 0), 0),
                                schedule(t, b, 2, LocalTime.of(10, 0), null, 200));

                when(trainScheduleRepository.findAllByOrderByTrain_IdAscSequenceNoAsc()).thenReturn(route);
                when(stationRepository.findAll()).thenReturn(List.of(a, b));

                DatasetHealthResponse response = service().checkHealth();

                assertThat(response.totalIssues()).isZero();
        }

        @Test
        void flagsDistanceDecreaseImpossibleSpeedAndOrphanStation() {

                Train t = train(1L, "9001");
                Station a = station("A");
                Station b = station("B");
                Station orphan = station("ZZZ");

                // 200km in 6 minutes = 2000 km/h - an impossible implied speed.
                List<TrainSchedule> route = List.of(
                                schedule(t, a, 1, null, LocalTime.of(8, 0), 0),
                                schedule(t, b, 2, LocalTime.of(8, 6), null, 200));

                // A second train with a genuinely impossible implied speed:
                // 500km in 10 minutes.
                Train fast = train(2L, "9002");
                Station c = station("C");
                Station d = station("D");

                List<TrainSchedule> fastRoute = List.of(
                                schedule(fast, c, 1, null, LocalTime.of(9, 0), 0),
                                schedule(fast, d, 2, LocalTime.of(9, 10), null, 500));

                java.util.List<TrainSchedule> all = new java.util.ArrayList<>();
                all.addAll(route);
                all.addAll(fastRoute);

                when(trainScheduleRepository.findAllByOrderByTrain_IdAscSequenceNoAsc()).thenReturn(all);
                when(stationRepository.findAll()).thenReturn(List.of(a, b, c, d, orphan));

                DatasetHealthResponse response = service().checkHealth();

                // Both trains imply a speed above the 200 km/h ceiling: 9001 at
                // 2000 km/h (200km/6min) and 9002 at 3000 km/h (500km/10min).
                assertThat(response.impossibleSpeedCount()).isEqualTo(2);
                assertThat(response.impossibleSpeedSamples())
                                .anySatisfy(sample -> assertThat(sample).contains("9001"))
                                .anySatisfy(sample -> assertThat(sample).contains("9002"));

                assertThat(response.orphanStationCount()).isEqualTo(1);
                assertThat(response.orphanStationSamples().get(0)).contains("ZZZ");

                assertThat(response.totalIssues()).isGreaterThan(0);
        }

        @Test
        void flagsInvalidRouteWithFewerThanTwoStops() {

                Train t = train(1L, "9003");
                Station a = station("A");

                when(trainScheduleRepository.findAllByOrderByTrain_IdAscSequenceNoAsc())
                                .thenReturn(List.of(schedule(t, a, 1, null, LocalTime.of(8, 0), 0)));
                when(stationRepository.findAll()).thenReturn(List.of(a));

                DatasetHealthResponse response = service().checkHealth();

                assertThat(response.invalidRouteCount()).isEqualTo(1);
                assertThat(response.invalidRouteSamples().get(0)).contains("9003");
        }

        @Test
        void flagsDuplicateSequenceNumbersAndLongHalts() {

                Train t = train(1L, "9004");
                Station a = station("A");
                Station b = station("B");
                Station c = station("C");

                List<TrainSchedule> route = List.of(
                                schedule(t, a, 1, null, LocalTime.of(8, 0), 0),
                                schedule(t, b, 2, LocalTime.of(9, 0), LocalTime.of(12, 0), 100),
                                // Duplicate sequence number 2.
                                schedule(t, c, 2, LocalTime.of(13, 0), null, 200));

                when(trainScheduleRepository.findAllByOrderByTrain_IdAscSequenceNoAsc()).thenReturn(route);
                when(stationRepository.findAll()).thenReturn(List.of(a, b, c));

                DatasetHealthResponse response = service().checkHealth();

                assertThat(response.duplicateScheduleRowCount()).isEqualTo(1);

                // 3-hour halt at B (09:00 -> 12:00) exceeds the 180-minute
                // threshold.
                assertThat(response.haltAnomalyCount()).isGreaterThanOrEqualTo(1);
                assertThat(response.haltAnomalySamples()).anySatisfy(sample -> assertThat(sample).contains("9004"));
        }
}
