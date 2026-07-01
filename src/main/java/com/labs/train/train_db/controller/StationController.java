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

import com.labs.train.train_db.entity.Station;
import com.labs.train.train_db.model.StationResponse;
import com.labs.train.train_db.model.StationSearchResponse;
import com.labs.train.train_db.repository.StationRepository;
import com.labs.train.train_db.service.StationService;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/stations")
@RequiredArgsConstructor
@Validated
public class StationController {

    private final StationRepository stationRepository;
    private final StationService stationService;

    @PostMapping
    public Station createStation(
            @RequestBody Station station) {

        return stationRepository.save(station);
    }

    @GetMapping
    public List<Station> getAllStations() {

        return stationRepository.findAll();
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