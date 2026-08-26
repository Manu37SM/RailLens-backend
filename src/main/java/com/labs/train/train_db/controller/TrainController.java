package com.labs.train.train_db.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.labs.train.train_db.model.CreateTrainRequest;
import com.labs.train.train_db.model.RouteComparisonResponse;
import com.labs.train.train_db.model.TrainDetailsResponse;
import com.labs.train.train_db.model.TrainIntelligenceResponse;
import com.labs.train.train_db.model.TrainSearchResponse;
import com.labs.train.train_db.service.RouteAnalyticsService;
import com.labs.train.train_db.service.TrainIntelligenceService;
import com.labs.train.train_db.service.TrainService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/trains")
@RequiredArgsConstructor
@Validated
public class TrainController {

    private final TrainService trainService;
    private final TrainIntelligenceService trainIntelligenceService;
    private final RouteAnalyticsService routeAnalyticsService;

    @PostMapping
    public TrainSearchResponse createTrain(
            @RequestBody @Valid CreateTrainRequest request) {

        return trainService.createTrain(request);
    }

    @GetMapping
    public Page<TrainSearchResponse> getAllTrains(
            @PageableDefault(size = 20) Pageable pageable) {

        return trainService.getAllTrains(pageable);
    }

    @GetMapping("/search")
    public List<TrainSearchResponse> search(
            @RequestParam @NotBlank @Size(max = 100) String q) {

        return trainService.search(q);
    }

    @GetMapping("/{trainNumber}")
    public TrainDetailsResponse getTrainDetails(
            @PathVariable @NotBlank String trainNumber) {

        return trainService.getTrainDetails(trainNumber.toUpperCase());
    }

    @GetMapping("/{trainNumber}/intelligence")
    public TrainIntelligenceResponse getTrainIntelligence(
            @PathVariable @NotBlank String trainNumber) {

        return trainIntelligenceService.getIntelligence(trainNumber.toUpperCase());
    }

    @GetMapping("/{trainNumber}/compare/{otherTrainNumber}")
    public RouteComparisonResponse compareRoutes(
            @PathVariable @NotBlank String trainNumber,
            @PathVariable @NotBlank String otherTrainNumber) {

        return routeAnalyticsService.compareRoutes(
                trainNumber.toUpperCase(), otherTrainNumber.toUpperCase());
    }
}
