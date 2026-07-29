package com.labs.train.train_db.model;

import java.time.LocalTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScheduleRequest {

    @NotBlank(message = "trainNumber is required")
    private String trainNumber;

    @NotBlank(message = "stationCode is required")
    private String stationCode;

    @NotNull(message = "sequenceNo is required")
    private Integer sequenceNo;

    private LocalTime arrivalTime;

    private LocalTime departureTime;
}