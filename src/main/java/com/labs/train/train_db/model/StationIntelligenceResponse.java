package com.labs.train.train_db.model;

public record StationIntelligenceResponse(

                String stationCode,
                String stationName,

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

                int[] departureCountByHour,
                int[] arrivalCountByHour) {
}
