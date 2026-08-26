package com.labs.train.train_db.model;

import java.util.List;

public record JourneySearchResponse(
        String from,
        String to,
        int totalTrains,
        List<JourneyTrainResponse> trains) {
}