package com.labs.train.train_db.model;

import java.util.List;

public record RouteComparisonResponse(

                String trainNumberA,
                String trainNameA,
                int totalStationsA,

                String trainNumberB,
                String trainNameB,
                int totalStationsB,

                int sharedStationCount,

                double routeSimilarityPercent,

                List<String> longestCommonSegment,

                String divergencePoint,

                String convergencePoint,

                boolean isReverseRoute,

                boolean isSameRoute) {
}
