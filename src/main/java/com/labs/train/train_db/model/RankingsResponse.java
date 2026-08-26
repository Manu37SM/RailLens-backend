package com.labs.train.train_db.model;

import java.util.List;

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
