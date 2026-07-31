package com.labs.train.train_db.service.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
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
import com.labs.train.train_db.model.NetworkStatsResponse;
import com.labs.train.train_db.service.ScheduleSnapshotService;

@ExtendWith(MockitoExtension.class)
class RailwayNetworkServiceTest {

        @Mock
        private ScheduleSnapshotService scheduleSnapshotService;

        private RailwayNetworkService service() {
                return new RailwayNetworkService(scheduleSnapshotService);
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
                        Train train, Station station, int sequenceNo, LocalTime arrival, LocalTime departure) {

                TrainSchedule schedule = new TrainSchedule();
                schedule.setTrain(train);
                schedule.setStation(station);
                schedule.setSequenceNo(sequenceNo);
                schedule.setArrivalTime(arrival);
                schedule.setDepartureTime(departure);
                schedule.setDistance(sequenceNo * 100);
                return schedule;
        }

        /**
         * A-B-C-D-E as a single straight-line path (one train, 5 stops) has
         * hand-computable graph metrics for every measure this service
         * produces - see the method-local comments for the manual
         * derivation of each expected value.
         */
        @Test
        void computesKnownMetricsForAFiveStationPath() {

                Train t = train(1L, "1001");

                Station a = station("A");
                Station b = station("B");
                Station c = station("C");
                Station d = station("D");
                Station e = station("E");

                List<TrainSchedule> route = List.of(
                                schedule(t, a, 1, null, LocalTime.of(6, 0)),
                                schedule(t, b, 2, LocalTime.of(6, 30), LocalTime.of(6, 35)),
                                schedule(t, c, 3, LocalTime.of(7, 0), LocalTime.of(7, 10)),
                                schedule(t, d, 4, LocalTime.of(7, 30), LocalTime.of(7, 35)),
                                schedule(t, e, 5, LocalTime.of(8, 0), null));

                when(scheduleSnapshotService.getAllOrderedByTrainThenSequence()).thenReturn(route);

                RailwayNetworkSnapshot snapshot = service().buildSnapshot();

                assertThat(snapshot.totalTrains).isEqualTo(1);
                assertThat(snapshot.totalStations()).isEqualTo(5);
                assertThat(snapshot.connectedComponents).hasSize(1);
                assertThat(snapshot.connectedComponents.get(0)).hasSize(5);

                // Diameter of a 5-node path = 4 (A to E).
                assertThat(snapshot.networkDiameter).isEqualTo(4);

                // Degree: endpoints have 1 neighbor, everyone else has 2.
                assertThat(snapshot.station("A").degree()).isEqualTo(1);
                assertThat(snapshot.station("C").degree()).isEqualTo(2);

                // Closeness(A) = (n-1) / sum_of_distances = 4 / (1+2+3+4) = 0.4.
                assertThat(snapshot.station("A").closenessCentrality).isCloseTo(0.4, within(0.0001));

                // Closeness(C) = 4 / (2+1+0+1+2 -> sum excluding self = 6) = 0.6667.
                assertThat(snapshot.station("C").closenessCentrality).isCloseTo(4.0 / 6.0, within(0.0001));

                // Betweenness of a path-graph node at 0-indexed position i (of n)
                // is i * (n-1-i): A=0*4=0, B=1*3=3, C=2*2=4, D=3*1=3, E=4*0=0.
                assertThat(snapshot.station("A").betweennessCentrality).isCloseTo(0.0, within(0.0001));
                assertThat(snapshot.station("B").betweennessCentrality).isCloseTo(3.0, within(0.0001));
                assertThat(snapshot.station("C").betweennessCentrality).isCloseTo(4.0, within(0.0001));
                assertThat(snapshot.station("D").betweennessCentrality).isCloseTo(3.0, within(0.0001));
                assertThat(snapshot.station("E").betweennessCentrality).isCloseTo(0.0, within(0.0001));
        }

        @Test
        void tracksOriginDestinationAndTransitCountsSeparately() {

                Train t = train(1L, "1001");
                Station a = station("A");
                Station b = station("B");
                Station c = station("C");

                List<TrainSchedule> route = List.of(
                                schedule(t, a, 1, null, LocalTime.of(6, 0)),
                                schedule(t, b, 2, LocalTime.of(6, 30), LocalTime.of(6, 40)),
                                schedule(t, c, 3, LocalTime.of(7, 0), null));

                when(scheduleSnapshotService.getAllOrderedByTrainThenSequence()).thenReturn(route);

                RailwayNetworkSnapshot snapshot = service().buildSnapshot();

                assertThat(snapshot.station("A").originCount).isEqualTo(1);
                assertThat(snapshot.station("A").destinationCount).isZero();
                assertThat(snapshot.station("A").transitCount).isZero();

                assertThat(snapshot.station("B").transitCount).isEqualTo(1);
                assertThat(snapshot.station("B").originCount).isZero();
                assertThat(snapshot.station("B").destinationCount).isZero();
                // 10 minute halt at the one transit stop.
                assertThat(snapshot.station("B").averageHaltMinutes()).isEqualTo(10.0);

                assertThat(snapshot.station("C").destinationCount).isEqualTo(1);
        }

