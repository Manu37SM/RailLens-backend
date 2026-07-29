package com.labs.train.train_db.model;

import java.util.List;

/**
 * Backs the public GET /api/v1/stats endpoint and the frontend's /stats
 * page (PROMPT.md's "Statistics" target feature). Nested route/station
 * records are nullable - an empty database (fresh install before the
 * first CSV import) has no trains to report a longest/shortest route or
 * busiest station for, and the frontend needs to distinguish that from a
 * real zero-length answer. The list fields default to an empty list
 * (never null) in that same empty-database case, so the frontend can
 * render "no data yet" the same way for all of them without a separate
 * null-check per field.
 */
public record StatsResponse(
                long totalTrains,
                long totalStations,

                RouteDistanceProjection longestRoute,
                RouteDistanceProjection shortestRoute,

                StationTrafficProjection busiestStation,

                // Ranked lists, most-first - see StatsService for how each is
                // computed (busiestStations is a DB-side aggregation like
                // busiestStation above, just not truncated to one row;
                // fastestTrains/slowestTrains are computed in Java - see
                // TrainSpeedProjection's javadoc for why).
                List<StationTrafficProjection> busiestStations,
                List<TrainSpeedProjection> fastestTrains,
                List<TrainSpeedProjection> slowestTrains) {
}
