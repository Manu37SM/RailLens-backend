package com.labs.train.train_db.model;

/**
 * Backs the "fastest trains" / "slowest trains" entries on GET
 * /api/v1/stats. Unlike RouteDistanceProjection/StationTrafficProjection,
 * this is NOT a database-side aggregation - average speed depends on
 * correctly handling a journey that crosses midnight (see
 * JourneyDayCalculator), which plain SQL/JPQL arithmetic on a TIME column
 * can't express. StatsService computes this in Java from the full
 * schedule table instead, mirroring the exact per-train math
 * TrainService#getTrainDetails already uses for a single train's average
 * speed, just applied across every train at once.
 */
public record TrainSpeedProjection(
                String trainNumber,
                String trainName,
                double averageSpeedKmh,
                int distanceKm,
                long durationMinutes) {
}
