package com.labs.train.train_db.model;

/**
 * Backs the "busiest station" entry on GET /api/stats - see
 * RouteDistanceProjection's javadoc for why this is a database-side
 * aggregation rather than an in-memory count.
 */
public record StationTrafficProjection(
                String stationCode,
                String stationName,
                long trainCount) {
}
