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

    /**
     * Paginated to avoid shipping every row in the table in one response
     * (see the RailLens backend review's "unpaginated list endpoint"
     * finding). Defaults to 20 per page, capped implicitly by
     * {@code Pageable}'s own bounds; callers can override with the standard
     * {@code ?page=&size=&sort=} query params.
     */
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

        // Normalized the same way StationController normalizes
        // stationCode - train numbers are numeric today, so this is
        // currently a no-op, but per the backend architecture review's
        // "inconsistent case normalization" finding, some Indian Railways
        // special/international services do use alphanumeric identifiers,
        // and TrainController previously never normalized case anywhere.
        return trainService.getTrainDetails(trainNumber.toUpperCase());
    }

    /**
     * "Train Intelligence" scores (FEATURE.md) - route complexity,
     * uniqueness, expressness, night/day travel split, longest non-stop
     * segment, average halt duration, journey efficiency, and possibly-
     * skipped stations. See TrainIntelligenceService for how each is
     * derived and, where relevant, the documented judgment call behind it.
     * Deliberately a separate endpoint from getTrainDetails rather than
     * folding these fields into TrainDetailsResponse - this one additionally
     * depends on the network-wide graph snapshot (RailwayNetworkService),
     * so it's a heavier call that a caller who only wants the schedule
     * shouldn't be forced to pay for.
     */
    @GetMapping("/{trainNumber}/intelligence")
    public TrainIntelligenceResponse getTrainIntelligence(
            @PathVariable @NotBlank String trainNumber) {

        return trainIntelligenceService.getIntelligence(trainNumber.toUpperCase());
    }

    /**
     * "Route Analytics" (FEATURE.md) - overlap, longest common section,
     * divergence/convergence points, and reverse-route detection between
     * two trains. See RouteAnalyticsService for how each field is derived.
     */
    @GetMapping("/{trainNumber}/compare/{otherTrainNumber}")
    public RouteComparisonResponse compareRoutes(
            @PathVariable @NotBlank String trainNumber,
            @PathVariable @NotBlank String otherTrainNumber) {

        return routeAnalyticsService.compareRoutes(
                trainNumber.toUpperCase(), otherTrainNumber.toUpperCase());
    }
}
