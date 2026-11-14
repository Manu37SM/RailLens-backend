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

        @Test
        void fromToKeepsOnlyTrainsRunningInThatDirection() {

                Train outbound = train(1L, "9101");
                Train inbound = train(2L, "9102");

                Station a = station("A");
                Station b = station("B");

                java.util.List<TrainSchedule> all = new java.util.ArrayList<>(List.of(
                                schedule(outbound, a, 1, null, LocalTime.of(8, 0), 0),
                                schedule(outbound, b, 2, LocalTime.of(9, 0), null, 100),
                                schedule(inbound, b, 1, null, LocalTime.of(10, 0), 0),
                                schedule(inbound, a, 2, LocalTime.of(11, 0), null, 100)));

                when(trainScheduleRepository.findAllByOrderByTrain_IdAscSequenceNoAsc()).thenReturn(all);
                when(stationRepository.findByStationCode("A")).thenReturn(Optional.of(a));
                when(stationRepository.findByStationCode("B")).thenReturn(Optional.of(b));

                SmartSearchResponse response = service().search("A to B");

                assertThat(response.recognized()).isTrue();
                assertThat(response.interpretedAs()).isEqualTo("Trains from A to B");
                assertThat(response.trains()).extracting(t -> t.trainNumber()).containsExactly("9101");
        }

        @Test
        void aCityNameMeansAllItsStations() {

                Train fromNewDelhi = train(1L, "9201");
                Train fromOldDelhiWrongWay = train(2L, "9202");

                Station ndls = station("NDLS");
                Station dli = station("DLI");
                Station mmct = station("MMCT");

                java.util.List<TrainSchedule> all = new java.util.ArrayList<>(List.of(
                                schedule(fromNewDelhi, ndls, 1, null, LocalTime.of(16, 0), 0),
                                schedule(fromNewDelhi, mmct, 2, LocalTime.of(8, 0), null, 1386),
                                schedule(fromOldDelhiWrongWay, mmct, 1, null, LocalTime.of(10, 0), 0),
                                schedule(fromOldDelhiWrongWay, dli, 2, LocalTime.of(9, 0), null, 1390)));

                when(trainScheduleRepository.findAllByOrderByTrain_IdAscSequenceNoAsc()).thenReturn(all);
                when(stationRepository.findTop25ByStationNameContainingIgnoreCaseOrderByStationCode("delhi"))
                                .thenReturn(List.of(dli, ndls));
                when(stationRepository.findTop25ByStationNameContainingIgnoreCaseOrderByStationCode("mumbai"))
                                .thenReturn(List.of(mmct));

                SmartSearchResponse response = service().search("delhi to mumbai");

                assertThat(response.interpretedAs()).isEqualTo("Trains from DELHI (2 stations) to MMCT");
                assertThat(response.trains()).extracting(t -> t.trainNumber()).containsExactly("9201");
        }
}
