package com.labs.train.train_db.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

import com.labs.train.train_db.model.StationSearchResponse;
import com.labs.train.train_db.repository.StationRepository;
import com.labs.train.train_db.repository.TrainScheduleRepository;

/**
 * Covers only the searchStations()/fuzzySearch() path added alongside the
 * fuzzy-search fallback - mirrors TrainServiceSearchTest.
 */
@ExtendWith(MockitoExtension.class)
class StationServiceSearchTest {

    @Mock
    private StationRepository stationRepository;

    @Mock
    private TrainScheduleRepository trainScheduleRepository;

    private StationService stationService() {
        return new StationService(stationRepository, trainScheduleRepository);
    }

    @Test
    void returnsExactMatchesWithoutConsultingFuzzyIndex() {

        org.springframework.data.domain.PageImpl<com.labs.train.train_db.entity.Station> page =
                new org.springframework.data.domain.PageImpl<>(List.of(station("NDLS", "New Delhi")));
        when(stationRepository.search(org.mockito.ArgumentMatchers.eq("Delhi"), any())).thenReturn(page);

        List<StationSearchResponse> result = stationService().searchStations("Delhi");

        assertThat(result).containsExactly(new StationSearchResponse("NDLS", "New Delhi"));
        verify(stationRepository, never()).findAllSearchKeys();
    }

    @Test
    void fallsBackToFuzzyMatchWhenExactSearchFindsNothing() {

        when(stationRepository.search(org.mockito.ArgumentMatchers.eq("Delhy"), any()))
                .thenReturn(Page.empty());

        when(stationRepository.findAllSearchKeys()).thenReturn(List.of(
                new StationSearchResponse("NDLS", "New Delhi"), // "delhi" is 1 edit from "delhy"
                new StationSearchResponse("BCT", "Mumbai Central")));

        List<StationSearchResponse> result = stationService().searchStations("Delhy");

        assertThat(result).containsExactly(new StationSearchResponse("NDLS", "New Delhi"));
    }

    private static com.labs.train.train_db.entity.Station station(String code, String name) {
        com.labs.train.train_db.entity.Station station = new com.labs.train.train_db.entity.Station();
        station.setStationCode(code);
        station.setStationName(name);
        return station;
    }
}
