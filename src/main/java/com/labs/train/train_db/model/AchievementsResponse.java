package com.labs.train.train_db.model;

import java.util.List;

/**
 * "Railway Achievements" (FEATURE.md) - extended top-100 longest/fastest
 * lists (StatsResponse only shows a top-10 preview of the same data), plus
 * a few derived "award" categories: mega routes (>3000km), super express
 * rankings (best distance-per-halt), rare routes (trains whose hops are
 * mostly exclusive to them), and hidden gems (fast, long trains that don't
 * otherwise crack the top-10 lists). See AchievementsService for how each
 * is derived.
 */
public record AchievementsResponse(

                List<RouteDistanceProjection> longestRoutes,
                List<TrainSpeedProjection> fastestTrains,

                // distanceKm > 3000 - a fixed threshold (documented judgment
                // call, not derived from the data), sorted longest-first.
                List<RouteDistanceProjection> megaRoutes,

                List<SuperExpressEntry> superExpressRankings,
                List<RareRouteEntry> rareRoutes,
                List<HiddenGemEntry> hiddenGems) {

        public record SuperExpressEntry(
                        String trainNumber,
                        String trainName,
                        double kmPerHalt) {
        }

        public record RareRouteEntry(
                        String trainNumber,
                        String trainName,

                        // Average, across every hop on this train's route, of how
                        // many distinct trains (including this one) make that exact
                        // hop - close to 1.0 means almost the whole route is
                        // exclusive to this train.
                        double averageTrainsPerHop) {
        }

        public record HiddenGemEntry(
                        String trainNumber,
                        String trainName,
                        int distanceKm,
                        double averageSpeedKmh) {
        }
}
