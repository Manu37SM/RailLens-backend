package com.labs.train.train_db.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.labs.train.train_db.service.AchievementsService;
import com.labs.train.train_db.service.FunStatsService;
import com.labs.train.train_db.service.RankingsService;
import com.labs.train.train_db.service.StationService;
import com.labs.train.train_db.service.StatsService;
import com.labs.train.train_db.service.TrainService;
import com.labs.train.train_db.service.network.RailwayNetworkService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fills the expensive caches (timetable snapshot, network graph, stats, rankings...) in the background as soon as the
 * app is up. On Render's free plan the app sleeps when idle, and without this the first visitor after a wake-up waited
 * for the whole network analysis. Runs on one low-priority daemon thread; startup and the health check don't wait.
 * A visitor who asks for one of these while it runs simply waits for the same computation (the caches use sync=true),
 * never a second copy. Off with {@code raillens.cache.warmup.enabled=false}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheWarmup {

        private final StatsService statsService;
        private final RailwayNetworkService railwayNetworkService;
        private final RankingsService rankingsService;
        private final FunStatsService funStatsService;
        private final AchievementsService achievementsService;
        private final TrainService trainService;
        private final StationService stationService;

        @Value("${raillens.cache.warmup.enabled:true}")
        private boolean enabled;

        @EventListener(ApplicationReadyEvent.class)
        public void start() {
                if (!enabled) {
                        return;
                }
                Thread thread = new Thread(this::warm, "cache-warmup");
                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY);
                thread.start();
        }

        void warm() {
                long startedAt = System.currentTimeMillis();
                step("search indexes", () -> {
                        trainService.fuzzySearchIndex();
                        stationService.fuzzySearchIndex();
                });
                step("stats", statsService::getStats);
                step("network", railwayNetworkService::buildSnapshot);
                step("rankings", rankingsService::getRankings);
                step("fun stats", funStatsService::getFunStats);
                step("achievements", achievementsService::getAchievements);
                log.info("Cache warm-up finished in {} ms", System.currentTimeMillis() - startedAt);
        }

        private void step(String name, Runnable work) {
                long startedAt = System.currentTimeMillis();
                try {
                        work.run();
                        log.info("Cache warm-up: {} ready in {} ms", name, System.currentTimeMillis() - startedAt);
                } catch (RuntimeException e) {
                        // A failed warm-up only means the first visitor computes it instead, as before.
                        log.warn("Cache warm-up: {} failed ({}); it will be built on first use", name, e.toString());
                }
        }
}
