package com.labs.train.train_db.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTrainRequest(

                @NotBlank(message = "trainNumber is required")
                @Size(max = 255, message = "trainNumber must be at most 255 characters")
                String trainNumber,

                @Size(max = 255, message = "trainName must be at most 255 characters")
                String trainName) {
}
