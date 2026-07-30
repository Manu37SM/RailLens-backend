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

        /**
         * Single-entry cache for {@code RailwayNetworkService#buildSnapshot} -
         * the full station-graph build (connected components + Brandes'
         * algorithm for betweenness/closeness/diameter over the largest
         * component) is the most expensive computation in the app, run once
         * on cache miss rather than per request. Backs every "Railway
         * Intelligence" feature that needs network-wide context (station
         * importance/connectivity, train uniqueness, route analytics).
         */
        public static final String NETWORK_CACHE = "railwayNetwork";

        /**
         * Single-entry cache for {@code RankingsService#getRankings} - like
         * STATS_CACHE, a parameterless public endpoint returning the same
         * aggregate response for everyone, and like NETWORK_CACHE it partly
         * builds on RailwayNetworkService's own cached snapshot but still
         * does its own full schedule-table scan for the halt-count/duration
         * leaderboards, so it's worth caching separately.
         */
        public static final String RANKINGS_CACHE = "rankings";

        /**
         * Single-entry cache for {@code FunStatsService#getFunStats} - same
         * "parameterless public endpoint, cache the one aggregate response"
         * reasoning as STATS_CACHE/RANKINGS_CACHE.
         */
        public static final String FUN_STATS_CACHE = "funStats";

        /**
         * Single-entry cache for {@code AchievementsService#getAchievements} -
         * same reasoning as STATS_CACHE/RANKINGS_CACHE/FUN_STATS_CACHE.
         */
        public static final String ACHIEVEMENTS_CACHE = "achievements";

        @Bean
        public CacheManagerCustomizer<CaffeineCacheManager> cacheManagerCustomizer() {

                return cacheManager -> {

                        cacheManager.setCacheNames(
                                        List.of(
                                                        TRAIN_DETAILS_CACHE, STATION_DETAILS_CACHE, STATS_CACHE, SEARCH_INDEX_CACHE,
                                                        NETWORK_CACHE, RANKINGS_CACHE, FUN_STATS_CACHE, ACHIEVEMENTS_CACHE));

                        cacheManager.setCaffeine(
                                        Caffeine.newBuilder()
                                                        .maximumSize(5_000)
                                                        .expireAfterWrite(15, TimeUnit.MINUTES));
                };
        }
}
