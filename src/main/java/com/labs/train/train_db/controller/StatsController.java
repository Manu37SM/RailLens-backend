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
        private final RankingsService rankingsService;
        private final FunStatsService funStatsService;
        private final AchievementsService achievementsService;

        @GetMapping
        public StatsResponse getStats() {
                return statsService.getStats();
        }

        /**
         * "Rankings" leaderboards (FEATURE.md) - most/fewest halts, longest/
         * shortest halt, most popular origin stations, most connected
         * stations. See RankingsService for how each is derived.
         */
        @GetMapping("/rankings")
        public RankingsResponse getRankings() {
                return rankingsService.getRankings();
        }

        /**
         * "Fun Statistics" (FEATURE.md) - station-name/route trivia, distinct
         * from the leaderboards above. See FunStatsService for how each field
         * is derived.
         */
        @GetMapping("/fun-facts")
        public FunStatsResponse getFunStats() {
                return funStatsService.getFunStats();
        }

        /**
         * "Railway Achievements" (FEATURE.md) - top 100 longest/fastest, mega
         * routes, super express rankings, rare routes, hidden gems. See
         * AchievementsService for how each is derived.
         */
        @GetMapping("/achievements")
        public AchievementsResponse getAchievements() {
                return achievementsService.getAchievements();
        }
}
