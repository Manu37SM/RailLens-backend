package com.labs.train.train_db.model;

import java.util.List;

public record DatasetHealthResponse(

                int totalIssues,

                int duplicateScheduleRowCount,
                List<String> duplicateScheduleRowSamples,

                int missingTimingCount,
                List<String> missingTimingSamples,

                int distanceInconsistencyCount,
                List<String> distanceInconsistencySamples,

                int impossibleSpeedCount,
                List<String> impossibleSpeedSamples,

                int haltAnomalyCount,
                List<String> haltAnomalySamples,

                int orphanStationCount,
                List<String> orphanStationSamples,

                int invalidRouteCount,
                List<String> invalidRouteSamples) {
}
