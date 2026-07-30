package com.labs.train.train_db.model;

/**
 * "Station Intelligence" scores for a single station (FEATURE.md) - the
 * per-station counterpart to TrainIntelligenceResponse. Connectivity/
 * centrality/rank fields come straight from the shared railway network
 * graph (RailwayNetworkService); traffic split and halt duration come from
 * the station's own schedule rows; average speed and the hourly histograms
 * are computed by StationIntelligenceService by walking each serving
 * train's full route relative to this station. See that service's javadoc
 * for which numbers are exact vs. a documented heuristic.
 */
public record StationIntelligenceResponse(

                String stationCode,
                String stationName,

                // 1 = most central station in its connected component, by
                // betweenness centrality; null if the station has no schedule
                // rows at all (nothing to rank).
                Integer networkRank,
                int totalStationsInNetwork,

                double connectivityScore,
                double betweennessCentrality,
                double closenessCentrality,
                int degree,

                int totalStops,
                int originCount,
                int destinationCount,
                int transitCount,
                double originPercent,
                double destinationPercent,
                double transitPercent,

                double averageHaltMinutes,
                Double averageTrainSpeedKmh,

                double stationImportanceScore,

                // Index 0-23, count of schedule rows with a departure/arrival
                // falling in that hour. Always length 24 (zero-filled), so a
                // frontend chart can index directly without gap-filling.
                int[] departureCountByHour,
                int[] arrivalCountByHour) {
}
