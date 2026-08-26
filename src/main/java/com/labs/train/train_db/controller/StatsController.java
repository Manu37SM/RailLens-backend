package com.labs.train.train_db.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.labs.train.train_db.model.AchievementsResponse;
import com.labs.train.train_db.model.FunStatsResponse;
import com.labs.train.train_db.model.RankingsResponse;
import com.labs.train.train_db.model.StatsResponse;
import com.labs.train.train_db.service.AchievementsService;
import com.labs.train.train_db.service.FunStatsService;
import com.labs.train.train_db.service.RankingsService;
import com.labs.train.train_db.service.StatsService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {

        private final StatsService statsService;
        private final RankingsService rankingsService;
        private final FunStatsService funStatsService;
        private final AchievementsService achievementsService;

        @GetMapping
        public StatsResponse getStats() {
                return statsService.getStats();
        }

        @GetMapping("/rankings")
        public RankingsResponse getRankings() {
                return rankingsService.getRankings();
        }

        @GetMapping("/fun-facts")
        public FunStatsResponse getFunStats() {
                return funStatsService.getFunStats();
        }

        @GetMapping("/achievements")
        public AchievementsResponse getAchievements() {
                return achievementsService.getAchievements();
        }
}
