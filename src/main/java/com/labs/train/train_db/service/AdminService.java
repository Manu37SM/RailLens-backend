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

        public void clearAllCaches() {

                List<String> cacheNames = CacheConfig.DATASET_CACHES;

                for (String cacheName : cacheNames) {

                        Cache cache = cacheManager.getCache(cacheName);

                        if (cache != null) {
                                cache.clear();
                        }
                }

                log.info("Admin manually cleared all caches: {}", cacheNames);
        }
}
