package com.labs.train.train_db.model;

public record AdminStatsResponse(
                long totalTrains,
                long totalStations,
                long totalScheduleRows) {
}
