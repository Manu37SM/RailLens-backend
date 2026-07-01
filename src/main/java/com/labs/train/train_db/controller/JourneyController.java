package com.labs.train.train_db.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.labs.train.train_db.model.JourneySearchResponse;
import com.labs.train.train_db.service.JourneyService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class JourneyController {

    private final JourneyService journeyService;

    @GetMapping("/api/journeys")
    public JourneySearchResponse search(
            @RequestParam String from,
            @RequestParam String to) {

        return journeyService.search(from, to);
    }
}