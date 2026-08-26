package com.labs.train.train_db.model;

import java.util.List;

public record NetworkStatsResponse(

                int totalStations,
                int totalTrains,
                int totalEdges,

                double routeDensity,

                int connectedComponentCount,
                int largestComponentSize,

                int networkDiameter,

                List<CentralStation> mostCentralStations) {

        public record CentralStation(
                        String stationCode,
                        String stationName,
                        double betweennessCentrality,
                        double closenessCentrality,
                        int degree) {
        }
}
