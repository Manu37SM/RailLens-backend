package com.labs.train.train_db.model;

import java.util.List;

/**
 * "Route Analytics" (FEATURE.md) - a train-to-train route comparison.
 * Everything here is derived purely from each train's own ordered stop
 * sequence; see RouteAnalyticsService for how each field is computed.
 */
public record RouteComparisonResponse(

                String trainNumberA,
                String trainNameA,
                int totalStationsA,

                String trainNumberB,
                String trainNameB,
                int totalStationsB,

                // |stations(A) ∩ stations(B)|, regardless of order.
                int sharedStationCount,

                // Jaccard similarity of the two station sets: shared / union,
                // as a percentage. Order-independent - two routes serving
                // exactly the same stations in a different sequence score 100.
                double routeSimilarityPercent,

                // The single longest run of stations that appear consecutively,
                // in the same order, on both routes - i.e. the longest shared
                // physical section of track both trains travel. Empty if the
                // two routes share no station.
                List<String> longestCommonSegment,

                // Last station of longestCommonSegment - the point after which
                // the two trains' paths part ways. Null if the routes never
                // share a contiguous run (or one is empty).
                String divergencePoint,

                // The next station after divergencePoint that both routes visit
                // again (not necessarily consecutively) - i.e. where the two
                // trains' paths reconverge after splitting. Null if they never
                // meet again.
                String convergencePoint,

                // Train B's route is exactly train A's route in reverse order -
                // the classic "up" and "down" service pair.
                boolean isReverseRoute,

                // Both trains stop at exactly the same stations in exactly the
                // same order.
                boolean isSameRoute) {
}
