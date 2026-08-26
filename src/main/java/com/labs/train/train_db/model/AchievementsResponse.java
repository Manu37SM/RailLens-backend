package com.labs.train.train_db.model;

import java.util.List;

public record AchievementsResponse(

                List<RouteDistanceProjection> longestRoutes,
                List<TrainSpeedProjection> fastestTrains,

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

                        double averageTrainsPerHop) {
        }

        public record HiddenGemEntry(
                        String trainNumber,
                        String trainName,
                        int distanceKm,
                        double averageSpeedKmh) {
        }
}
