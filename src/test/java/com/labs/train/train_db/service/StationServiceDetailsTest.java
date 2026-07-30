package com.labs.train.train_db.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.labs.train.train_db.entity.Station;
import com.labs.train.train_db.entity.Train;
import com.labs.train.train_db.entity.TrainSchedule;
import com.labs.train.train_db.exception.ResourceNotFoundException;
import com.labs.train.train_db.model.CreateStationRequest;
import com.labs.train.train_db.model.StationResponse;
import com.labs.train.train_db.model.StationSearchResponse;
import com.labs.train.train_db.model.StationTrainResponse;
import com.labs.train.train_db.repository.StationRepository;
import com.labs.train.train_db.repository.TrainScheduleRepository;

/**
 * Covers getStation() (StationServiceSearchTest explicitly scopes itself to
 * only search()/fuzzySearch(), same as TrainServiceSearchTest for trains).
 */
@ExtendWith(MockitoExtension.class)
class StationServiceDetailsTest {

        @Mock
        private StationRepository stationRepository;

        @Mock
        private TrainScheduleRepository trainScheduleRepository;

        private StationService stationService() {
                return new StationService(stationRepository, trainScheduleRepository);
        }

        private static Train train(long id, String number, String name) {
                Train train = new Train();
                train.setId(id);
                train.setTrainNumber(number);
                train.setTrainName(name);
                return train;
        }

        private static Station station(String code, String name) {
                Station station = new Station();
                station.setStationCode(code);
                station.setStationName(name);
                return station;
        }

        private static TrainSchedule schedule(
                        Train train, Station station, int sequenceNo, LocalTime arrival, LocalTime departure, Integer distance) {

                TrainSchedule schedule = new TrainSchedule();
                schedule.setTrain(train);
                schedule.setStation(station);
                schedule.setSequenceNo(sequenceNo);
                schedule.setArrivalTime(arrival);
                schedule.setDepartureTime(departure);
                schedule.setDistance(distance);
                return schedule;
        }

        @Test
        void createStationSavesAndReturnsTheMappedResponse() {

                when(stationRepository.save(any(Station.class))).thenAnswer(invocation -> invocation.getArgument(0));

                StationSearchResponse response = stationService().createStation(
                                new CreateStationRequest("NDLS", "New Delhi"));

                assertThat(response.stationCode()).isEqualTo("NDLS");
                assertThat(response.stationName()).isEqualTo("New Delhi");
        }

        @Test
        void throwsWhenTheStationDoesNotExist() {

                when(stationRepository.findByStationCode("ZZZZ")).thenReturn(Optional.empty());

                assertThatThrownBy(() -> stationService().getStation("ZZZZ"))
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("ZZZZ");
        }

        @Test
        void returnsAStationWithNoTrainsWhenNothingStopsThere() {

                Station ndls = station("NDLS", "New Delhi");

                when(stationRepository.findByStationCode("NDLS")).thenReturn(Optional.of(ndls));
                when(trainScheduleRepository.findByStation_StationCodeOrderByArrivalTime("NDLS"))
                                .thenReturn(List.of());

                StationResponse response = stationService().getStation("NDLS");

                assertThat(response.stationCode()).isEqualTo("NDLS");
                assertThat(response.totalTrains()).isZero();
                assertThat(response.trains()).isEmpty();
        }

        @Test
        void marksATrainOriginatingAtThisStationAsOriginNotDestination() {

                Station ndls = station("NDLS", "New Delhi");
                Station agc = station("AGC", "Agra Cantt");

                Train train = train(1L, "12301", "Rajdhani Express");
                TrainSchedule atNdls = schedule(train, ndls, 1, null, LocalTime.of(8, 0), 0);
                TrainSchedule atAgc = schedule(train, agc, 2, LocalTime.of(10, 0), LocalTime.of(10, 5), 200);

                when(stationRepository.findByStationCode("NDLS")).thenReturn(Optional.of(ndls));
                when(trainScheduleRepository.findByStation_StationCodeOrderByArrivalTime("NDLS"))
                                .thenReturn(List.of(atNdls));
                when(trainScheduleRepository.findByTrain_IdInOrderByTrain_IdAscSequenceNoAsc(anyList()))
                                .thenReturn(List.of(atNdls, atAgc));

                StationResponse response = stationService().getStation("NDLS");

                StationTrainResponse result = response.trains().get(0);
                assertThat(result.origin()).isTrue();
                assertThat(result.destination()).isFalse();
        }

