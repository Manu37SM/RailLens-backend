package com.labs.train.train_db.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.labs.train.train_db.entity.Station;
import com.labs.train.train_db.entity.Train;
import com.labs.train.train_db.entity.TrainSchedule;
import com.labs.train.train_db.exception.ResourceNotFoundException;
import com.labs.train.train_db.model.StationIntelligenceResponse;
import com.labs.train.train_db.repository.StationRepository;
import com.labs.train.train_db.repository.TrainScheduleRepository;
import com.labs.train.train_db.service.network.RailwayNetworkService;

@ExtendWith(MockitoExtension.class)
class StationIntelligenceServiceTest {

        @Mock
        private StationRepository stationRepository;

        @Mock
        private TrainScheduleRepository trainScheduleRepository;

        @Mock
        private ScheduleSnapshotService scheduleSnapshotService;

        private StationIntelligenceService service() {
                return new StationIntelligenceService(
                                stationRepository,
                                trainScheduleRepository,
                                new RailwayNetworkService(scheduleSnapshotService));
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
                        long id, Train train, Station station, int sequenceNo,
                        LocalTime arrival, LocalTime departure, Integer distance) {

                TrainSchedule schedule = new TrainSchedule();
                schedule.setId(id);
                schedule.setTrain(train);
                schedule.setStation(station);
                schedule.setSequenceNo(sequenceNo);
                schedule.setArrivalTime(arrival);
                schedule.setDepartureTime(departure);
                schedule.setDistance(distance);
                return schedule;
        }

        @Test
        void throwsWhenTheStationDoesNotExist() {

                when(stationRepository.findByStationCode("ZZZZ")).thenReturn(Optional.empty());

                assertThatThrownBy(() -> service().getIntelligence("ZZZZ"))
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("ZZZZ");
        }

        @Test
        void computesExactMetricsForAStarShapedNetwork() {

                Train t9001 = train(1L, "9001");
                Train t9002 = train(2L, "9002");

                Station a = station("A");
                Station b = station("B");
                Station c = station("C");
                Station x = station("X");
                Station y = station("Y");

                TrainSchedule s1 = schedule(1L, t9001, a, 1, null, LocalTime.of(8, 0), 0);
                TrainSchedule s2 = schedule(2L, t9001, b, 2, LocalTime.of(9, 0), LocalTime.of(9, 10), 100);
                TrainSchedule s3 = schedule(3L, t9001, c, 3, LocalTime.of(10, 0), null, 200);

                TrainSchedule s4 = schedule(4L, t9002, x, 1, null, LocalTime.of(8, 0), 0);
                TrainSchedule s5 = schedule(5L, t9002, b, 2, LocalTime.of(8, 30), LocalTime.of(8, 40), 50);
                TrainSchedule s6 = schedule(6L, t9002, y, 3, LocalTime.of(9, 10), null, 100);

                List<TrainSchedule> route9001 = List.of(s1, s2, s3);
                List<TrainSchedule> route9002 = List.of(s4, s5, s6);

                List<TrainSchedule> allSchedules = new ArrayList<>();
                allSchedules.addAll(route9001);
                allSchedules.addAll(route9002);

                when(stationRepository.findByStationCode("B"))
                                .thenReturn(Optional.of(b));
                when(scheduleSnapshotService.getAllOrderedByTrainThenSequence())
                                .thenReturn(allSchedules);
                when(trainScheduleRepository.findByStation_StationCodeOrderByArrivalTime("B"))
                                .thenReturn(List.of(s2, s5));
                when(trainScheduleRepository.findByTrain_IdInOrderByTrain_IdAscSequenceNoAsc(List.of(1L, 2L)))
                                .thenReturn(allSchedules);

                StationIntelligenceResponse response = service().getIntelligence("B");

                assertThat(response.stationCode()).isEqualTo("B");
                assertThat(response.totalStationsInNetwork()).isEqualTo(5);

                assertThat(response.degree()).isEqualTo(4);
                assertThat(response.connectivityScore()).isCloseTo(100.0, within(0.01));
                assertThat(response.networkRank()).isEqualTo(1);

                assertThat(response.closenessCentrality()).isCloseTo(1.0, within(0.0001));

                assertThat(response.betweennessCentrality()).isCloseTo(6.0, within(0.01));

                assertThat(response.totalStops()).isEqualTo(2);
                assertThat(response.originCount()).isZero();
                assertThat(response.destinationCount()).isZero();
                assertThat(response.transitCount()).isEqualTo(2);
                assertThat(response.transitPercent()).isCloseTo(100.0, within(0.01));

                assertThat(response.averageHaltMinutes()).isCloseTo(10.0, within(0.01));

                assertThat(response.averageTrainSpeedKmh()).isCloseTo(105.0, within(0.1));

                assertThat(response.stationImportanceScore()).isCloseTo(100.0, within(0.01));

                assertThat(response.departureCountByHour()[8]).isEqualTo(1);
                assertThat(response.departureCountByHour()[9]).isEqualTo(1);
                assertThat(response.arrivalCountByHour()[8]).isEqualTo(1);
                assertThat(response.arrivalCountByHour()[9]).isEqualTo(1);
        }

        @Test
        void returnsZeroedResponseForAStationWithNoScheduleRows() {

                Station fresh = station("NEW1");

                when(stationRepository.findByStationCode("NEW1")).thenReturn(Optional.of(fresh));
                when(scheduleSnapshotService.getAllOrderedByTrainThenSequence()).thenReturn(List.of());
                when(trainScheduleRepository.findByStation_StationCodeOrderByArrivalTime("NEW1"))
                                .thenReturn(List.of());

                StationIntelligenceResponse response = service().getIntelligence("NEW1");

                assertThat(response.stationCode()).isEqualTo("NEW1");
                assertThat(response.networkRank()).isNull();
                assertThat(response.degree()).isZero();
                assertThat(response.totalStops()).isZero();
                assertThat(response.averageTrainSpeedKmh()).isNull();
                assertThat(response.departureCountByHour()).hasSize(24);
                assertThat(response.arrivalCountByHour()).hasSize(24);
        }
}
