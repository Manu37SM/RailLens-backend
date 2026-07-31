package com.labs.train.train_db.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.labs.train.train_db.exception.ResourceNotFoundException;
import com.labs.train.train_db.model.TrainIntelligenceResponse;
import com.labs.train.train_db.repository.TrainScheduleRepository;
import com.labs.train.train_db.service.network.RailwayNetworkService;

@ExtendWith(MockitoExtension.class)
class TrainIntelligenceServiceTest {

        @Mock
        private TrainScheduleRepository trainScheduleRepository;

        @Mock
        private ScheduleSnapshotService scheduleSnapshotService;

        private final JourneyDayCalculator journeyDayCalculator = new JourneyDayCalculator();

        private TrainIntelligenceService service() {
                return new TrainIntelligenceService(
                                trainScheduleRepository,
                                journeyDayCalculator,
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
        void throwsWhenTheTrainHasNoScheduleRows() {

                when(trainScheduleRepository.findByTrain_TrainNumberOrderBySequenceNo("99999"))
                                .thenReturn(List.of());

                assertThatThrownBy(() -> service().getIntelligence("99999"))
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("99999");
        }

        /**
         * A-B-C-D, single train, all hand-computable:
         *  - A depart 08:00 (distance 0)
         *  - B arrive 09:00 / depart 09:10 (distance 100, 10 min halt)
         *  - C arrive 21:30 / depart 21:40 (distance 400, 10 min halt)
         *  - D arrive 23:00 (distance 600)
         *
         * totalDistance = 600, journeyMinutes = 08:00->23:00 = 900.
         */
        @Test
        void computesExactMetricsForAHandComputableRoute() {

                Train t = train(1L, "9001");
                Station a = station("A");
                Station b = station("B");
                Station c = station("C");
                Station d = station("D");

                List<TrainSchedule> route = List.of(
                                schedule(t, a, 1, null, LocalTime.of(8, 0), 0),
                                schedule(t, b, 2, LocalTime.of(9, 0), LocalTime.of(9, 10), 100),
                                schedule(t, c, 3, LocalTime.of(21, 30), LocalTime.of(21, 40), 400),
                                schedule(t, d, 4, LocalTime.of(23, 0), null, 600));

                when(trainScheduleRepository.findByTrain_TrainNumberOrderBySequenceNo("9001")).thenReturn(route);
                when(scheduleSnapshotService.getAllOrderedByTrainThenSequence()).thenReturn(route);

                TrainIntelligenceResponse response = service().getIntelligence("9001");

                assertThat(response.trainNumber()).isEqualTo("9001");
                assertThat(response.trainName()).isEqualTo("Train 9001");

                // expressness = totalDistance / halts = 600 / 3.
                assertThat(response.expressnessScoreKmPerHalt()).isCloseTo(200.0, within(0.01));

                // longest non-stop segment: A-B=100, B-C=300, C-D=200 -> B-C.
                assertThat(response.longestNonStopSegmentKm()).isEqualTo(300);
                assertThat(response.longestNonStopSegmentFromStation()).isEqualTo("B");
                assertThat(response.longestNonStopSegmentToStation()).isEqualTo("C");

                // average halt = (10 + 10) / 2.
                assertThat(response.averageHaltMinutes()).isCloseTo(10.0, within(0.01));

                // route complexity = 4*1.5 + 600/100 + 900/60*2 = 6 + 6 + 30 = 42.
                assertThat(response.routeComplexityScore()).isCloseTo(42.0, within(0.01));

                // efficiency = min(100, (600/(900/60)) / 130 * 100) = (40/130*100).
                assertThat(response.journeyEfficiencyIndex()).isCloseTo(40.0 / 130.0 * 100.0, within(0.1));

                // Night window is 21:00-06:00. Moving legs (departure->next
                // arrival, excluding halts): A->B 08:00-09:00 (0 night),
                // B->C 09:10-21:30 (30 min in [21:00,21:30)),
                // C->D 21:40-23:00 (all 80 min, fully inside [21:00,24:00)).
                // Total moving = 60+740+80 = 880; night = 0+30+80 = 110.
                // 110/880 = 12.5%.
                assertThat(response.nightTravelPercent()).isCloseTo(12.5, within(0.1));
                assertThat(response.dayTravelPercent()).isCloseTo(87.5, within(0.1));

                // Only train on every hop of its own route -> fully unique.
                assertThat(response.trainUniquenessScore()).isCloseTo(100.0, within(0.1));
                assertThat(response.possiblySkippedStations()).isEmpty();

                // Origin A != destination D.
                assertThat(response.isCircularRoute()).isFalse();
        }

        /**
         * 9001 runs A->C directly (one hop). 9003 duplicates that same A-C
         * hop (so it's shared, not unique). 9004 runs A->X->C, making X a
         * direct network neighbor of both A and C - exactly the condition
         * #possiblySkippedStations flags as "this train's direct hop
         * plausibly bypasses X."
         */
        @Test
        void computesUniquenessAndFlagsAPlausiblySkippedStation() {

                Train t9001 = train(1L, "9001");
                Train t9003 = train(3L, "9003");
                Train t9004 = train(4L, "9004");

                Station a = station("A");
                Station c = station("C");
                Station x = station("X");

                List<TrainSchedule> route9001 = List.of(
                                schedule(t9001, a, 1, null, LocalTime.of(8, 0), 0),
                                schedule(t9001, c, 2, LocalTime.of(10, 0), null, 500));

                List<TrainSchedule> route9003 = List.of(
                                schedule(t9003, a, 1, null, LocalTime.of(9, 0), 0),
                                schedule(t9003, c, 2, LocalTime.of(11, 0), null, 500));

                List<TrainSchedule> route9004 = List.of(
                                schedule(t9004, a, 1, null, LocalTime.of(7, 0), 0),
                                schedule(t9004, x, 2, LocalTime.of(8, 0), LocalTime.of(8, 5), 200),
                                schedule(t9004, c, 3, LocalTime.of(9, 0), null, 500));

                when(trainScheduleRepository.findByTrain_TrainNumberOrderBySequenceNo("9001"))
                                .thenReturn(route9001);

                List<TrainSchedule> allSchedules = new java.util.ArrayList<>();
                allSchedules.addAll(route9001);
                allSchedules.addAll(route9003);
                allSchedules.addAll(route9004);

                when(scheduleSnapshotService.getAllOrderedByTrainThenSequence())
                                .thenReturn(allSchedules);

                TrainIntelligenceResponse response = service().getIntelligence("9001");

                // A-C hop is shared by 9001 and 9003 -> 1/2 -> 50.
                assertThat(response.trainUniquenessScore()).isCloseTo(50.0, within(0.1));

                assertThat(response.possiblySkippedStations()).containsExactly("X");
        }

        @Test
        void flagsACircularRouteWhenOriginAndDestinationMatch() {

                Train t = train(5L, "9005");
                Station a = station("A");
                Station b = station("B");

                List<TrainSchedule> route = List.of(
                                schedule(t, a, 1, null, LocalTime.of(8, 0), 0),
                                schedule(t, b, 2, LocalTime.of(9, 0), LocalTime.of(9, 10), 50),
                                schedule(t, a, 3, LocalTime.of(10, 0), null, 100));

                when(trainScheduleRepository.findByTrain_TrainNumberOrderBySequenceNo("9005")).thenReturn(route);
                when(scheduleSnapshotService.getAllOrderedByTrainThenSequence()).thenReturn(route);

                TrainIntelligenceResponse response = service().getIntelligence("9005");

                assertThat(response.isCircularRoute()).isTrue();
        }
}
