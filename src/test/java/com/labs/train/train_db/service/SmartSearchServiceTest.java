package com.labs.train.train_db.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.labs.train.train_db.entity.Station;
import com.labs.train.train_db.entity.Train;
import com.labs.train.train_db.entity.TrainSchedule;
import com.labs.train.train_db.model.SmartSearchResponse;
import com.labs.train.train_db.repository.StationRepository;
import com.labs.train.train_db.repository.TrainScheduleRepository;

@ExtendWith(MockitoExtension.class)
class SmartSearchServiceTest {

        @Mock
        private StationRepository stationRepository;

        // TrainSummaryIndex now reads through ScheduleSnapshotService instead of
        // TrainScheduleRepository directly (see that class's javadoc - it was
        // an independent full-table load duplicating what ScheduleSnapshotService
        // already consolidates for every other "Railway Intelligence" service).
        // The mock is still set up per-test via trainScheduleRepository so the
        // existing when(...findAllByOrderBy...) calls below don't need to
        // change - ScheduleSnapshotService.getAllOrderedByTrainThenSequence()
        // just delegates straight to that same repository method.
        @Mock
        private TrainScheduleRepository trainScheduleRepository;

        private SmartSearchService service() {
                ScheduleSnapshotService snapshotService = new ScheduleSnapshotService(trainScheduleRepository);
                TrainSummaryIndex index = new TrainSummaryIndex(snapshotService, new JourneyDayCalculator());
                return new SmartSearchService(stationRepository, index);
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
        void reportsUnrecognizedForAnUnparseableQuery() {

                SmartSearchResponse response = service().search("what is the weather today");

                assertThat(response.recognized()).isFalse();
                assertThat(response.matchCount()).isZero();
        }

        @Test
        void findsTrainsStoppingAtBothStations() {

                Train t9001 = train(1L, "9001");
                Train t9002 = train(2L, "9002");

                Station a = station("A");
                Station b = station("B");
                Station c = station("C");

                List<TrainSchedule> route9001 = List.of(
                                schedule(t9001, a, 1, null, LocalTime.of(8, 0), 0),
                                schedule(t9001, b, 2, LocalTime.of(9, 0), LocalTime.of(9, 5), 100),
                                schedule(t9001, c, 3, LocalTime.of(10, 0), null, 200));

                List<TrainSchedule> route9002 = List.of(
                                schedule(t9002, a, 1, null, LocalTime.of(7, 0), 0),
                                schedule(t9002, c, 2, LocalTime.of(9, 0), null, 250));

                java.util.List<TrainSchedule> all = new java.util.ArrayList<>();
                all.addAll(route9001);
                all.addAll(route9002);

                when(trainScheduleRepository.findAllByOrderByTrain_IdAscSequenceNoAsc()).thenReturn(all);
                when(stationRepository.findByStationCode("A")).thenReturn(Optional.of(a));
                when(stationRepository.findByStationCode("B")).thenReturn(Optional.of(b));

                SmartSearchResponse response = service().search("trains that stop at both A and B");

                // Only 9001 stops at both A and B - 9002 skips B entirely.
                assertThat(response.recognized()).isTrue();
                assertThat(response.matchCount()).isEqualTo(1);
                assertThat(response.trains().get(0).trainNumber()).isEqualTo("9001");
        }

        @Test
        void findsTrainsLongerThanAGivenDistance() {

                Train shortTrain = train(1L, "1001");
                Train longTrain = train(2L, "1002");

                Station a = station("A");
                Station b = station("B");

                List<TrainSchedule> shortRoute = List.of(
                                schedule(shortTrain, a, 1, null, LocalTime.of(8, 0), 0),
                                schedule(shortTrain, b, 2, LocalTime.of(9, 0), null, 100));

                List<TrainSchedule> longRoute = List.of(
                                schedule(longTrain, a, 1, null, LocalTime.of(8, 0), 0),
                                schedule(longTrain, b, 2, LocalTime.of(20, 0), null, 900));

                java.util.List<TrainSchedule> all = new java.util.ArrayList<>();
                all.addAll(shortRoute);
                all.addAll(longRoute);

                when(trainScheduleRepository.findAllByOrderByTrain_IdAscSequenceNoAsc()).thenReturn(all);

                SmartSearchResponse response = service().search("trains longer than 500km");

                assertThat(response.matchCount()).isEqualTo(1);
                assertThat(response.trains().get(0).trainNumber()).isEqualTo("1002");
        }

        @Test
        void reportsAnUnresolvedStationWithoutCrashing() {

                when(trainScheduleRepository.findAllByOrderByTrain_IdAscSequenceNoAsc()).thenReturn(List.of());
                when(stationRepository.findByStationCode("NOWHERE")).thenReturn(Optional.empty());
                when(stationRepository.search(org.mockito.ArgumentMatchers.eq("NOWHERE"), any()))
                                .thenReturn(org.springframework.data.domain.Page.empty());

                SmartSearchResponse response = service().search("trains that stop at NOWHERE");

                assertThat(response.recognized()).isTrue();
                assertThat(response.matchCount()).isZero();
                assertThat(response.interpretedAs()).contains("NOWHERE");
        }
}
