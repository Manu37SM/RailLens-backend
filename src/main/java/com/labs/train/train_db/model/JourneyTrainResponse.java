package com.labs.train.train_db.model;

import java.time.LocalTime;

public record JourneyTrainResponse(
        String trainNumber,
        String trainName,
        LocalTime departureTime,
        LocalTime arrivalTime,
        String duration,
        Integer distance) {
}