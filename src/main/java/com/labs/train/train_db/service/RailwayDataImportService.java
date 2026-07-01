package com.labs.train.train_db.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.labs.train.train_db.repository.StationRepository;
import com.labs.train.train_db.repository.TrainRepository;
import com.labs.train.train_db.repository.TrainScheduleRepository;

import com.labs.train.train_db.entity.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RailwayDataImportService {

    private final StationRepository stationRepository;
    private final TrainRepository trainRepository;
    private final TrainScheduleRepository trainScheduleRepository;

    public void importCsv() {

        Map<String, Station> stationCache = new HashMap<>();
        Map<String, Train> trainCache = new HashMap<>();

        try {

            log.info("Starting railway data import...");

            if (trainScheduleRepository.count() > 0) {
                System.out.println("Data already imported");
                return;
            }

            stationRepository.findAll()
                    .forEach(station -> stationCache.put(
                            station.getStationCode(),
                            station));

            trainRepository.findAll()
                    .forEach(train -> trainCache.put(
                            train.getTrainNumber(),
                            train));

            ClassPathResource resource = new ClassPathResource("data/train_dataset.csv");

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream()));

            // Skip header
            reader.readLine();

            String line;

            int count = 0;

            while ((line = reader.readLine()) != null) {

                try {
                    String[] cols = line.split(",", -1);

                    if (cols.length < 8) {
                        continue;
                    }

                    String trainNo = cols[0];
                    String trainName = cols[1];

                    if ("NA".equalsIgnoreCase(cols[5]) ||
                            "NA".equalsIgnoreCase(cols[6]) ||
                            "NA".equalsIgnoreCase(cols[7])) {

                        System.out.println("Skipping train: " + trainNo);

                        continue;
                    }

                    Integer sequenceNo = Integer.parseInt(cols[2]);

                    String stationCode = cols[3];
                    String stationName = cols[4];

                    LocalTime arrivalTime = parseTime(cols[5]);
                    LocalTime departureTime = parseTime(cols[6]);

                    Integer distance = Integer.parseInt(cols[7]);

                    // Station
                    Station station = stationCache.get(stationCode);

                    if (station == null) {

                        station = new Station();

                        station.setStationCode(stationCode);
                        station.setStationName(stationName);

                        station = stationRepository.save(station);

                        stationCache.put(stationCode, station);
                    }

                    // Train
                    Train train = trainCache.get(trainNo);

                    if (train == null) {

                        train = new Train();

                        train.setTrainNumber(trainNo);
                        train.setTrainName(trainName);

                        train = trainRepository.save(train);

                        trainCache.put(trainNo, train);
                    }

                    // Schedule
                    TrainSchedule schedule = new TrainSchedule();

                    schedule.setTrain(train);
                    schedule.setStation(station);

                    schedule.setSequenceNo(sequenceNo);
                    schedule.setArrivalTime(arrivalTime);
                    schedule.setDepartureTime(departureTime);
                    schedule.setDistance(distance);

                    trainScheduleRepository.save(schedule);

                    count++;

                    if (count % 10000 == 0) {
                        System.out.println("Imported: " + count);
                    }
                } catch (Exception ex) {

                    System.out.println("Skipping row:");
                    System.out.println(line);
                }

            }

            reader.close();

            System.out.println("Imported rows: " + count);

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    private LocalTime parseTime(String value) {

        if (value == null ||
                value.isBlank() ||
                value.equalsIgnoreCase("NA")) {
            return null;
        }

        try {
            return LocalTime.parse(value);
        } catch (Exception e) {
            System.out.println("Invalid time: " + value);
            return null;
        }
    }
}