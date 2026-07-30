package com.labs.train.train_db.model;

import java.util.List;

/**
 * "Dataset Health" diagnostics (FEATURE.md) - admin-panel-side data quality
 * checks over what's actually in the database right now. Distinct from
 * rail-dataset-analyzer's Python validator/quality score, which runs
 * against a CSV *before* import; this runs against the live database
 * *after* import, so it can catch issues introduced by direct DB edits or
 * bugs in the import path itself, not just malformed source files. See
 * DatasetHealthService for how each check is implemented. Every "Samples"
 * list is capped (see DatasetHealthService.MAX_SAMPLES) - the counts are
 * exact, the samples are just enough to start investigating.
 */
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
