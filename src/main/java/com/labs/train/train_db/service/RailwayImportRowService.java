package com.labs.train.train_db.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.labs.train.train_db.entity.Station;
import com.labs.train.train_db.entity.Train;
import com.labs.train.train_db.entity.TrainSchedule;
import com.labs.train.train_db.repository.StationRepository;
import com.labs.train.train_db.repository.TrainRepository;
import com.labs.train.train_db.repository.TrainScheduleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Persists exactly one already-parsed CSV row. Deliberately NOT {@code
 * @Transactional} - this used to run in its own {@code Propagation.NESTED}
 * transaction, then its own {@code Propagation.REQUIRES_NEW} transaction,
 * see {@link RailwayImportBatchService}'s javadoc for why both were
 * abandoned. This method now just does the actual persistence work and
 * runs inside whatever transaction is already active on the calling
 * thread - {@code RailwayImportBatchService} owns the transaction, the
 * per-row failure isolation (a raw JDBC savepoint), and the decision of
 * when it's safe to write to the shared caches below.
 *
 * Still its own class rather than a private method on {@code
 * RailwayImportBatchService} - not for the self-invocation/{@code
 * @Transactional} reason that used to apply (this method isn't
 * transactional anymore), just because "how to persist one row" and "how
 * to loop over a batch and manage its transaction/savepoints" are
 * different enough concerns to keep separate.
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
     * but always the entity actually persisted in this call. Returned so
     * the caller can update {@code stationCache}/{@code trainCache} itself,
     * only after confirming both this method returned without throwing AND
     * the caller's own {@code entityManager.flush()} afterward succeeded.
     */
    public record RowSaveResult(Station station, Train train) {
    }

    /**
     * {@code deleteExistingSchedule} is decided by the caller, and neither
     * {@code stationCache}/{@code trainCache} nor {@code processedTrains}
     * (see {@code RailwayImportBatchService}) are written to here, for the
     * same reason as before: they're plain in-memory collections, not part
     * of any transaction. If the caller rolls back this row's work (via its
     * savepoint) after this method returns, writing a new station/train
     * into the shared cache here would leave a "phantom" entry behind - one
     * whose database row was rolled back, but that a later row could still
     * read from the cache and try to build a foreign key against. The
     * caller only commits cache/bookkeeping writes once this row's flush
     * has actually succeeded, keeping them consistent with what's durable.
     */
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
