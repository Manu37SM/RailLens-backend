package com.labs.train.train_db.model;

/**
 * Backs the "longest/shortest route" entries on GET /api/stats. Built via
 * a JPQL constructor expression (see TrainScheduleRepository) rather than
 * loading every schedule row into memory and computing this in Java - the
 * aggregation (MAX(distance) GROUP BY train) happens in the database,
 * which is the only way this stays fast once the dataset covers the full
 * IR timetable (~8,000+ trains, hundreds of thousands of schedule rows).
 */
public record RouteDistanceProjection(
                String trainNumber,
                String trainName,
                Integer distanceKm) {
}
