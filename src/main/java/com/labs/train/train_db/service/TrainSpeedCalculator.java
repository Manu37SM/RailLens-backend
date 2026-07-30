package com.labs.train.train_db.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.labs.train.train_db.entity.TrainSchedule;
import com.labs.train.train_db.model.TrainSpeedProjection;

/**
 * Average speed per train, computed the same way TrainService#getTrainDetails
 * computes it for a single train (distance / hours, correctly handling a
 * journey that crosses midnight via JourneyDayCalculator) - just applied to
 * every train in one pass instead of one JPA call per train.
 *
 * Originally a private method on StatsService; extracted here (stateless
 * static utility, not a Spring bean - both callers already own their own
 * JourneyDayCalculator/schedule query) so AchievementsService's "top 100
 * fastest" / "super express rankings" can reuse the exact same speed
 * calculation instead of re-implementing it, per this codebase's "prefer
 * composition over duplication" convention.
 */
final class TrainSpeedCalculator {

        private TrainSpeedCalculator() {
        }

        /**
         * Trains with fewer than two stops, a missing first-departure/last-
         * arrival time, or a missing distance anywhere on their route are
         * skipped rather than guessed at.
         */
        static List<TrainSpeedProjection> computeAll(
                        List<TrainSchedule> allSchedulesOrderedByTrainThenSequence,
                        JourneyDayCalculator journeyDayCalculator) {

                Map<Long, List<TrainSchedule>> schedulesByTrainId = allSchedulesOrderedByTrainThenSequence.stream()
                                .collect(Collectors.groupingBy(schedule -> schedule.getTrain().getId()));

                List<TrainSpeedProjection> speeds = new ArrayList<>();

                for (List<TrainSchedule> schedules : schedulesByTrainId.values()) {

                        if (schedules.size() < 2) {
                                continue;
                        }

                        TrainSchedule first = schedules.get(0);
                        TrainSchedule last = schedules.get(schedules.size() - 1);

                        if (first.getDepartureTime() == null
                                        || last.getArrivalTime() == null
                                        || first.getDistance() == null
                                        || last.getDistance() == null) {
                                continue;
                        }

                        int distanceKm = last.getDistance() - first.getDistance();

                        if (distanceKm <= 0) {
                                continue;
                        }

                        List<Integer> journeyDays = journeyDayCalculator.computeJourneyDays(schedules);

                        long durationMinutes = journeyDayCalculator.minutesBetween(
                                        journeyDays.get(0), first.getDepartureTime(),
                                        journeyDays.get(journeyDays.size() - 1), last.getArrivalTime());

                        if (durationMinutes <= 0) {
                                continue;
                        }

                        double averageSpeedKmh = distanceKm / (durationMinutes / 60.0);

                        speeds.add(new TrainSpeedProjection(
                                        first.getTrain().getTrainNumber(),
                                        first.getTrain().getTrainName(),
                                        Math.round(averageSpeedKmh * 10.0) / 10.0,
                                        distanceKm,
                                        durationMinutes));
                }

                return speeds;
        }
}
