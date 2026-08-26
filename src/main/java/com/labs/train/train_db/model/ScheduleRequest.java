package com.labs.train.train_db.model;

import java.time.LocalTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ScheduleRequest(
        @NotBlank(message = "trainNumber is required") String trainNumber,
        @NotBlank(message = "stationCode is required") String stationCode,
        @NotNull(message = "sequenceNo is required") Integer sequenceNo,
        LocalTime arrivalTime,
        LocalTime departureTime) {
}