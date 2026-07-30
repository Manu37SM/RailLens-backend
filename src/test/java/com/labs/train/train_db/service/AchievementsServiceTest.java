package com.labs.train.train_db.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import com.labs.train.train_db.model.AchievementsResponse;
import com.labs.train.train_db.model.RouteDistanceProjection;
import com.labs.train.train_db.repository.TrainScheduleRepository;
import com.labs.train.train_db.service.network.RailwayNetworkService;

@ExtendWith(MockitoExtension.class)
class AchievementsServiceTest {

        @Mock
        private TrainScheduleRepository trainScheduleRepository;

        private final JourneyDayCalculator journeyDayCalculator = new JourneyDayCalculator();

        private AchievementsService service() {
                return new AchievementsService(
                                trainScheduleRepository, journeyDayCalculator,
                                new RailwayNetworkService(trainScheduleRepository));
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
         * Train 9001: A-B-C-D-E, unique to itself on every hop (super express
         * candidate + rare route candidate: distance 600km over 3 halts =
         * 200 km/halt; average trains-per-hop = 1.0, the rarest possible).
         * Train 9002: A-B duplicates 9001's A-B hop, so that hop has 2 trains
         * on it - 9002 is a plain direct (0-halt) train and is excluded from
         * super express rankings (no halts to average over).
         */
        @Test
        void computesSuperExpressAndRareRouteAwards() {

                Train t9001 = train(1L, "9001");
                Train t9002 = train(2L, "9002");

                Station a = station("A");
                Station b = station("B");
                Station c = station("C");
                Station d = station("D");
                Station e = station("E");

                List<TrainSchedule> route9001 = List.of(
                                schedule(t9001, a, 1, null, LocalTime.of(8, 0), 0),
                                schedule(t9001, b, 2, LocalTime.of(9, 0), LocalTime.of(9, 10), 100),
                                schedule(t9001, c, 3, LocalTime.of(10, 0), LocalTime.of(10, 10), 300),
                                schedule(t9001, d, 4, LocalTime.of(11, 0), LocalTime.of(11, 10), 450),
                                schedule(t9001, e, 5, LocalTime.of(12, 0), null, 600));

                List<TrainSchedule> route9002 = List.of(
                                schedule(t9002, a, 1, null, LocalTime.of(7, 0), 0),
                                schedule(t9002, b, 2, LocalTime.of(7, 30), null, 100));

                List<TrainSchedule> all = new ArrayList<>();
                all.addAll(route9001);
                all.addAll(route9002);

                when(trainScheduleRepository.findAllByOrderByTrain_IdAscSequenceNoAsc()).thenReturn(all);
                when(trainScheduleRepository.findRouteDistancesDescending(any()))
                                .thenReturn(List.of(new RouteDistanceProjection("9001", "Train 9001", 600)));

                AchievementsResponse response = service().getAchievements();

                assertThat(response.superExpressRankings()).hasSize(1);
                assertThat(response.superExpressRankings().get(0).trainNumber()).isEqualTo("9001");
                // 600km / 3 halts = 200 km/halt.
                assertThat(response.superExpressRankings().get(0).kmPerHalt()).isEqualTo(200.0);

                // 9001's hops: A-B shared with 9002 (2 trains), B-C/C-D/D-E
                // exclusive to 9001 (1 train each) -> average = (2+1+1+1)/4 = 1.25.
                assertThat(response.rareRoutes())
                                .anySatisfy(entry -> {
                                        assertThat(entry.trainNumber()).isEqualTo("9001");
                                        assertThat(entry.averageTrainsPerHop()).isEqualTo(1.25);
                                });

                // 9002 is a direct (0-halt) train - not in mega/super-express.
                assertThat(response.megaRoutes()).isEmpty();

                assertThat(response.longestRoutes()).hasSize(1);
                assertThat(response.longestRoutes().get(0).trainNumber()).isEqualTo("9001");
        }
}
