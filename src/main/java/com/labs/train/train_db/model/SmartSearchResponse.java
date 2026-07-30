package com.labs.train.train_db.model;

import java.util.List;

/**
 * "Smart Search" (FEATURE.md) - result of running a structured, natural-
 * language-ish query through SmartSearchQueryParser. {@code recognized}
 * distinguishes "understood the query but found zero matches" (recognized
 * = true, matchCount = 0) from "couldn't parse this query at all"
 * (recognized = false) - the frontend should show different messaging for
 * each (a validation hint vs. an empty-results state).
 */
public record SmartSearchResponse(

                boolean recognized,

                // A plain-English restatement of what was understood, e.g.
                // "Trains stopping at both NDLS and HWH" - null when
                // recognized is false.
                String interpretedAs,

                int matchCount,
                List<TrainSearchResponse> trains) {
}
