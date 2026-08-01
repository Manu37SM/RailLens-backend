package com.labs.train.train_db.service;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.labs.train.train_db.entity.Station;
import com.labs.train.train_db.entity.Train;
import com.labs.train.train_db.entity.TrainSchedule;
import com.labs.train.train_db.repository.StationRepository;
import com.labs.train.train_db.repository.TrainRepository;
import com.labs.train.train_db.repository.TrainScheduleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Persists exactly one already-parsed CSV row, in its own {@code
 * Propagation.NESTED} transaction (a JDBC savepoint inside the caller's
 * {@code RailwayImportBatchService} batch transaction - see {@link
 * com.labs.train.train_db.config.TransactionConfig} for why NESTED had to
 * be explicitly enabled).
 *
 * Split out of {@code RailwayImportBatchService} for the same reason {@code
 * RailwayImportBatchService} itself was split out of {@code
 * RailwayDataImportService}: {@code @Transactional} only takes effect
 * through Spring's proxy, which a batch method calling another method on
 * {@code this} would bypass entirely.
 *
 * Why this exists at all: a single row failing (e.g. a constraint
 * violation) marks the enclosing Postgres transaction "aborted," and every
 * later statement in that same transaction is refused until rollback. With
 * the whole batch as one transaction, one bad row was silently failing
 * every other row after it in that batch too - see the 2026-08-01 import
 * incident where a single failure at row ~156 cascaded through the rest of
 * its batch. Each row now gets its own savepoint via NESTED: a failure here
 * rolls back only this row, and the batch's outer transaction (and every
 * other row in it) is unaffected.
 *
 * {@code stationCache}/{@code trainCache} are read here but deliberately
 * never written here - see {@link #saveRow}'s javadoc.
 */
@Service
@RequiredArgsConstructor
public class RailwayImportRowService {

    private final StationRepository stationRepository;
    private final TrainRepository trainRepository;
    private final TrainScheduleRepository trainScheduleRepository;

    /**
     * The station/train ultimately used for this row - not necessarily new,
     * but always the entity actually persisted in this call's transaction
     * (or already-cached and unchanged). Returned so the caller can update
     * {@code stationCache}/{@code trainCache} itself, only after confirming
     * this method returned without throwing.
     */
    public record RowSaveResult(Station station, Train train) {
    }

    /**
     * {@code deleteExistingSchedule} is decided by the caller, and neither
     * {@code stationCache}/{@code trainCache} nor {@code processedTrains}
     * (see {@code RailwayImportBatchService}) are written to here, for the
     * same reason: they're plain in-memory collections, not part of this (or
     * any) transaction. If this method's NESTED transaction rolls back after
     * having inserted a new station/train but before the schedule insert,
     * writing that station/train into the shared cache here would leave a
     * "phantom" entry behind - one whose database row was rolled back, but
     * that a later row could still read from the cache and try to build a
     * foreign key against. The caller only commits cache/bookkeeping writes
     * once this method has returned successfully, keeping them consistent
     * with what's actually durable in the database.
     */
    @Transactional(propagation = Propagation.NESTED)
    public RowSaveResult saveRow(
                    RailwayDataImportService.ParsedRow row,
                    Map<String, Station> stationCache,
                    Map<String, Train> trainCache,
                    boolean deleteExistingSchedule) {

        // Station
        Station station = stationCache.get(row.stationCode());

        if (station == null) {

            station = new Station();
            station.setStationCode(row.stationCode());
            station.setStationName(row.stationName());

            station = stationRepository.save(station);

        } else if (!row.stationName().equals(station.getStationName())) {

            station.setStationName(row.stationName());
            station = stationRepository.save(station);
        }

        // Train
        Train train = trainCache.get(row.trainNo());

        if (train == null) {

            train = new Train();
            train.setTrainNumber(row.trainNo());
            train.setTrainName(row.trainName());

            train = trainRepository.save(train);

        } else if (!row.trainName().equals(train.getTrainName())) {

            train.setTrainName(row.trainName());
            train = trainRepository.save(train);
        }

        if (deleteExistingSchedule) {
            trainScheduleRepository.deleteByTrain(train);
        }

        // Schedule
        TrainSchedule schedule = new TrainSchedule();

        schedule.setTrain(train);
        schedule.setStation(station);

        schedule.setSequenceNo(row.sequenceNo());
        schedule.setArrivalTime(row.arrivalTime());
        schedule.setDepartureTime(row.departureTime());
        schedule.setDistance(row.distance());

        trainScheduleRepository.save(schedule);

        return new RowSaveResult(station, train);
    }
}
