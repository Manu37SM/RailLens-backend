package com.labs.train.train_db.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.labs.train.train_db.model.NetworkStatsResponse;
import com.labs.train.train_db.service.network.RailwayNetworkService;

import lombok.RequiredArgsConstructor;

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
