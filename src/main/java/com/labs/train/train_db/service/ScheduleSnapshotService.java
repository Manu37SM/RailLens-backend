package com.labs.train.train_db.service;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.labs.train.train_db.config.CacheConfig;
import com.labs.train.train_db.entity.TrainSchedule;
import com.labs.train.train_db.repository.TrainScheduleRepository;

import lombok.RequiredArgsConstructor;

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
