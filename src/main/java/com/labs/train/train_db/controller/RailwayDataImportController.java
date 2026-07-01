package com.labs.train.train_db.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.labs.train.train_db.service.RailwayDataImportService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class RailwayDataImportController {

    private final RailwayDataImportService importService;

    @PostMapping("/import")
    public String importData() {

        importService.importCsv();

        return "Import Started";
    }
}