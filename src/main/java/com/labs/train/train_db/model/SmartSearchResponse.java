package com.labs.train.train_db.model;

import java.util.List;

public record SmartSearchResponse(

                boolean recognized,

                String interpretedAs,

                int matchCount,
                List<TrainSearchResponse> trains) {
}
