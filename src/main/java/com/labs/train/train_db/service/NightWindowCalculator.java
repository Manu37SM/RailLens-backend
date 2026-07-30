package com.labs.train.train_db.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * How many minutes of a [start, end) time range fall inside "night" hours
 * (21:00-06:00, the conventional window IRCTC and most Indian journey
 * planners use for "night travel" warnings). Extracted from
 * TrainIntelligenceService (where this was originally written for its
 * whole-route night/day split) so JourneyService's per-segment night/day
 * analysis can reuse the exact same definition instead of re-implementing
 * it - see both callers for how the resulting minute count is turned into
 * a percentage.
 */
final class NightWindowCalculator {

        private NightWindowCalculator() {
        }

        static long nightMinutesInRange(LocalDateTime start, LocalDateTime end) {

                long minutes = 0;

                for (LocalDate day = start.toLocalDate(); !day.isAfter(end.toLocalDate()); day = day.plusDays(1)) {

                        LocalDateTime midnightToSixStart = LocalDateTime.of(day, LocalTime.MIDNIGHT);
                        LocalDateTime midnightToSixEnd = LocalDateTime.of(day, LocalTime.of(6, 0));

                        LocalDateTime nightStart = LocalDateTime.of(day, LocalTime.of(21, 0));
                        LocalDateTime nightEnd = LocalDateTime.of(day.plusDays(1), LocalTime.MIDNIGHT);

                        minutes += overlapMinutes(start, end, midnightToSixStart, midnightToSixEnd);
                        minutes += overlapMinutes(start, end, nightStart, nightEnd);
                }

                return minutes;
        }

        private static long overlapMinutes(
                        LocalDateTime aStart, LocalDateTime aEnd, LocalDateTime bStart, LocalDateTime bEnd) {

                LocalDateTime overlapStart = aStart.isAfter(bStart) ? aStart : bStart;
                LocalDateTime overlapEnd = aEnd.isBefore(bEnd) ? aEnd : bEnd;

                if (!overlapEnd.isAfter(overlapStart)) {
                        return 0;
                }

                return java.time.Duration.between(overlapStart, overlapEnd).toMinutes();
        }
}
