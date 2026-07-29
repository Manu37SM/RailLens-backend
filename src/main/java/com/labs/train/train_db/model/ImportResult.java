package com.labs.train.train_db.model;

/**
 * Result of {@code POST /api/admin/import}. The endpoint used to return a
 * hardcoded "Import Started" string regardless of what actually happened -
 * including when the import silently failed. This makes the real outcome
 * (rows imported, rows skipped, and whether it succeeded at all) visible to
 * the caller.
 */
public record ImportResult(
                boolean success,
                int rowsImported,
                int rowsFailed,
                String message) {
}
