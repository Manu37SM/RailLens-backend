package com.labs.train.train_db.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.labs.train.train_db.model.CreateTrainRequest;
import com.labs.train.train_db.model.RouteStopResponse;
import com.labs.train.train_db.model.TrainDetailsResponse;
import com.labs.train.train_db.model.TrainSearchResponse;
import com.labs.train.train_db.repository.TrainRepository;
import com.labs.train.train_db.repository.TrainScheduleRepository;

/**
 * Covers getTrainDetails(), the one TrainService method TrainServiceSearchTest
 * explicitly leaves out of scope.
 */
@ExtendWith(MockitoExtension.class)
class TrainServiceDetailsTest {

        @Mock
        private TrainRepository trainRepository;

        @Mock
        private TrainScheduleRepository trainScheduleRepository;

        private final JourneyDayCalculator journeyDayCalculator = new JourneyDayCalculator();

        private TrainService trainService() {
                return new TrainService(trainRepository, trainScheduleRepository, journeyDayCalculator);
        }

        private static Train train(String number, String name) {
                Train train = new Train();
                train.setTrainNumber(number);
                train.setTrainName(name);
                return train;
        }

        private static Station station(String code, String name) {
                Station station = new Station();
                station.setStationCode(code);
                station.setStationName(name);
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
        void createTrainSavesAndReturnsTheMappedResponse() {

                when(trainRepository.save(any(Train.class))).thenAnswer(invocation -> invocation.getArgument(0));

                TrainSearchResponse response = trainService().createTrain(
                                new CreateTrainRequest("12301", "Rajdhani Express"));

                assertThat(response.trainNumber()).isEqualTo("12301");
                assertThat(response.trainName()).isEqualTo("Rajdhani Express");
        }

        @Test
        void throwsWhenTheTrainHasNoScheduleRows() {

                when(trainScheduleRepository.findByTrain_TrainNumberOrderBySequenceNo("99999"))
                                .thenReturn(List.of());

                assertThatThrownBy(() -> trainService().getTrainDetails("99999"))
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("99999");
        }

        @Test
        void firstStopHasNoArrivalAndLastStopHasNoDeparture() {

                Train train = train("12301", "Rajdhani Express");
                Station a = station("NDLS", "New Delhi");
                Station b = station("AGC", "Agra Cantt");
                Station c = station("BPL", "Bhopal Jn");

                TrainSchedule first = schedule(train, a, 1, LocalTime.of(7, 0), LocalTime.of(8, 0), 0);
                TrainSchedule middle = schedule(train, b, 2, LocalTime.of(9, 30), LocalTime.of(9, 40), 200);
                TrainSchedule last = schedule(train, c, 3, LocalTime.of(14, 0), LocalTime.of(14, 30), 700);

                when(trainScheduleRepository.findByTrain_TrainNumberOrderBySequenceNo("12301"))
                                .thenReturn(List.of(first, middle, last));

                TrainDetailsResponse response = trainService().getTrainDetails("12301");

                List<RouteStopResponse> route = response.route();
                assertThat(route.get(0).arrivalTime()).isNull();
                assertThat(route.get(0).departureTime()).isEqualTo(LocalTime.of(8, 0));
                assertThat(route.get(0).origin()).isTrue();

                assertThat(route.get(2).arrivalTime()).isEqualTo(LocalTime.of(14, 0));
                assertThat(route.get(2).departureTime()).isNull();
                assertThat(route.get(2).destination()).isTrue();
        }

        @Test
        void computesHaltMinutesOnlyForIntermediateStops() {

                Train train = train("12301", "Rajdhani Express");
                Station a = station("NDLS", "New Delhi");
                Station b = station("AGC", "Agra Cantt");
                Station c = station("BPL", "Bhopal Jn");

                TrainSchedule first = schedule(train, a, 1, LocalTime.of(7, 0), LocalTime.of(8, 0), 0);
                // 10 minute halt at the middle stop.
                TrainSchedule middle = schedule(train, b, 2, LocalTime.of(9, 30), LocalTime.of(9, 40), 200);
                TrainSchedule last = schedule(train, c, 3, LocalTime.of(14, 0), LocalTime.of(14, 30), 700);

                when(trainScheduleRepository.findByTrain_TrainNumberOrderBySequenceNo("12301"))
                                .thenReturn(List.of(first, middle, last));

                TrainDetailsResponse response = trainService().getTrainDetails("12301");

                List<RouteStopResponse> route = response.route();
                assertThat(route.get(0).haltMinutes()).isZero(); // origin
                assertThat(route.get(1).haltMinutes()).isEqualTo(10); // intermediate
                assertThat(route.get(2).haltMinutes()).isZero(); // destination
        }

        @Test
        void computesDistanceFromPreviousStop() {

                Train train = train("12301", "Rajdhani Express");
                Station a = station("NDLS", "New Delhi");
                Station b = station("AGC", "Agra Cantt");
                Station c = station("BPL", "Bhopal Jn");

                TrainSchedule first = schedule(train, a, 1, null, LocalTime.of(8, 0), 0);
                TrainSchedule middle = schedule(train, b, 2, LocalTime.of(9, 30), LocalTime.of(9, 40), 200);
                TrainSchedule last = schedule(train, c, 3, LocalTime.of(14, 0), null, 700);

                when(trainScheduleRepository.findByTrain_TrainNumberOrderBySequenceNo("12301"))
                                .thenReturn(List.of(first, middle, last));

                TrainDetailsResponse response = trainService().getTrainDetails("12301");

                List<RouteStopResponse> route = response.route();
                assertThat(route.get(0).distanceFromPrevious()).isEqualTo(0);
                assertThat(route.get(1).distanceFromPrevious()).isEqualTo(200);
                assertThat(route.get(2).distanceFromPrevious()).isEqualTo(500);
        }

        @Test
        void distanceFromPreviousIsNullWhenEitherStopIsMissingDistance() {

                Train train = train("12301", "Rajdhani Express");
                Station a = station("NDLS", "New Delhi");
                Station b = station("AGC", "Agra Cantt");

                TrainSchedule first = schedule(train, a, 1, null, LocalTime.of(8, 0), 0);
                TrainSchedule second = schedule(train, b, 2, LocalTime.of(9, 30), null, null);

                when(trainScheduleRepository.findByTrain_TrainNumberOrderBySequenceNo("12301"))
                                .thenReturn(List.of(first, second));

                TrainDetailsResponse response = trainService().getTrainDetails("12301");

                assertThat(response.route().get(1).distanceFromPrevious()).isNull();
        }

        @Test
        void journeyDistanceFallsBackToZeroWhenLastStopHasNoDistance() {

                Train train = train("12301", "Rajdhani Express");
                Station a = station("NDLS", "New Delhi");
                Station b = station("AGC", "Agra Cantt");

                TrainSchedule first = schedule(train, a, 1, null, LocalTime.of(8, 0), 0);
                TrainSchedule last = schedule(train, b, 2, LocalTime.of(9, 30), null, null);

                when(trainScheduleRepository.findByTrain_TrainNumberOrderBySequenceNo("12301"))
                                .thenReturn(List.of(first, last));

                TrainDetailsResponse response = trainService().getTrainDetails("12301");

                assertThat(response.journeyDistance()).isZero();
        }

        @Test
        void computesJourneyMinutesAndAverageSpeedAcrossAMidnightRollover() {

                Train train = train("12301", "Rajdhani Express");
                Station a = station("NDLS", "New Delhi");
                Station b = station("HWH", "Howrah Jn");

                // Departs 23:00, arrives 03:00 next day = 4h = 240 minutes.
                // 480 km / 4h = 120 km/h.
                TrainSchedule first = schedule(train, a, 1, null, LocalTime.of(23, 0), 0);
                TrainSchedule last = schedule(train, b, 2, LocalTime.of(3, 0), null, 480);

                when(trainScheduleRepository.findByTrain_TrainNumberOrderBySequenceNo("12301"))
                                .thenReturn(List.of(first, last));

                TrainDetailsResponse response = trainService().getTrainDetails("12301");

                assertThat(response.journeyMinutes().longValue()).isEqualTo(240L);
                assertThat(response.averageSpeed()).isEqualTo(120.0);
        }

        @Test
        void averageSpeedIsZeroWhenJourneyMinutesIsZero() {

                Train train = train("12301", "Rajdhani Express");
                Station a = station("NDLS", "New Delhi");
                Station b = station("AGC", "Agra Cantt");

                // No departure time at the first stop -> journeyMinutes is 0.
                TrainSchedule first = schedule(train, a, 1, null, null, 0);
                TrainSchedule last = schedule(train, b, 2, LocalTime.of(9, 30), null, 200);

                when(trainScheduleRepository.findByTrain_TrainNumberOrderBySequenceNo("12301"))
                                .thenReturn(List.of(first, last));

                TrainDetailsResponse response = trainService().getTrainDetails("12301");

                assertThat(response.journeyMinutes().longValue()).isZero();
                assertThat(response.averageSpeed().doubleValue()).isZero();
        }

        @Test
        void sourceAndDestinationStationNamesComeFromTheFirstAndLastStop() {

                Train train = train("12301", "Rajdhani Express");
                Station a = station("NDLS", "New Delhi");
                Station b = station("HWH", "Howrah Jn");

                TrainSchedule first = schedule(train, a, 1, null, LocalTime.of(8, 0), 0);
                TrainSchedule last = schedule(train, b, 2, LocalTime.of(20, 0), null, 1400);

                when(trainScheduleRepository.findByTrain_TrainNumberOrderBySequenceNo("12301"))
                                .thenReturn(List.of(first, last));

                TrainDetailsResponse response = trainService().getTrainDetails("12301");

                assertThat(response.sourceStationName()).isEqualTo("New Delhi");
                assertThat(response.destinationStationName()).isEqualTo("Howrah Jn");
                assertThat(response.totalStops()).isEqualTo(2);
        }
}
