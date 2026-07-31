package com.labs.train.train_db.service;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.labs.train.train_db.config.CacheConfig;
import com.labs.train.train_db.entity.TrainSchedule;
import com.labs.train.train_db.repository.TrainScheduleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Single shared, cached source for "every schedule row, ordered by train
 * then sequence" - the ~300k-row full-table load that StatsService,
 * RankingsService, FunStatsService, AchievementsService, and
 * RailwayNetworkService each used to call independently via
 * {@code TrainScheduleRepository.findAllByOrderByTrain_IdAscSequenceNoAsc()}.
 *
 * Each of those was individually reasonable (one linear pass, each behind
 * its own @Cacheable endpoint), but none of them shared the underlying data -
 * five independent full-table loads of the same ~300k rows. Under steady
 * traffic that's fine (each cache only misses once per TTL), but Render's
 * free tier spins a service down after 15 minutes idle, wiping every cache
 * at once - so the first burst of traffic after a cold start could trigger
 * several of these full loads back-to-back or concurrently, each briefly
 * holding its own independent ~300k-entity list in memory at the same time.
 * Routing all of them through this one cached method means a cold start
 * pays that cost once, not up to five times over.
 *
 * Deliberately its own class/cache rather than folding into
 * RailwayNetworkService's existing NETWORK_CACHE snapshot - that snapshot is
 * a derived graph structure (StationNetworkNode map + connected components),
 * built FROM this raw list, not a substitute for it; keeping the raw list
 * available on its own lets StatsService/RankingsService/FunStatsService/
 * AchievementsService depend on just the data they need instead of forcing
 * every one of them to also build a full network graph.
 *
 * Deliberately NOT used by DatasetHealthService - that service is
 * intentionally uncached (see its own javadoc) so an admin always sees a
 * fresh, on-demand check rather than a result that could be stale by up to
 * this cache's TTL. It's also admin-only and low-traffic, not a contributor
 * to the public-endpoint cold-start burst this class exists to soften.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleSnapshotService {

        private final TrainScheduleRepository trainScheduleRepository;

        @Cacheable(cacheNames = CacheConfig.SCHEDULE_SNAPSHOT_CACHE)
        public List<TrainSchedule> getAllOrderedByTrainThenSequence() {
                return trainScheduleRepository.findAllByOrderByTrain_IdAscSequenceNoAsc();
        }
}
