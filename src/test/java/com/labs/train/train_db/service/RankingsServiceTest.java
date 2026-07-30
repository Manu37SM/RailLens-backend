package com.labs.train.train_db.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.labs.train.train_db.entity.Station;
import com.labs.train.train_db.entity.Train;
import com.labs.train.train_db.entity.TrainSchedule;
import com.labs.train.train_db.model.RankingsResponse;
import com.labs.train.train_db.repository.TrainScheduleRepository;
import com.labs.train.train_db.service.network.RailwayNetworkService;

@ExtendWith(MockitoExtension.class)
class RankingsServiceTest {

        @Mock
        private TrainScheduleRepository trainScheduleRepository;

        private RankingsService service() {
                return new RankingsService(trainScheduleRepository, new RailwayNetworkService(trainScheduleRepository));
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

        /**
         * Train 9001: A-B-C-D (2 halts: B 5min, C 45min).
         * Train 9002: X-B-Y (1 halt: B 20min) - B is also the busiest/most
         * connected station, appearing on both trains and linking to
         * A, C, X, Y (degree 4). A is the only origin (9001's), X is the
         * other origin (9002's) - both origins, so "most popular origin"
         * is a tie broken by encounter order, not asserted strictly.
         */
        @Test
        void ranksHaltsAndStationsCorrectly() {

                Train t9001 = train(1L, "9001");
                Train t9002 = train(2L, "9002");

                Station a = station("A");
                Station b = station("B");
                Station c = station("C");
                Station d = station("D");
                Station x = station("X");
                Station y = station("Y");

                List<TrainSchedule> route9001 = List.of(
                                schedule(t9001, a, 1, null, LocalTime.of(8, 0), 0),
                                schedule(t9001, b, 2, LocalTime.of(9, 0), LocalTime.of(9, 5), 100),
                                schedule(t9001, c, 3, LocalTime.of(10, 0), LocalTime.of(10, 45), 200),
                                schedule(t9001, d, 4, LocalTime.of(11, 0), null, 300));

                List<TrainSchedule> route9002 = List.of(
                                schedule(t9002, x, 1, null, LocalTime.of(7, 0), 0),
                                schedule(t9002, b, 2, LocalTime.of(7, 30), LocalTime.of(7, 50), 50),
                                schedule(t9002, y, 3, LocalTime.of(8, 30), null, 100));

                List<TrainSchedule> allSchedules = new ArrayList<>();
                allSchedules.addAll(route9001);
                allSchedules.addAll(route9002);

                when(trainScheduleRepository.findAllByOrderByTrain_IdAscSequenceNoAsc()).thenReturn(allSchedules);

                RankingsResponse response = service().getRankings();

                // 9001 has 2 halts (B, C), 9002 has 1 (B).
                assertThat(response.mostHaltsTrains().get(0).trainNumber()).isEqualTo("9001");
                assertThat(response.mostHaltsTrains().get(0).haltCount()).isEqualTo(2);

                assertThat(response.fewestHaltsTrains().get(0).trainNumber()).isEqualTo("9002");
                assertThat(response.fewestHaltsTrains().get(0).haltCount()).isEqualTo(1);

                // Longest halt: 9001 at C, 45 minutes.
                assertThat(response.longestHalts().get(0).trainNumber()).isEqualTo("9001");
                assertThat(response.longestHalts().get(0).stationCode()).isEqualTo("C");
                assertThat(response.longestHalts().get(0).minutes()).isEqualTo(45);

                // Shortest halt: 9001 at B, 5 minutes.
                assertThat(response.shortestHalts().get(0).stationCode()).isEqualTo("B");
                assertThat(response.shortestHalts().get(0).minutes()).isEqualTo(5);

                // B is the only station touched by both trains -> degree 4
                // (A, C, X, Y), the highest of any station.
                assertThat(response.mostConnectedStations().get(0).stationCode()).isEqualTo("B");
                assertThat(response.mostConnectedStations().get(0).count()).isEqualTo(4);

                // A and X are each an origin once - B is never an origin, so it
                // must not appear at the top of this particular list.
                assertThat(response.mostPopularOriginStations().get(0).count()).isEqualTo(1);
                assertThat(response.mostPopularOriginStations())
                                .extracting((RankingsResponse.StationCountEntry entry) -> entry.stationCode())
                                .doesNotContain("B");
        }
}
