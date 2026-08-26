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
