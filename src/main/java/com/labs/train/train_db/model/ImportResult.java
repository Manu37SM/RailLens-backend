package com.labs.train.train_db.model;

public record ImportResult(
                boolean success,
                int rowsImported,
                int rowsFailed,
                String message) {
}
