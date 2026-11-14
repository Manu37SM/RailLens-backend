package com.labs.train.train_db.config;

import java.util.List;

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

        public static final String STATION_INTELLIGENCE_CACHE = "stationIntelligence";

        public static final String TRAIN_INTELLIGENCE_CACHE = "trainIntelligence";

        /** Every cache built from the timetable. Whatever changes the timetable clears all of them. */
        public static final List<String> DATASET_CACHES = List.of(
                        TRAIN_DETAILS_CACHE, STATION_DETAILS_CACHE, STATS_CACHE, SEARCH_INDEX_CACHE, NETWORK_CACHE,
                        RANKINGS_CACHE, FUN_STATS_CACHE, ACHIEVEMENTS_CACHE, SCHEDULE_SNAPSHOT_CACHE,
                        STATION_INTELLIGENCE_CACHE, TRAIN_INTELLIGENCE_CACHE);

        /**
         * No time-based expiry: the timetable only changes through the CSV import and the admin endpoints, and each of
         * those clears every cache in {@link #DATASET_CACHES}. (A 15-minute expiry used to throw away the network
         * analysis while the site was in use, so a visitor paid for the rebuild, over a minute on Render's CPU.)
         * Sizes are bounded per cache instead: whole-dataset results hold a handful of entries; per-item caches hold
         * what fits comfortably in a 192 MB heap (station details reach about 120 KB of JSON for the biggest stations).
         */
        @Bean
        public CacheManagerCustomizer<CaffeineCacheManager> cacheManagerCustomizer() {

                return cacheManager -> {

                        cacheManager.setCacheNames(DATASET_CACHES);

                        for (String wholeDataset : List.of(STATS_CACHE, SEARCH_INDEX_CACHE, NETWORK_CACHE, RANKINGS_CACHE,
                                        FUN_STATS_CACHE, ACHIEVEMENTS_CACHE, SCHEDULE_SNAPSHOT_CACHE)) {
                                cacheManager.registerCustomCache(wholeDataset, Caffeine.newBuilder().maximumSize(10).build());
                        }
                        cacheManager.registerCustomCache(TRAIN_DETAILS_CACHE, Caffeine.newBuilder().maximumSize(2_000).build());
                        cacheManager.registerCustomCache(STATION_DETAILS_CACHE, Caffeine.newBuilder().maximumSize(150).build());
                        cacheManager.registerCustomCache(TRAIN_INTELLIGENCE_CACHE, Caffeine.newBuilder().maximumSize(2_000).build());
                        cacheManager.registerCustomCache(STATION_INTELLIGENCE_CACHE, Caffeine.newBuilder().maximumSize(2_000).build());
                };
        }
}
