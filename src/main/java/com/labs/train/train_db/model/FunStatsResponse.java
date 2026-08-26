package com.labs.train.train_db.model;

import java.util.List;
import java.util.Map;

public record FunStatsResponse(

                StationNameEntry longestStationName,
                StationNameEntry shortestStationName,

                WordFrequency mostCommonStationNameWord,

                Map<String, Integer> stationCountByFirstLetter,

                TrainStopEntry trainWithMostUniqueStations,

                List<String> palindromeStationCodes) {

        public record StationNameEntry(String stationCode, String stationName, int length) {
        }

        public record WordFrequency(String word, int count) {
        }

        public record TrainStopEntry(String trainNumber, String trainName, int uniqueStationCount) {
        }
}
