package com.labs.train.train_db.model;

import java.util.List;

public record StationResponse(

                String stationCode,

                String stationName,

                Integer totalTrains,

                List<StationTrainResponse> trains

) {
}