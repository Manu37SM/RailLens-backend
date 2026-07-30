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

/**
 * "Smart Search" (FEATURE.md) - structured, natural-language-ish train
 * queries. See SmartSearchQueryParser for the fixed grammar this
 * understands and SmartSearchResponse for how an unrecognized query is
 * reported (200 with {@code recognized: false}, not a 4xx - an
 * unparseable query is an expected outcome for a free-text field, not a
 * client error).
 */
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
