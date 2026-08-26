package com.labs.train.train_db.model;

public record StationTrafficProjection(
                String stationCode,
                String stationName,
                long trainCount) {
}
