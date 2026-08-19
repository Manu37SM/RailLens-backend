package com.labs.train.train_db.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.labs.train.train_db.model.ImportResult;
import com.labs.train.train_db.service.RailwayDataImportService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class RailwayDataImportController {

    private final RailwayDataImportService importService;

    /**
     * Runs synchronously and blocks until the whole CSV has been processed -
     * previously this always returned the string "Import Started" instead of
     * a real outcome, which misleadingly implied an async job even though
     * the caller was already blocked waiting for it. Now returns the actual
     * row counts and reflects failure with a 500 instead of a false 200.
     *
     * DESTRUCTIVE: for each train encountered, its existing schedule rows
     * are deleted and replaced (see {@code RailwayDataImportService}). Do
     * not call this against production data without reviewing the CSV
     * first - unchanged from before this enhancement, just calling it out
     * since the response now looks more like a "safe to call casually"
     * admin action than it actually is.
     */
    @PostMapping("/import")
    public ResponseEntity<ImportResult> importData() {

        ImportResult result = importService.importCsv();

        return result.success()
                        ? ResponseEntity.ok(result)
                        : ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }
}