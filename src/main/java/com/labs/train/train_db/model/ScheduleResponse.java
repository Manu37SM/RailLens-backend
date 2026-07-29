package com.labs.train.train_db.model;

import java.time.LocalTime;

/**
 * Response for {@code POST /api/schedules}. Mirrors just the fields of the
 * created record rather than returning the {@code TrainSchedule} entity
 * (and its full {@code Train}/{@code Station} entity graph) directly.
 */
public record ScheduleResponse(

                String trainNumber,

                String stationCode,

                Integer sequenceNo,

                LocalTime arrivalTime,

                LocalTime departureTime,

                Integer distance) {
}