        @Test
        void countsHowManyDistinctTrainsUseEachEdge() {

                Train first = train(1L, "1001");
                Train second = train(2L, "1002");
                Station a = station("A");
                Station b = station("B");

                List<TrainSchedule> route = List.of(
                                schedule(first, a, 1, null, LocalTime.of(6, 0)),
                                schedule(first, b, 2, LocalTime.of(6, 30), null),
                                schedule(second, a, 1, null, LocalTime.of(9, 0)),
                                schedule(second, b, 2, LocalTime.of(9, 30), null));

                when(scheduleSnapshotService.getAllOrderedByTrainThenSequence()).thenReturn(route);

                RailwayNetworkSnapshot snapshot = service().buildSnapshot();

                assertThat(snapshot.station("A").neighborTrainCounts.get("B")).isEqualTo(2);
                assertThat(snapshot.station("B").neighborTrainCounts.get("A")).isEqualTo(2);
        }

        @Test
        void getNetworkStatsSummarizesTheFiveStationPath() {

                Train t = train(1L, "1001");

                Station a = station("A");
                Station b = station("B");
                Station c = station("C");
                Station d = station("D");
                Station e = station("E");

                List<TrainSchedule> route = List.of(
                                schedule(t, a, 1, null, LocalTime.of(6, 0)),
                                schedule(t, b, 2, LocalTime.of(6, 30), LocalTime.of(6, 35)),
                                schedule(t, c, 3, LocalTime.of(7, 0), LocalTime.of(7, 10)),
                                schedule(t, d, 4, LocalTime.of(7, 30), LocalTime.of(7, 35)),
                                schedule(t, e, 5, LocalTime.of(8, 0), null));

                when(scheduleSnapshotService.getAllOrderedByTrainThenSequence()).thenReturn(route);

                NetworkStatsResponse stats = service().getNetworkStats();

                assertThat(stats.totalStations()).isEqualTo(5);
                assertThat(stats.totalTrains()).isEqualTo(1);
                assertThat(stats.totalEdges()).isEqualTo(4);
                assertThat(stats.routeDensity()).isCloseTo(0.4, within(0.001)); // 4 / (5*4/2).
                assertThat(stats.connectedComponentCount()).isEqualTo(1);
                assertThat(stats.largestComponentSize()).isEqualTo(5);
                assertThat(stats.networkDiameter()).isEqualTo(4);

                assertThat(stats.mostCentralStations()).hasSize(5);
                assertThat(stats.mostCentralStations().get(0).stationCode()).isEqualTo("C");
                assertThat(stats.mostCentralStations().get(0).betweennessCentrality()).isCloseTo(4.0, within(0.01));
        }

        @Test
        void findsMultipleDisconnectedComponentsAndComputesDiameterOnlyForTheLargest() {

                Train small = train(1L, "1001");
                Train large = train(2L, "1002");

                Station x = station("X");
                Station y = station("Y");

                Station p = station("P");
                Station q = station("Q");
                Station r = station("R");

                List<TrainSchedule> route = List.of(
                                // Component 1: X-Y (2 stations).
                                schedule(small, x, 1, null, LocalTime.of(6, 0)),
                                schedule(small, y, 2, LocalTime.of(6, 30), null),
                                // Component 2: P-Q-R (3 stations, disjoint from the above).
                                schedule(large, p, 1, null, LocalTime.of(8, 0)),
                                schedule(large, q, 2, LocalTime.of(8, 30), LocalTime.of(8, 35)),
                                schedule(large, r, 3, LocalTime.of(9, 0), null));

                when(scheduleSnapshotService.getAllOrderedByTrainThenSequence()).thenReturn(route);

                RailwayNetworkSnapshot snapshot = service().buildSnapshot();

                assertThat(snapshot.connectedComponents).hasSize(2);
                assertThat(snapshot.connectedComponents.get(snapshot.largestComponentIndex)).hasSize(3);

                // Diameter must come from the 3-node component (P-Q-R, diameter 2),
                // not the 2-node one.
                assertThat(snapshot.networkDiameter).isEqualTo(2);
        }
}
