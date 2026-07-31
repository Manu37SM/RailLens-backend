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
import com.labs.train.train_db.model.FunStatsResponse;
import com.labs.train.train_db.repository.StationRepository;

@ExtendWith(MockitoExtension.class)
class FunStatsServiceTest {

        @Mock
        private StationRepository stationRepository;

        @Mock
        private ScheduleSnapshotService scheduleSnapshotService;

        private FunStatsService service() {
                return new FunStatsService(stationRepository, scheduleSnapshotService);
        }

        private static Station station(String code, String name) {
                Station station = new Station();
                station.setStationCode(code);
                station.setStationName(name);
                return station;
        }

        private static Train train(long id, String number) {
                Train train = new Train();
                train.setId(id);
                train.setTrainNumber(number);
                train.setTrainName("Train " + number);
                return train;
        }

        private static TrainSchedule schedule(Train train, Station station, int sequenceNo) {
                TrainSchedule schedule = new TrainSchedule();
                schedule.setTrain(train);
                schedule.setStation(station);
                schedule.setSequenceNo(sequenceNo);
                schedule.setArrivalTime(LocalTime.of(10, 0));
                schedule.setDepartureTime(LocalTime.of(10, 5));
                schedule.setDistance(0);
                return schedule;
        }

        @Test
        void computesStationNameAndPalindromeFacts() {

                Station a = station("MAM", "Junction Road");
                Station b = station("XY", "Central Junction");
                Station c = station("SBC", "KSR Bengaluru City Junction");
                Station d = station("A1A", "Halt");

                when(stationRepository.findAll()).thenReturn(List.of(a, b, c, d));
                when(scheduleSnapshotService.getAllOrderedByTrainThenSequence()).thenReturn(List.of());

                FunStatsResponse response = service().getFunStats();

                // "KSR Bengaluru City Junction" (27 chars) is the longest;
                // "Halt" (4 chars) is the shortest.
                assertThat(response.longestStationName().stationCode()).isEqualTo("SBC");
                assertThat(response.shortestStationName().stationCode()).isEqualTo("A1A");
                assertThat(response.shortestStationName().length()).isEqualTo(4);

                // "Junction" appears in all 3 of A/B/C's names -> most common word.
                assertThat(response.mostCommonStationNameWord().word()).isEqualTo("junction");
                assertThat(response.mostCommonStationNameWord().count()).isEqualTo(3);

                // "MAM" and "A1A" both read the same forwards and backwards.
                assertThat(response.palindromeStationCodes()).containsExactlyInAnyOrder("MAM", "A1A");

                // First-letter coverage: J(unction Road)->1, C(entral)->1,
                // K(SR...)->1, H(alt)->1, everything else 0.
                assertThat(response.stationCountByFirstLetter().get("J")).isEqualTo(1);
                assertThat(response.stationCountByFirstLetter().get("C")).isEqualTo(1);
                assertThat(response.stationCountByFirstLetter().get("K")).isEqualTo(1);
                assertThat(response.stationCountByFirstLetter().get("H")).isEqualTo(1);
                assertThat(response.stationCountByFirstLetter().get("Z")).isEqualTo(0);
                assertThat(response.stationCountByFirstLetter()).hasSize(26);
        }

        @Test
        void findsTheTrainWithTheMostUniqueStations() {

                when(stationRepository.findAll()).thenReturn(List.of());

                Train small = train(1L, "1001");
                Train big = train(2L, "1002");

                Station a = station("A", "A Station");
                Station b = station("B", "B Station");
                Station c = station("C", "C Station");
                Station d = station("D", "D Station");

                List<TrainSchedule> smallRoute = List.of(
                                schedule(small, a, 1), schedule(small, b, 2));

                List<TrainSchedule> bigRoute = List.of(
                                schedule(big, a, 1), schedule(big, b, 2),
                                schedule(big, c, 3), schedule(big, d, 4));

                List<TrainSchedule> all = new java.util.ArrayList<>();
                all.addAll(smallRoute);
                all.addAll(bigRoute);

                when(scheduleSnapshotService.getAllOrderedByTrainThenSequence()).thenReturn(all);

                FunStatsResponse response = service().getFunStats();

                assertThat(response.trainWithMostUniqueStations().trainNumber()).isEqualTo("1002");
                assertThat(response.trainWithMostUniqueStations().uniqueStationCount()).isEqualTo(4);
        }
}
