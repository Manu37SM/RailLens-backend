package com.labs.train.train_db.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.labs.train.train_db.model.ImportResult;
import com.labs.train.train_db.service.RailwayDataImportService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class RailwayDataImportController {

    private final RailwayDataImportService importService;

    @PostMapping("/import")
    public ResponseEntity<ImportResult> importData() {

        ImportResult result = importService.importCsv();

        return result.success()
                        ? ResponseEntity.ok(result)
                        : ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }
}