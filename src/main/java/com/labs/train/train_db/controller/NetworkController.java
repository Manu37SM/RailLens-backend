package com.labs.train.train_db.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.labs.train.train_db.model.NetworkStatsResponse;
import com.labs.train.train_db.service.network.RailwayNetworkService;

import lombok.RequiredArgsConstructor;

/**
 * "Railway Network" graph metrics (FEATURE.md) - public, unauthenticated,
 * same visibility level as StatsController's dataset statistics. Separate
 * controller (rather than folding into StatsController) since it's backed
 * by a different, heavier cache (NETWORK_CACHE vs STATS_CACHE) with its own
 * computation cost - keeping them apart makes that cost visible at the
 * routing level, not just in a code comment.
 */
@RestController
@RequestMapping("/api/v1/network")
@RequiredArgsConstructor
public class NetworkController {

        private final RailwayNetworkService railwayNetworkService;

        @GetMapping("/stats")
        public NetworkStatsResponse getNetworkStats() {
                return railwayNetworkService.getNetworkStats();
        }
}
