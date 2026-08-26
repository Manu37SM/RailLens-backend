package com.labs.train.train_db.model;

import java.time.LocalTime;

public record ScheduleResponse(

                String trainNumber,

                String stationCode,

                Integer sequenceNo,

                LocalTime arrivalTime,

                LocalTime departureTime,

                Integer distance) {
}
