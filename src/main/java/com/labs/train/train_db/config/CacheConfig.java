package com.labs.train.train_db.config;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.cache.autoconfigure.CacheManagerCustomizer;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Two named caches for read-heavy, relatively static lookups:
 *
 * - "trainDetails": backs {@code TrainService#getTrainDetails}, keyed by
 *   train number.
 * - "stationDetails": backs {@code StationService#getStation}, keyed by
 *   station code.
 *
 * Search endpoints are deliberately NOT cached here - free-text queries
 * have too much cardinality (near-infinite distinct inputs) for a cache to
 * help much, and they're already reasonably cheap (a single indexed LIKE
 * query capped at AppConstants.SEARCH_PAGE_SIZE rows). Detail lookups are
 * the better target: a relatively small, bounded set of trains/stations
 * that get requested repeatedly by many different users.
 *
 * expireAfterWrite is a safety net, not the primary invalidation strategy -
 * writes that actually change cached data (see ScheduleService,
 * RailwayDataImportService) evict explicitly so results are correct
 * immediately, not just "eventually" within the TTL window.
 */
@Configuration
public class CacheConfig {

        public static final String TRAIN_DETAILS_CACHE = "trainDetails";
        public static final String STATION_DETAILS_CACHE = "stationDetails";

        @Bean
        public CacheManagerCustomizer<CaffeineCacheManager> cacheManagerCustomizer() {

                return cacheManager -> {

                        cacheManager.setCacheNames(
                                        List.of(TRAIN_DETAILS_CACHE, STATION_DETAILS_CACHE));

                        cacheManager.setCaffeine(
                                        Caffeine.newBuilder()
                                                        .maximumSize(5_000)
                                                        .expireAfterWrite(15, TimeUnit.MINUTES));
                };
        }
}
