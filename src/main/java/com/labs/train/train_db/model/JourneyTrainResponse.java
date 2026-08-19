package com.labs.train.train_db.model;

import java.time.LocalTime;

/**
 * "Journey Analysis" (FEATURE.md) fields - movingMinutes/haltedMinutes/
 * numHalts/longestHaltMinutes/averageMovingSpeedKmh/night-day split are all
 * scoped to this specific source-to-destination leg (not the train's whole
 * route - see TrainIntelligenceResponse for the whole-route equivalents),
 * computed in JourneyService alongside the existing duration/distance
 * fields. See JourneyService#analyzeSegment for how each is derived.
 */
public record JourneyTrainResponse(
        String trainNumber,
        String trainName,
        LocalTime departureTime,
        LocalTime arrivalTime,
        String duration,
        Integer distance,

        long movingMinutes,
        long haltedMinutes,
        int numHalts,
        Long longestHaltMinutes,
        Double averageMovingSpeedKmh,
        Double nightTravelPercent,
        Double dayTravelPercent) {
}