package com.labs.train.train_db.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.labs.train.train_db.entity.Train;
import com.labs.train.train_db.entity.TrainSchedule;

@Repository
public interface TrainScheduleRepository
        extends JpaRepository<TrainSchedule, Long> {

    Optional<TrainSchedule> findFirstByTrainOrderBySequenceNoAsc(Train train);

    Optional<TrainSchedule> findFirstByTrainOrderBySequenceNoDesc(Train train);

    List<TrainSchedule> findByTrain_TrainNumberOrderBySequenceNo(String trainNumber);

    List<TrainSchedule> findByStation_StationCodeOrderByArrivalTime(String stationCode);

    List<TrainSchedule> findByStation_StationCode(String stationCode);

    List<TrainSchedule> findByStation_StationCodeOrderBySequenceNo(String stationCode);

}