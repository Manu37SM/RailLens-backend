package com.labs.train.train_db.model;

import java.util.List;

/**
 * "Train Intelligence" scores for a single train - all derived purely from
 * schedule data already in the database (stop times, sequence, distance)
 * plus the shared railway network graph (RailwayNetworkService), no new
 * dataset fields required. See TrainIntelligenceService for how each score
 * is computed and, where the metric has no single "correct" definition
 * (route complexity, journey efficiency, uniqueness, station skipping), the
 * documented judgment call behind it.
 */
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

                // Origin and destination are the same station - a loop service
                // rather than a point-to-point one (e.g. a suburban circular).
                boolean isCircularRoute,

                List<String> possiblySkippedStations) {
}
