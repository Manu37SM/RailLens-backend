package com.labs.train.train_db.model;

/**
 * Backs the Admin Portal's landing view (see AuthController-style
 * write-up in AdminController). Deliberately just counts, not a full data
 * dump - the admin key gates a destructive bulk-import action, not general
 * data browsing, so this stays minimal rather than growing into a second
 * copy of the public search endpoints behind a different auth scheme.
 */
public record AdminStatsResponse(
                long totalTrains,
                long totalStations,
                long totalScheduleRows) {
}
