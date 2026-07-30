package com.labs.train.train_db.model;

import java.util.List;

/**
 * "Rankings" leaderboards (FEATURE.md) - extends StatsResponse's
 * fastest/slowest-train and busiest-station lists with halt-count and halt-
 * duration leaderboards, plus the "most popular origin" and "most
 * connected" station lists (both sourced from the shared railway network
 * graph - see RankingsService). Kept as its own endpoint/response rather
 * than folded into StatsResponse since the connectivity-based lists here
 * additionally depend on RailwayNetworkService's graph snapshot, the same
 * "heavier, so keep it separate" reasoning as TrainIntelligenceResponse.
 */
public record RankingsResponse(

                List<HaltCountEntry> mostHaltsTrains,
                List<HaltCountEntry> fewestHaltsTrains,

                List<HaltDurationEntry> longestHalts,
                List<HaltDurationEntry> shortestHalts,

                List<StationCountEntry> mostPopularOriginStations,
                List<StationCountEntry> mostConnectedStations) {

        public record HaltCountEntry(
                        String trainNumber,
                        String trainName,
                        int haltCount) {
        }

        public record HaltDurationEntry(
                        String trainNumber,
                        String trainName,
                        String stationCode,
                        String stationName,
                        long minutes) {
        }

        public record StationCountEntry(
                        String stationCode,
                        String stationName,
                        int count) {
        }
}
