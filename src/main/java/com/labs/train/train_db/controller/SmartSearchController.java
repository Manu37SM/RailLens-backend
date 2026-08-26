package com.labs.train.train_db.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.labs.train.train_db.model.SmartSearchResponse;
import com.labs.train.train_db.service.SmartSearchService;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Validated
public class SmartSearchController {

        private final SmartSearchService smartSearchService;

        @GetMapping("/smart")
        public SmartSearchResponse search(
                        @RequestParam @NotBlank @Size(max = 200) String q) {

                return smartSearchService.search(q);
        }
}
