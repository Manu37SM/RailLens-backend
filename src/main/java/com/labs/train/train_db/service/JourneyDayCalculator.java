package com.labs.train.train_db.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.labs.train.train_db.entity.TrainSchedule;

/**
 * Shared "which calendar day of the journey is this stop on" logic.
 *
 * Both {@link TrainService#getTrainDetails} (full route view) and
 * {@link JourneyService#search} (origin/destination duration) independently
 * implemented the same day-rollover-and-duration algorithm before this was
 * extracted (backend architecture review, "duplicated duration-calculation
 * logic" finding). Pulling it out here means a bug fix or timezone-handling
 * change only has to be made in one place.
 */
@Component
public class JourneyDayCalculator {

        // Arbitrary anchor date. Only the number of days elapsed between two
        // stops matters, not the actual calendar date.
        private static final LocalDate BASE_DATE = LocalDate.of(2000, 1, 1);

        /**
         * Returns the 1-indexed journey day for every stop in {@code route}
         * (which must already be ordered by sequence number). The day
         * increments whenever a stop's arrival time is earlier than the
         * previous stop's departure time, i.e. the schedule has rolled past
         * midnight.
         */
        public List<Integer> computeJourneyDays(List<TrainSchedule> route) {

                List<Integer> journeyDays = new ArrayList<>(route.size());

                int journeyDay = 1;
                LocalTime previousDeparture = route.isEmpty()
                                ? null
                                : route.getFirst().getDepartureTime();

                for (int i = 0; i < route.size(); i++) {

                        TrainSchedule schedule = route.get(i);

                        if (i > 0
                                        && previousDeparture != null
                                        && schedule.getArrivalTime() != null
                                        && schedule.getArrivalTime().isBefore(previousDeparture)) {

                                journeyDay++;
                        }

                        journeyDays.add(journeyDay);

                        if (schedule.getDepartureTime() != null) {
                                previousDeparture = schedule.getDepartureTime();
                        }
                }

                return journeyDays;
        }

        /**
         * Minutes elapsed between two (journeyDay, time-of-day) points,
         * anchored to the same arbitrary base date so multi-day journeys
         * (e.g. departure day 1, arrival day 2) compute correctly.
         */
        public long minutesBetween(int dayA, LocalTime timeA, int dayB, LocalTime timeB) {

                LocalDateTime a = LocalDateTime.of(BASE_DATE.plusDays(dayA - 1), timeA);
                LocalDateTime b = LocalDateTime.of(BASE_DATE.plusDays(dayB - 1), timeB);

                return Duration.between(a, b).toMinutes();
        }
}
