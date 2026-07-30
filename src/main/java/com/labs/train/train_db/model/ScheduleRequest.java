package com.labs.train.train_db.model;

import java.time.LocalTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Converted from a mutable @Getter/@Setter class to a record - this was
 * the only non-record DTO in model/, flagged as a style inconsistency in
 * the backend architecture review (P2). Jakarta Bean Validation supports
 * constraint annotations directly on record components (Spring resolves
 * them via the generated accessor), so @Valid on the controller's
 * @RequestBody continues to work exactly as before.
 */
public record ScheduleRequest(
        @NotBlank(message = "trainNumber is required") String trainNumber,
        @NotBlank(message = "stationCode is required") String stationCode,
        @NotNull(message = "sequenceNo is required") Integer sequenceNo,
        LocalTime arrivalTime,
        LocalTime departureTime) {
}