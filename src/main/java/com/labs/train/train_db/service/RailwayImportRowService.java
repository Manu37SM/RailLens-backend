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

@Service
@RequiredArgsConstructor
public class RailwayImportRowService {

    private final StationRepository stationRepository;
    private final TrainRepository trainRepository;
    private final TrainScheduleRepository trainScheduleRepository;

    public record RowSaveResult(Station station, Train train) {
    }

    public RowSaveResult saveRow(
                    RailwayDataImportService.ParsedRow row,
                    Map<String, Station> stationCache,
                    Map<String, Train> trainCache,
                    boolean deleteExistingSchedule) {

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
