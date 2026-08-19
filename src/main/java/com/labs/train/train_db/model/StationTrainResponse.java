package com.labs.train.train_db.model;

import java.time.LocalTime;

public record StationTrainResponse(

                String trainNumber,

                String trainName,

                LocalTime arrivalTime,

                LocalTime departureTime,

                Integer distance,

                Integer sequenceNo,

                boolean origin,

                boolean destination

) {
}