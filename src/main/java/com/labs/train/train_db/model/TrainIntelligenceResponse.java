package com.labs.train.train_db.model;

import java.util.List;

public record TrainIntelligenceResponse(

                String trainNumber,
                String trainName,

                double routeComplexityScore,
                double trainUniquenessScore,
                double expressnessScoreKmPerHalt,

                double nightTravelPercent,
                double dayTravelPercent,

                Integer longestNonStopSegmentKm,
                String longestNonStopSegmentFromStation,
                String longestNonStopSegmentToStation,

                double averageHaltMinutes,
                double journeyEfficiencyIndex,

                boolean isCircularRoute,

                List<String> possiblySkippedStations) {
}
