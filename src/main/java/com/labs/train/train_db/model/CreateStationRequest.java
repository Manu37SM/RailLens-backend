package com.labs.train.train_db.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/stations}. Kept separate from the
 * {@code Station} entity so the API contract doesn't change shape whenever
 * the persistence model does (see the RailLens backend review's "entity
 * leakage" finding).
 */
public record CreateStationRequest(

                @NotBlank(message = "stationCode is required")
                @Size(max = 255, message = "stationCode must be at most 255 characters")
                String stationCode,

                @Size(max = 255, message = "stationName must be at most 255 characters")
                String stationName) {
}
