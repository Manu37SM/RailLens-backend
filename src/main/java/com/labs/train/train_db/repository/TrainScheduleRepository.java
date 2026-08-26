package com.labs.train.train_db.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.labs.train.train_db.entity.Train;
import com.labs.train.train_db.entity.TrainSchedule;
import com.labs.train.train_db.model.RouteDistanceProjection;
import com.labs.train.train_db.model.StationTrafficProjection;

public interface TrainScheduleRepository
        extends JpaRepository<TrainSchedule, Long> {

    Optional<TrainSchedule> findFirstByTrainOrderBySequenceNoAsc(Train train);

    Optional<TrainSchedule> findFirstByTrainOrderBySequenceNoDesc(Train train);

    List<TrainSchedule> findByTrain_TrainNumberOrderBySequenceNo(String trainNumber);

    List<TrainSchedule> findByStation_StationCodeOrderByArrivalTime(String stationCode);

    List<TrainSchedule> findByStation_StationCode(String stationCode);

    List<TrainSchedule> findByTrain_IdInOrderByTrain_IdAscSequenceNoAsc(List<Long> trainIds);

    List<TrainSchedule> findAllByOrderByTrain_IdAscSequenceNoAsc();

    void deleteByTrain(Train train);

    @Query("""
            SELECT new com.labs.train.train_db.model.RouteDistanceProjection(
                ts.train.trainNumber, ts.train.trainName, MAX(ts.distance))
            FROM TrainSchedule ts
            WHERE ts.distance IS NOT NULL
            GROUP BY ts.train.trainNumber, ts.train.trainName
            ORDER BY MAX(ts.distance) DESC
            """)
    List<RouteDistanceProjection> findRouteDistancesDescending(Pageable pageable);

    @Query("""
            SELECT new com.labs.train.train_db.model.RouteDistanceProjection(
                ts.train.trainNumber, ts.train.trainName, MAX(ts.distance))
            FROM TrainSchedule ts
            WHERE ts.distance IS NOT NULL
            GROUP BY ts.train.trainNumber, ts.train.trainName
            HAVING MAX(ts.distance) > 0
            ORDER BY MAX(ts.distance) ASC
            """)
    List<RouteDistanceProjection> findRouteDistancesAscending(Pageable pageable);

    @Query("""
            SELECT new com.labs.train.train_db.model.StationTrafficProjection(
                ts.station.stationCode, ts.station.stationName, COUNT(ts))
            FROM TrainSchedule ts
            GROUP BY ts.station.stationCode, ts.station.stationName
            ORDER BY COUNT(ts) DESC
            """)
    List<StationTrafficProjection> findBusiestStations(Pageable pageable);

}