package com.labs.train.train_db.service;

import java.util.List;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.labs.train.train_db.config.CacheConfig;
import com.labs.train.train_db.model.AdminStatsResponse;
import com.labs.train.train_db.repository.StationRepository;
import com.labs.train.train_db.repository.TrainRepository;
import com.labs.train.train_db.repository.TrainScheduleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

        private final TrainRepository trainRepository;
        private final StationRepository stationRepository;
        private final TrainScheduleRepository trainScheduleRepository;
        private final CacheManager cacheManager;

        public AdminStatsResponse getStats() {
                return new AdminStatsResponse(
                                trainRepository.count(),
                                stationRepository.count(),
                                trainScheduleRepository.count());
        }

        /**
         * Manually flushes every named cache (see CacheConfig) - normally
         * unnecessary since writes already evict explicitly (see
         * RailwayDataImportService#evictCachesIfAnyRowsChanged), but useful as
         * an operator escape hatch: e.g. after editing data directly in the
         * database rather than through the app, or just to confirm caching
         * isn't the cause of some data-looks-stale report during debugging.
         */
        public void clearAllCaches() {

                List<String> cacheNames = List.of(
                                CacheConfig.TRAIN_DETAILS_CACHE,
                                CacheConfig.STATION_DETAILS_CACHE,
                                CacheConfig.STATS_CACHE,
                                CacheConfig.SEARCH_INDEX_CACHE,
                                CacheConfig.NETWORK_CACHE,
                                CacheConfig.RANKINGS_CACHE,
                                CacheConfig.FUN_STATS_CACHE,
                                CacheConfig.ACHIEVEMENTS_CACHE);

                for (String cacheName : cacheNames) {

                        Cache cache = cacheManager.getCache(cacheName);

                        if (cache != null) {
                                cache.clear();
                        }
                }

                log.info("Admin manually cleared all caches: {}", cacheNames);
        }
}
