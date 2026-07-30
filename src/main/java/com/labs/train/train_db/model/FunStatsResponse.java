package com.labs.train.train_db.model;

import java.util.List;
import java.util.Map;

/**
 * "Fun Statistics" (FEATURE.md) - lightweight, purely-for-interest facts
 * about the dataset's station names and train routes, distinct from the
 * "serious" leaderboards in StatsResponse/RankingsResponse. See
 * FunStatsService for how each is derived.
 */
public record FunStatsResponse(

                StationNameEntry longestStationName,
                StationNameEntry shortestStationName,

                // The single most frequent word (case-insensitive) across every
                // station name, e.g. "Road" or "Junction" - null if the station
                // table is empty.
                WordFrequency mostCommonStationNameWord,

                // Every letter A-Z mapped to how many station names start with
                // it (0 if none do) - answers "do we have a station for every
                // letter of the alphabet."
                Map<String, Integer> stationCountByFirstLetter,

                // The train whose route visits the most distinct station codes -
                // usually just its stop count, but meaningfully different for a
                // circular route that revisits a station (see
                // TrainIntelligenceResponse#isCircularRoute).
                TrainStopEntry trainWithMostUniqueStations,

                // Station codes that read the same forwards and backwards
                // (length >= 2) - a pure trivia easter egg, not a metric anyone
                // is meant to act on.
                List<String> palindromeStationCodes) {

        public record StationNameEntry(String stationCode, String stationName, int length) {
        }

        public record WordFrequency(String word, int count) {
        }

        public record TrainStopEntry(String trainNumber, String trainName, int uniqueStationCount) {
        }
}
