package com.labs.train.train_db.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.QueryHint;

import org.hibernate.jpa.HibernateHints;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import com.labs.train.train_db.entity.Train;
import com.labs.train.train_db.entity.TrainSchedule;
import com.labs.train.train_db.model.RouteDistanceProjection;
import com.labs.train.train_db.model.StationTrafficProjection;

/**
 * Every finder that returns schedule rows loads the row's train and station in the same query (@EntityGraph).
 * The associations are eager, and without the join Hibernate fetched each distinct train and station with its own
 * SELECT afterwards: the full snapshot behind stats/rankings took about 22,800 round trips to the database.
 */
public interface TrainScheduleRepository
        extends JpaRepository<TrainSchedule, Long> {

    @EntityGraph(attributePaths = { "train", "station" })
    Optional<TrainSchedule> findFirstByTrainOrderBySequenceNoAsc(Train train);

    @EntityGraph(attributePaths = { "train", "station" })
    Optional<TrainSchedule> findFirstByTrainOrderBySequenceNoDesc(Train train);

    @EntityGraph(attributePaths = { "train", "station" })
    List<TrainSchedule> findByTrain_TrainNumberOrderBySequenceNo(String trainNumber);

    @EntityGraph(attributePaths = { "train", "station" })
    List<TrainSchedule> findByStation_StationCodeOrderByArrivalTime(String stationCode);

    @EntityGraph(attributePaths = { "train", "station" })
    List<TrainSchedule> findByStation_StationCode(String stationCode);

    @EntityGraph(attributePaths = { "train", "station" })
    List<TrainSchedule> findByTrain_IdInOrderByTrain_IdAscSequenceNoAsc(List<Long> trainIds);

    /**
     * The whole timetable (about 244k rows). The fetch size makes the PostgreSQL driver stream it in batches inside the
     * read-only transaction instead of buffering every row first, which roughly halves the memory peak of the load.
     */
    @EntityGraph(attributePaths = { "train", "station" })
    @QueryHints(@QueryHint(name = HibernateHints.HINT_FETCH_SIZE, value = "2000"))
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