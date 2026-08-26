package com.labs.train.train_db.config;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.cache.autoconfigure.CacheManagerCustomizer;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

@Configuration
public class CacheConfig {

        public static final String TRAIN_DETAILS_CACHE = "trainDetails";
        public static final String STATION_DETAILS_CACHE = "stationDetails";

        public static final String STATS_CACHE = "stats";

        public static final String SEARCH_INDEX_CACHE = "searchIndex";

        public static final String NETWORK_CACHE = "railwayNetwork";

        public static final String RANKINGS_CACHE = "rankings";

        public static final String FUN_STATS_CACHE = "funStats";

        public static final String ACHIEVEMENTS_CACHE = "achievements";

        public static final String SCHEDULE_SNAPSHOT_CACHE = "scheduleSnapshot";

        @Bean
        public CacheManagerCustomizer<CaffeineCacheManager> cacheManagerCustomizer() {

                return cacheManager -> {

                        cacheManager.setCacheNames(
                                        List.of(
                                                        TRAIN_DETAILS_CACHE, STATION_DETAILS_CACHE, STATS_CACHE, SEARCH_INDEX_CACHE,
                                                        NETWORK_CACHE, RANKINGS_CACHE, FUN_STATS_CACHE, ACHIEVEMENTS_CACHE,
                                                        SCHEDULE_SNAPSHOT_CACHE));

                        cacheManager.setCaffeine(
                                        Caffeine.newBuilder()
                                                        .maximumSize(5_000)
                                                        .expireAfterWrite(15, TimeUnit.MINUTES));
                };
        }
}
