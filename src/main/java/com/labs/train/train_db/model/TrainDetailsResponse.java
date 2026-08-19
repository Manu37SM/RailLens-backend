package com.labs.train.train_db.model;

import java.util.List;

public record TrainDetailsResponse(

                String trainNumber,

                String trainName,

                Integer totalStops,

                Integer journeyDistance,

                Long journeyMinutes,

                Double averageSpeed,

                List<RouteStopResponse> route,

                String sourceStationName,
                String destinationStationName

) {
}