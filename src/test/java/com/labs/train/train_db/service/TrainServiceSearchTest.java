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
import org.springframework.data.domain.PageImpl;

import com.labs.train.train_db.entity.Train;
import com.labs.train.train_db.model.TrainSearchResponse;
import com.labs.train.train_db.repository.TrainRepository;
import com.labs.train.train_db.repository.TrainScheduleRepository;

@ExtendWith(MockitoExtension.class)
class TrainServiceSearchTest {

    @Mock
    private TrainRepository trainRepository;

    @Mock
    private TrainScheduleRepository trainScheduleRepository;

    private final JourneyDayCalculator journeyDayCalculator = new JourneyDayCalculator();

    private TrainService trainService() {
        return new TrainService(trainRepository, trainScheduleRepository, journeyDayCalculator);
    }

    private static Train train(String number, String name) {
        Train train = new Train();
        train.setTrainNumber(number);
        train.setTrainName(name);
        return train;
    }

    @Test
    void returnsExactMatchesWithoutConsultingFuzzyIndex() {

        Page<Train> page = new PageImpl<>(List.of(train("12301", "Rajdhani Express")));
        when(trainRepository.search(org.mockito.ArgumentMatchers.eq("Rajdhani"), any())).thenReturn(page);

        List<TrainSearchResponse> result = trainService().search("Rajdhani");

        assertThat(result).containsExactly(new TrainSearchResponse("12301", "Rajdhani Express"));
        verify(trainRepository, never()).findAllSearchKeys();
    }

    @Test
    void fallsBackToFuzzyMatchWhenExactSearchFindsNothing() {

        when(trainRepository.search(org.mockito.ArgumentMatchers.eq("Rajdani"), any()))
                .thenReturn(Page.empty());

        when(trainRepository.findAllSearchKeys()).thenReturn(List.of(
                new TrainSearchResponse("12301", "Rajdhani Express"),
                new TrainSearchResponse("55555", "Local Passenger")));

        List<TrainSearchResponse> result = trainService().search("Rajdani");

        assertThat(result).containsExactly(new TrainSearchResponse("12301", "Rajdhani Express"));
    }

    @Test
    void returnsEmptyWhenNothingIsCloseEnough() {

        when(trainRepository.search(org.mockito.ArgumentMatchers.eq("zzz"), any()))
                .thenReturn(Page.empty());

        when(trainRepository.findAllSearchKeys()).thenReturn(List.of(
                new TrainSearchResponse("12301", "Rajdhani Express")));

        List<TrainSearchResponse> result = trainService().search("zzz");

        assertThat(result).isEmpty();
    }

    @Test
    void blankQueryReturnsEmptyWithoutTouchingRepository() {

        List<TrainSearchResponse> result = trainService().search("   ");

        assertThat(result).isEmpty();
        verify(trainRepository, never()).search(any(), any());
        verify(trainRepository, never()).findAllSearchKeys();
    }
}
