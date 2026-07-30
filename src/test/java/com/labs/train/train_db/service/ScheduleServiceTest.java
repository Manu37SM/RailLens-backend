package com.labs.train.train_db.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.labs.train.train_db.entity.Station;
import com.labs.train.train_db.entity.Train;
import com.labs.train.train_db.entity.TrainSchedule;
import com.labs.train.train_db.exception.ResourceNotFoundException;
import com.labs.train.train_db.model.ScheduleRequest;
import com.labs.train.train_db.model.ScheduleResponse;
import com.labs.train.train_db.repository.StationRepository;
import com.labs.train.train_db.repository.TrainRepository;
import com.labs.train.train_db.repository.TrainScheduleRepository;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

        @Mock
        private TrainRepository trainRepository;

        @Mock
        private StationRepository stationRepository;

        @Mock
        private TrainScheduleRepository trainScheduleRepository;

        private ScheduleService scheduleService() {
                return new ScheduleService(trainRepository, stationRepository, trainScheduleRepository);
        }

        private static ScheduleRequest request(String trainNumber, String stationCode, int sequenceNo) {
                return new ScheduleRequest(
                                trainNumber, stationCode, sequenceNo, LocalTime.of(10, 0), LocalTime.of(10, 5));
        }

        @Test
        void createsAScheduleRowLinkingTheResolvedTrainAndStation() {

                Train train = new Train();
                train.setTrainNumber("12301");
                train.setTrainName("Rajdhani Express");

                Station station = new Station();
                station.setStationCode("NDLS");
                station.setStationName("New Delhi");

                when(trainRepository.findByTrainNumber("12301")).thenReturn(Optional.of(train));
                when(stationRepository.findByStationCode("NDLS")).thenReturn(Optional.of(station));
                when(trainScheduleRepository.save(any(TrainSchedule.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                ScheduleResponse response = scheduleService().createSchedule(request("12301", "NDLS", 3));

                assertThat(response.trainNumber()).isEqualTo("12301");
                assertThat(response.stationCode()).isEqualTo("NDLS");
                assertThat(response.sequenceNo()).isEqualTo(3);
                assertThat(response.arrivalTime()).isEqualTo(LocalTime.of(10, 0));
                assertThat(response.departureTime()).isEqualTo(LocalTime.of(10, 5));
        }

        @Test
        void throwsWhenTheTrainDoesNotExist() {

                when(trainRepository.findByTrainNumber("99999")).thenReturn(Optional.empty());

                assertThatThrownBy(() -> scheduleService().createSchedule(request("99999", "NDLS", 1)))
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("99999");
        }

        @Test
        void throwsWhenTheStationDoesNotExist() {

                Train train = new Train();
                train.setTrainNumber("12301");

                when(trainRepository.findByTrainNumber("12301")).thenReturn(Optional.of(train));
                when(stationRepository.findByStationCode("ZZZZ")).thenReturn(Optional.empty());

                assertThatThrownBy(() -> scheduleService().createSchedule(request("12301", "ZZZZ", 1)))
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("ZZZZ");
        }
}
