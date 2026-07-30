package com.labs.train.train_db.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.labs.train.train_db.model.AdminStatsResponse;
import com.labs.train.train_db.model.DatasetHealthResponse;
import com.labs.train.train_db.service.AdminService;
import com.labs.train.train_db.service.DatasetHealthService;

import lombok.RequiredArgsConstructor;

/**
 * Admin endpoints backing the Admin Portal (see train-db-frontend's
 * app/admin). Kept separate from RailwayDataImportController, which owns
 * the one endpoint that touches train/station/schedule data itself (POST
 * /api/v1/admin/import) - everything here is either read-only or, for
 * cache clearing, only ever affects the cache layer, never the database.
 * Both controllers share the /api/v1/admin/** prefix, so both are covered
 * by AdminApiKeyInterceptor without needing their own auth wiring.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

        private final AdminService adminService;
        private final DatasetHealthService datasetHealthService;

        @GetMapping("/stats")
        public AdminStatsResponse getStats() {
                return adminService.getStats();
        }

        /**
         * "Dataset Health" diagnostics (FEATURE.md) - see
         * DatasetHealthResponse for what each check covers and how it differs
         * from the Python import-time validator in rail-dataset-analyzer.
         */
        @GetMapping("/health")
        public DatasetHealthResponse getDatasetHealth() {
                return datasetHealthService.checkHealth();
        }

        @PostMapping("/cache/clear")
        public ResponseEntity<Void> clearCache() {
                adminService.clearAllCaches();
                return ResponseEntity.noContent().build();
        }
}
