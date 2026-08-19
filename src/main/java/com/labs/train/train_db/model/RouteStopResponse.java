package com.labs.train.train_db.model;

import java.time.LocalTime;

public record RouteStopResponse(

        Integer sequenceNo,

        String stationCode,
        String stationName,

        LocalTime arrivalTime,
        LocalTime departureTime,

        Integer distance,

        Integer distanceFromPrevious,

        Integer haltMinutes,

        Integer journeyDay,

        boolean origin,

        boolean destination

) {
}