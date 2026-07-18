package com.labs.train.train_db.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.labs.train.train_db.repository.StationRepository;
import com.labs.train.train_db.repository.TrainRepository;
import com.labs.train.train_db.repository.TrainScheduleRepository;

import com.labs.train.train_db.entity.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RailwayDataImportService {

    private final StationRepository stationRepository;
    private final TrainRepository trainRepository;
    private final TrainScheduleRepository trainScheduleRepository;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("H:mm");

    public void importCsv() {

        Map<String, Station> stationCache = new HashMap<>();
        Map<String, Train> trainCache = new HashMap<>();
        Set<String> processedTrains = new HashSet<>();

        try {

            log.info("Starting railway data import...");

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

            CSVParser parser = CSVFormat.DEFAULT
                    .builder()
                    .setHeader(
                            "Train No",
                            "Train Name",
                            "SEQ",
                            "Station Code",
                            "Station Name",
                            "Arrival Time",
                            "Departure Time",
                            "Distance",
                            "Source Station",
                            "Source Station Name",
                            "Destination Station",
                            "Destination Station Name")
                    .setSkipHeaderRecord(true)
                    .get()
                    .parse(reader);

            int count = 0;

            for (CSVRecord record : parser) {

                try {

                    String trainNo = cleanText(record.get("Train No"));
                    String trainName = cleanText(record.get("Train Name"));

                    Integer sequenceNo = Integer.parseInt(cleanText(record.get("SEQ")));

                    String stationCode = cleanText(record.get("Station Code"));
                    String stationName = cleanText(record.get("Station Name"));

                    LocalTime arrivalTime = parseTime(cleanText(record.get("Arrival Time")));
                    LocalTime departureTime = parseTime(cleanText(record.get("Departure Time")));

                    Integer distance = parseInteger(cleanText(record.get("Distance")));

                    // Station
                    Station station = stationCache.get(stationCode);

                    if (station == null) {

                        station = new Station();
                        station.setStationCode(stationCode);
                        station.setStationName(stationName);

                        station = stationRepository.save(station);

                        stationCache.put(stationCode, station);

                    } else {

                        // Keep station names up-to-date
                        if (!stationName.equals(station.getStationName())) {
                            station.setStationName(stationName);
                            station = stationRepository.save(station);
                            stationCache.put(stationCode, station);
                        }
                    }

                    // Train
                    Train train = trainCache.get(trainNo);

                    if (train == null) {

                        train = new Train();
                        train.setTrainNumber(trainNo);
                        train.setTrainName(trainName);

                        train = trainRepository.save(train);

                        trainCache.put(trainNo, train);

                    } else {

                        if (!trainName.equals(train.getTrainName())) {
                            train.setTrainName(trainName);
                            train = trainRepository.save(train);
                            trainCache.put(trainNo, train);
                        }
                    }

                    // Delete schedule only for existing trains, only once
                    if (processedTrains.add(trainNo)) {
                        trainScheduleRepository.deleteByTrain(train);
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
                        log.info("Imported {} rows", count);
                    }
                } catch (Exception ex) {
                    log.error("Failed to import row: {}", record.toString(), ex);
                }

            }

            reader.close();

            log.info("Imported {} rows successfully", count);

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    private LocalTime parseTime(String value) {

        if (value == null || value.isBlank() || value.equalsIgnoreCase("NA")) {
            return null;
        }

        try {
            return LocalTime.parse(value, TIME_FORMATTER);
        } catch (Exception e) {
            log.warn("Invalid time: {}", value);
            return null;
        }
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("NA")) {
            return null;
        }
        return Integer.parseInt(value);
    }

    private String cleanText(String value) {

        if (value == null) {
            return null;
        }

        value = value.trim();

        if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }

        while (value.endsWith(",")) {
            value = value.substring(0, value.length() - 1).trim();
        }

        return value;
    }
}