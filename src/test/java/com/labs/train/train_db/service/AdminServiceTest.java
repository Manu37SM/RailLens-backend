package com.labs.train.train_db.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import com.labs.train.train_db.config.CacheConfig;
import com.labs.train.train_db.model.AdminStatsResponse;
import com.labs.train.train_db.repository.StationRepository;
import com.labs.train.train_db.repository.TrainRepository;
import com.labs.train.train_db.repository.TrainScheduleRepository;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

        @Mock
        private TrainRepository trainRepository;

        @Mock
        private StationRepository stationRepository;

        @Mock
        private TrainScheduleRepository trainScheduleRepository;

        @Mock
        private CacheManager cacheManager;

        private AdminService adminService() {
                return new AdminService(trainRepository, stationRepository, trainScheduleRepository, cacheManager);
        }

        @Test
        void getStatsReturnsCountsFromEachRepository() {

                when(trainRepository.count()).thenReturn(8000L);
                when(stationRepository.count()).thenReturn(7000L);
                when(trainScheduleRepository.count()).thenReturn(120000L);

                AdminStatsResponse stats = adminService().getStats();

                assertThat(stats.totalTrains()).isEqualTo(8000L);
                assertThat(stats.totalStations()).isEqualTo(7000L);
                assertThat(stats.totalScheduleRows()).isEqualTo(120000L);
        }

        @Test
        void clearAllCachesClearsEveryNamedCache() {

                Cache trainDetailsCache = mock(Cache.class);
                Cache stationDetailsCache = mock(Cache.class);
                Cache statsCache = mock(Cache.class);
                Cache searchIndexCache = mock(Cache.class);
                Cache networkCache = mock(Cache.class);
                Cache rankingsCache = mock(Cache.class);
                Cache funStatsCache = mock(Cache.class);
                Cache achievementsCache = mock(Cache.class);

                when(cacheManager.getCache(CacheConfig.TRAIN_DETAILS_CACHE)).thenReturn(trainDetailsCache);
                when(cacheManager.getCache(CacheConfig.STATION_DETAILS_CACHE)).thenReturn(stationDetailsCache);
                when(cacheManager.getCache(CacheConfig.STATS_CACHE)).thenReturn(statsCache);
                when(cacheManager.getCache(CacheConfig.SEARCH_INDEX_CACHE)).thenReturn(searchIndexCache);
                when(cacheManager.getCache(CacheConfig.NETWORK_CACHE)).thenReturn(networkCache);
                when(cacheManager.getCache(CacheConfig.RANKINGS_CACHE)).thenReturn(rankingsCache);
                when(cacheManager.getCache(CacheConfig.FUN_STATS_CACHE)).thenReturn(funStatsCache);
                when(cacheManager.getCache(CacheConfig.ACHIEVEMENTS_CACHE)).thenReturn(achievementsCache);

                adminService().clearAllCaches();

                verify(trainDetailsCache).clear();
                verify(stationDetailsCache).clear();
                verify(statsCache).clear();
                verify(searchIndexCache).clear();
                verify(networkCache).clear();
                verify(rankingsCache).clear();
                verify(funStatsCache).clear();
                verify(achievementsCache).clear();
        }

        @Test
        void clearAllCachesSkipsMissingCachesWithoutThrowing() {

                // A cache name resolving to null (e.g. renamed/removed from
                // CacheConfig but this list not updated) must be skipped, not
                // crash the whole admin operation over one stale name.
                when(cacheManager.getCache(CacheConfig.TRAIN_DETAILS_CACHE)).thenReturn(null);
                when(cacheManager.getCache(CacheConfig.STATION_DETAILS_CACHE)).thenReturn(null);
                when(cacheManager.getCache(CacheConfig.STATS_CACHE)).thenReturn(null);
                when(cacheManager.getCache(CacheConfig.SEARCH_INDEX_CACHE)).thenReturn(null);
                when(cacheManager.getCache(CacheConfig.NETWORK_CACHE)).thenReturn(null);
                when(cacheManager.getCache(CacheConfig.RANKINGS_CACHE)).thenReturn(null);
                when(cacheManager.getCache(CacheConfig.FUN_STATS_CACHE)).thenReturn(null);
                when(cacheManager.getCache(CacheConfig.ACHIEVEMENTS_CACHE)).thenReturn(null);

                adminService().clearAllCaches();

                verify(cacheManager, never()).getCache("some-other-cache");
        }
}
