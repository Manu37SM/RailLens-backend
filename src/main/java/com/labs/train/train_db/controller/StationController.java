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

import com.labs.train.train_db.model.CreateStationRequest;
import com.labs.train.train_db.model.StationResponse;
import com.labs.train.train_db.model.StationSearchResponse;
import com.labs.train.train_db.service.StationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/stations")
@RequiredArgsConstructor
@Validated
public class StationController {

    private final StationService stationService;

    @PostMapping
    public StationSearchResponse createStation(
            @RequestBody @Valid CreateStationRequest request) {

        return stationService.createStation(request);
    }

    /**
     * Paginated for the same reason as {@code TrainController#getAllTrains}.
     */
    @GetMapping
    public Page<StationSearchResponse> getAllStations(
            @PageableDefault(size = 20) Pageable pageable) {

        return stationService.getAllStations(pageable);
    }

    @GetMapping("/search")
    public List<StationSearchResponse> searchStations(
            @RequestParam @NotBlank @Size(max = 100) String q) {

        return stationService.searchStations(q);
    }

    @GetMapping("/{stationCode}")
    public StationResponse getStation(
            @PathVariable @NotBlank String stationCode) {

        return stationService.getStation(
                stationCode.toUpperCase());
    }
}
