package com.labs.train.train_db.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.labs.train.train_db.entity.TrainSchedule;

@Component
public class JourneyDayCalculator {

        private static final LocalDate BASE_DATE = LocalDate.of(2000, 1, 1);

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

        public long minutesBetween(int dayA, LocalTime timeA, int dayB, LocalTime timeB) {

                LocalDateTime a = LocalDateTime.of(BASE_DATE.plusDays(dayA - 1), timeA);
                LocalDateTime b = LocalDateTime.of(BASE_DATE.plusDays(dayB - 1), timeB);

                return Duration.between(a, b).toMinutes();
        }
}
