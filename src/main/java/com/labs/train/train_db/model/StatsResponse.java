package com.labs.train.train_db.model;

import java.util.List;

public record StatsResponse(
                long totalTrains,
                long totalStations,

                RouteDistanceProjection longestRoute,
                RouteDistanceProjection shortestRoute,

                StationTrafficProjection busiestStation,

                List<StationTrafficProjection> busiestStations,
                List<TrainSpeedProjection> fastestTrains,
                List<TrainSpeedProjection> slowestTrains) {
}
