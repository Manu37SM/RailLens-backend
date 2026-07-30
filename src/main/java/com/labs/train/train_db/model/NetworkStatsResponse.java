package com.labs.train.train_db.model;

import java.util.List;

/**
 * "Railway Network" graph metrics (FEATURE.md) - the network-wide summary
 * view over RailwayNetworkService's snapshot. Per-station detail (a single
 * station's own centrality/degree) lives on StationIntelligenceResponse
 * instead; this is the aggregate/leaderboard view.
 */
public record NetworkStatsResponse(

                int totalStations,
                int totalTrains,
                int totalEdges,

                // edges / all possible edges among the stations that have at
                // least one - a rough "how interconnected is this network"
                // figure, not a claim about physical track density.
                double routeDensity,

                int connectedComponentCount,
                int largestComponentSize,

                // Only meaningful within the largest connected component -
                // see RailwayNetworkSnapshot's javadoc.
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
