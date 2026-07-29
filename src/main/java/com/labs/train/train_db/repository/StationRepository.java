package com.labs.train.train_db.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.labs.train.train_db.entity.Station;
import com.labs.train.train_db.model.StationSearchResponse;

public interface StationRepository extends JpaRepository<Station, Long> {

    Optional<Station> findByStationCode(String stationCode);

    @Query("""
            SELECT s
            FROM Station s
            WHERE LOWER(s.stationName) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(s.stationCode) LIKE LOWER(CONCAT('%', :query, '%'))
            ORDER BY s.stationName
            """)
    Page<Station> search(@Param("query") String query, Pageable pageable);

    /**
     * Code+name only, for every station - backs the fuzzy-search fallback,
     * same reasoning as TrainRepository#findAllSearchKeys.
     */
    @Query("SELECT new com.labs.train.train_db.model.StationSearchResponse(s.stationCode, s.stationName) FROM Station s")
    List<StationSearchResponse> findAllSearchKeys();

}