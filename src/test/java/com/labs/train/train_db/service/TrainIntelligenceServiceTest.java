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

                assertThat(response.expressnessScoreKmPerHalt()).isCloseTo(200.0, within(0.01));

                assertThat(response.longestNonStopSegmentKm()).isEqualTo(300);
                assertThat(response.longestNonStopSegmentFromStation()).isEqualTo("B");
                assertThat(response.longestNonStopSegmentToStation()).isEqualTo("C");

                assertThat(response.averageHaltMinutes()).isCloseTo(10.0, within(0.01));

                assertThat(response.routeComplexityScore()).isCloseTo(42.0, within(0.01));

                assertThat(response.journeyEfficiencyIndex()).isCloseTo(40.0 / 130.0 * 100.0, within(0.1));

                assertThat(response.nightTravelPercent()).isCloseTo(12.5, within(0.1));
                assertThat(response.dayTravelPercent()).isCloseTo(87.5, within(0.1));

                assertThat(response.trainUniquenessScore()).isCloseTo(100.0, within(0.1));
                assertThat(response.possiblySkippedStations()).isEmpty();

                assertThat(response.isCircularRoute()).isFalse();
        }

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
