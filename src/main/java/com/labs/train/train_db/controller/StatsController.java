package com.labs.train.train_db.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.labs.train.train_db.model.StatsResponse;
import com.labs.train.train_db.service.StatsService;

import lombok.RequiredArgsConstructor;

/**
 * Public, unauthenticated dataset statistics (PROMPT.md's "Statistics"
 * target feature) - total trains/stations, longest/shortest route, busiest
 * station. Deliberately not under /api/admin/** - this is "did you know"
 * content for any visitor, not an operational endpoint, unlike
 * AdminController's stats which exist to help the admin verify an import.
 */
@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {

        private final StatsService statsService;

        @GetMapping
        public StatsResponse getStats() {
                return statsService.getStats();
        }
}
