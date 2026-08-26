package com.labs.train.train_db.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.labs.train.train_db.entity.TrainSchedule;

class JourneyDayCalculatorTest {

        private final JourneyDayCalculator calculator = new JourneyDayCalculator();

        private static TrainSchedule stop(LocalTime arrival, LocalTime departure) {
                TrainSchedule schedule = new TrainSchedule();
                schedule.setArrivalTime(arrival);
                schedule.setDepartureTime(departure);
                return schedule;
        }

        @Test
        void emptyRouteReturnsEmptyList() {
                assertThat(calculator.computeJourneyDays(List.of())).isEmpty();
        }

        @Test
        void singleStopStaysOnDayOne() {
                List<TrainSchedule> route = List.of(
                                stop(null, LocalTime.of(8, 0)));

                assertThat(calculator.computeJourneyDays(route)).containsExactly(1);
        }

        @Test
        void sameDayJourneyDoesNotRollOver() {
                List<TrainSchedule> route = List.of(
                                stop(null, LocalTime.of(8, 0)),
                                stop(LocalTime.of(10, 0), LocalTime.of(10, 5)),
                                stop(LocalTime.of(14, 0), null));

                assertThat(calculator.computeJourneyDays(route)).containsExactly(1, 1, 1);
        }

        @Test
        void overnightJourneyRollsOverToDayTwo() {
                List<TrainSchedule> route = List.of(
                                stop(null, LocalTime.of(23, 0)),
                                stop(LocalTime.of(1, 0), LocalTime.of(1, 5)),
                                stop(LocalTime.of(6, 0), null));

                assertThat(calculator.computeJourneyDays(route)).containsExactly(1, 2, 2);
        }

        @Test
        void multipleRolloversAccumulate() {
                List<TrainSchedule> route = List.of(
                                stop(null, LocalTime.of(22, 0)),
                                stop(LocalTime.of(2, 0), LocalTime.of(23, 30)),
                                stop(LocalTime.of(1, 0), null)
                );

                assertThat(calculator.computeJourneyDays(route)).containsExactly(1, 2, 3);
        }

        @Test
        void minutesBetweenSameDayIsSimpleDifference() {
                long minutes = calculator.minutesBetween(
                                1, LocalTime.of(8, 0),
                                1, LocalTime.of(10, 30));

                assertThat(minutes).isEqualTo(150);
        }

        @Test
        void minutesBetweenAcrossDaysAccountsForElapsedDays() {
                long minutes = calculator.minutesBetween(
                                1, LocalTime.of(23, 0),
                                2, LocalTime.of(1, 0));

                assertThat(minutes).isEqualTo(120);
        }
}
