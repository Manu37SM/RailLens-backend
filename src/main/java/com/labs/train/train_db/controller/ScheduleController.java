package com.labs.train.train_db.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.labs.train.train_db.model.ScheduleRequest;
import com.labs.train.train_db.model.ScheduleResponse;
import com.labs.train.train_db.service.ScheduleService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
@Validated
public class ScheduleController {

        private final ScheduleService scheduleService;

        @PostMapping
        public ScheduleResponse createSchedule(
                        @RequestBody @Valid ScheduleRequest request) {

                return scheduleService.createSchedule(request);
        }
}