        @Test
        void marksATrainTerminatingAtThisStationAsDestinationNotOrigin() {

                Station ndls = station("NDLS", "New Delhi");
                Station agc = station("AGC", "Agra Cantt");

                Train train = train(1L, "12301", "Rajdhani Express");
                TrainSchedule atAgc = schedule(train, agc, 1, null, LocalTime.of(8, 0), 0);
                TrainSchedule atNdls = schedule(train, ndls, 2, LocalTime.of(10, 0), null, 200);

                when(stationRepository.findByStationCode("NDLS")).thenReturn(Optional.of(ndls));
                when(trainScheduleRepository.findByStation_StationCodeOrderByArrivalTime("NDLS"))
                                .thenReturn(List.of(atNdls));
                when(trainScheduleRepository.findByTrain_IdInOrderByTrain_IdAscSequenceNoAsc(anyList()))
                                .thenReturn(List.of(atAgc, atNdls));

                StationResponse response = stationService().getStation("NDLS");

                StationTrainResponse result = response.trains().get(0);
                assertThat(result.origin()).isFalse();
                assertThat(result.destination()).isTrue();
        }

        @Test
        void marksAPassingThroughTrainAsNeitherOriginNorDestination() {

                Station a = station("AAA", "Station A");
                Station ndls = station("NDLS", "New Delhi");
                Station c = station("CCC", "Station C");

                Train train = train(1L, "12301", "Rajdhani Express");
                TrainSchedule atA = schedule(train, a, 1, null, LocalTime.of(6, 0), 0);
                TrainSchedule atNdls = schedule(train, ndls, 2, LocalTime.of(8, 0), LocalTime.of(8, 5), 100);
                TrainSchedule atC = schedule(train, c, 3, LocalTime.of(12, 0), null, 400);

                when(stationRepository.findByStationCode("NDLS")).thenReturn(Optional.of(ndls));
                when(trainScheduleRepository.findByStation_StationCodeOrderByArrivalTime("NDLS"))
                                .thenReturn(List.of(atNdls));
                when(trainScheduleRepository.findByTrain_IdInOrderByTrain_IdAscSequenceNoAsc(anyList()))
                                .thenReturn(List.of(atA, atNdls, atC));

                StationResponse response = stationService().getStation("NDLS");

                StationTrainResponse result = response.trains().get(0);
                assertThat(result.origin()).isFalse();
                assertThat(result.destination()).isFalse();
        }

        @Test
        void resolvesOriginDestinationIndependentlyForMultipleTrainsAtTheSameStation() {

                Station ndls = station("NDLS", "New Delhi");
                Station agc = station("AGC", "Agra Cantt");

                Train originates = train(1L, "1001", "Originates Here");
                TrainSchedule originStop = schedule(originates, ndls, 1, null, LocalTime.of(6, 0), 0);
                TrainSchedule originOtherStop = schedule(originates, agc, 2, LocalTime.of(8, 0), null, 200);

                Train terminates = train(2L, "1002", "Terminates Here");
                TrainSchedule terminatesOtherStop = schedule(terminates, agc, 1, null, LocalTime.of(6, 0), 0);
                TrainSchedule terminatesStop = schedule(terminates, ndls, 2, LocalTime.of(9, 0), null, 200);

                when(stationRepository.findByStationCode("NDLS")).thenReturn(Optional.of(ndls));
                when(trainScheduleRepository.findByStation_StationCodeOrderByArrivalTime("NDLS"))
                                .thenReturn(List.of(originStop, terminatesStop));
                when(trainScheduleRepository.findByTrain_IdInOrderByTrain_IdAscSequenceNoAsc(anyList()))
                                .thenReturn(List.of(
                                                originStop, originOtherStop,
                                                terminatesOtherStop, terminatesStop));

                StationResponse response = stationService().getStation("NDLS");

                assertThat(response.totalTrains()).isEqualTo(2);

                StationTrainResponse originResult = response.trains().stream()
                                .filter(t -> t.trainNumber().equals("1001"))
                                .findFirst()
                                .orElseThrow();
                assertThat(originResult.origin()).isTrue();

                StationTrainResponse terminatesResult = response.trains().stream()
                                .filter(t -> t.trainNumber().equals("1002"))
                                .findFirst()
                                .orElseThrow();
                assertThat(terminatesResult.destination()).isTrue();
        }
}
