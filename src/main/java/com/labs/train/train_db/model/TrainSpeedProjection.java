package com.labs.train.train_db.model;

public record TrainSpeedProjection(
                String trainNumber,
                String trainName,
                double averageSpeedKmh,
                int distanceKm,
                long durationMinutes) {
}
