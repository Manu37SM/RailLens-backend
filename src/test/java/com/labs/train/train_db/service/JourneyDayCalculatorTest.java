package com.labs.train.train_db.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.labs.train.train_db.entity.TrainSchedule;

/**
 * Plain unit tests for the shared day-rollover / duration logic extracted
 * during the N+1-query and duplicated-duration-logic cleanup (see
 * TrainService and JourneyService, which both delegate here). No Spring
 * context needed - this class has no dependencies of its own.
 */
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
                // Origin 08:00 -> intermediate 10:00/10:05 -> destination 14:00,
                // all strictly increasing, so every stop should be day 1.
                List<TrainSchedule> route = List.of(
                                stop(null, LocalTime.of(8, 0)),
                                stop(LocalTime.of(10, 0), LocalTime.of(10, 5)),
                                stop(LocalTime.of(14, 0), null));

                assertThat(calculator.computeJourneyDays(route)).containsExactly(1, 1, 1);
        }

        @Test
        void overnightJourneyRollsOverToDayTwo() {
                // Departs 23:00 day 1, next stop arrives 01:00 - earlier than the
                // previous departure, so it must have rolled past midnight onto
                // day 2. Everything after that stays on day 2 since it keeps
                // increasing again from there.
                List<TrainSchedule> route = List.of(
                                stop(null, LocalTime.of(23, 0)),
                                stop(LocalTime.of(1, 0), LocalTime.of(1, 5)),
                                stop(LocalTime.of(6, 0), null));

                assertThat(calculator.computeJourneyDays(route)).containsExactly(1, 2, 2);
        }

        @Test
        void multipleRolloversAccumulate() {
                // Two separate midnight crossings should land on day 3.
                List<TrainSchedule> route = List.of(
                                stop(null, LocalTime.of(22, 0)), // day 1
                                stop(LocalTime.of(2, 0), LocalTime.of(23, 30)), // day 2 (rolled once)
                                stop(LocalTime.of(1, 0), null) // day 3 (rolled again)
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
                // Day 1 23:00 -> day 2 01:00 is 2 hours, not a negative duration.
                long minutes = calculator.minutesBetween(
                                1, LocalTime.of(23, 0),
                                2, LocalTime.of(1, 0));

                assertThat(minutes).isEqualTo(120);
        }
}
