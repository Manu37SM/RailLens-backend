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
 * The free-text search endpoints themselves are deliberately NOT cached
 * per-query here - free-text queries have too much cardinality
 * (near-infinite distinct inputs) for a cache to help much, and the
 * primary LIKE path is already reasonably cheap (a single indexed query
 * capped at AppConstants.SEARCH_PAGE_SIZE rows). SEARCH_INDEX_CACHE below
 * is different: it caches the small, bounded "every train/station"
 * candidate list used only by the fuzzy-match fallback, not per-query
 * results. Detail lookups (trainDetails/stationDetails) remain the primary
 * caching target: a relatively small, bounded set of trains/stations that
 * get requested repeatedly by many different users.
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

        /**
         * Backs GET /api/stats (StatsService). A single-entry cache (one key
         * regardless of caller) rather than per-parameter like the two above -
         * this endpoint has no parameters, it's the same aggregate response
         * for everyone. Runs several GROUP BY scans over the whole schedule
         * table (see TrainScheduleRepository's stats queries), so caching it
         * matters more than the per-lookup caches above once this is a public,
         * unauthenticated endpoint anyone can hit repeatedly.
         */
        public static final String STATS_CACHE = "stats";

        /**
         * Single-entry-per-type cache (one key for "all trains," one for
         * "all stations") backing the fuzzy-search fallback in
         * TrainService/StationService. Only read on the rare path where the
         * primary LIKE search finds zero results, so caching the full
         * number/name list here keeps that fallback from re-scanning the
         * whole table on every typo'd query.
         */
        public static final String SEARCH_INDEX_CACHE = "searchIndex";

        @Bean
        public CacheManagerCustomizer<CaffeineCacheManager> cacheManagerCustomizer() {

                return cacheManager -> {

                        cacheManager.setCacheNames(
                                        List.of(TRAIN_DETAILS_CACHE, STATION_DETAILS_CACHE, STATS_CACHE, SEARCH_INDEX_CACHE));

                        cacheManager.setCaffeine(
                                        Caffeine.newBuilder()
                                                        .maximumSize(5_000)
                                                        .expireAfterWrite(15, TimeUnit.MINUTES));
                };
        }
}
