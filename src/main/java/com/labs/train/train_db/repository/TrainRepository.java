package com.labs.train.train_db.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.labs.train.train_db.entity.Train;
import com.labs.train.train_db.model.TrainSearchResponse;

public interface TrainRepository extends JpaRepository<Train, Long> {

    Optional<Train> findByTrainNumber(String trainNumber);

    @Query("""
            SELECT t
            FROM Train t
            WHERE LOWER(t.trainName) LIKE LOWER(CONCAT('%', :query, '%'))
               OR t.trainNumber LIKE CONCAT('%', :query, '%')
            ORDER BY t.trainNumber
            """)
    Page<Train> search(
            @Param("query") String query,
            Pageable pageable);

    @Query("SELECT new com.labs.train.train_db.model.TrainSearchResponse(t.trainNumber, t.trainName) FROM Train t")
    List<TrainSearchResponse> findAllSearchKeys();

}