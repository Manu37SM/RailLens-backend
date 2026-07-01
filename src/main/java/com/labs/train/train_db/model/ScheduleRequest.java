package com.labs.train.train_db.model;

import java.time.LocalTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScheduleRequest {

    private String trainNumber;

    private String stationCode;

    private Integer sequenceNo;

    private LocalTime arrivalTime;

    private LocalTime departureTime;
}