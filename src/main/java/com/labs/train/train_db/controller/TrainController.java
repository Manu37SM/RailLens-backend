package com.labs.train.train_db.controller;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.labs.train.train_db.entity.Train;
import com.labs.train.train_db.model.TrainDetailsResponse;
import com.labs.train.train_db.model.TrainSearchResponse;
import com.labs.train.train_db.repository.TrainRepository;
import com.labs.train.train_db.service.TrainService;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/trains")
@RequiredArgsConstructor
@Validated
public class TrainController {

    private final TrainRepository trainRepository;
    private final TrainService trainService;

    @PostMapping
    public Train createTrain(
            @RequestBody Train train) {

        return trainRepository.save(train);
    }

    @GetMapping
    public List<Train> getAllTrains() {

        return trainRepository.findAll();
    }

    @GetMapping("/search")
    public List<TrainSearchResponse> search(
            @RequestParam @NotBlank @Size(max = 100) String q) {

        return trainService.search(q);
    }

    @GetMapping("/{trainNumber}")
    public TrainDetailsResponse getTrainDetails(
            @PathVariable @NotBlank String trainNumber) {

        return trainService.getTrainDetails(trainNumber);
    }
}
