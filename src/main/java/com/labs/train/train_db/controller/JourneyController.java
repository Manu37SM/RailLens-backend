package com.labs.train.train_db.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.labs.train.train_db.model.JourneySearchResponse;
import com.labs.train.train_db.service.JourneyService;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/journeys")
@RequiredArgsConstructor
@Validated
public class JourneyController {

    private final JourneyService journeyService;

    @GetMapping
    public JourneySearchResponse search(
            @RequestParam @NotBlank @Size(max = 20) String from,
            @RequestParam @NotBlank @Size(max = 20) String to) {

        return journeyService.search(from, to);
    }
}