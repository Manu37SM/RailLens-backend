package com.labs.train.train_db.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.labs.train.train_db.entity.Station;
import com.labs.train.train_db.entity.Train;
import com.labs.train.train_db.entity.TrainSchedule;
import com.labs.train.train_db.model.RouteDistanceProjection;
import com.labs.train.train_db.model.StationTrafficProjection;
import com.labs.train.train_db.model.StatsResponse;
import com.labs.train.train_db.repository.StationRepository;
import com.labs.train.train_db.repository.TrainRepository;
import com.labs.train.train_db.repository.TrainScheduleRepository;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

        @Mock
        private TrainRepository trainRepository;

        @Mock
        private StationRepository stationRepository;

        @Mock
        private TrainScheduleRepository trainScheduleRepository;

        @Mock
        private ScheduleSnapshotService scheduleSnapshotService;

        // Stateless, no dependencies of its own - safe to use the real thing
        // rather than mocking every call (see its own class javadoc).
        private final JourneyDayCalculator journeyDayCalculator = new JourneyDayCalculator();

        private StatsService statsService() {
                return new StatsService(
                                trainRepository, stationRepository, trainScheduleRepository,
                                journeyDayCalculator, scheduleSnapshotService);
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

        @Test
        void returnsCountsAndTopResultsWhenDataExists() {

                when(trainRepository.count()).thenReturn(8000L);
                when(stationRepository.count()).thenReturn(7000L);

                RouteDistanceProjection longest = new RouteDistanceProjection("12345", "Long Express", 3000);
                RouteDistanceProjection shortest = new RouteDistanceProjection("54321", "Short Passenger", 15);
                StationTrafficProjection busiest = new StationTrafficProjection("NDLS", "New Delhi", 500L);

                when(trainScheduleRepository.findRouteDistancesDescending(any())).thenReturn(List.of(longest));
                when(trainScheduleRepository.findRouteDistancesAscending(any())).thenReturn(List.of(shortest));
                when(trainScheduleRepository.findBusiestStations(any())).thenReturn(List.of(busiest));
                when(scheduleSnapshotService.getAllOrderedByTrainThenSequence()).thenReturn(List.of());

                StatsResponse response = statsService().getStats();

                assertThat(response.totalTrains()).isEqualTo(8000L);
                assertThat(response.totalStations()).isEqualTo(7000L);
                assertThat(response.longestRoute()).isEqualTo(longest);
                assertThat(response.shortestRoute()).isEqualTo(shortest);
                assertThat(response.busiestStation()).isEqualTo(busiest);
                assertThat(response.busiestStations()).containsExactly(busiest);
                assertThat(response.fastestTrains()).isEmpty();
                assertThat(response.slowestTrains()).isEmpty();
        }

        @Test
        void returnsNullAggregatesRatherThanThrowingWhenDatabaseIsEmpty() {

                when(trainRepository.count()).thenReturn(0L);
                when(stationRepository.count()).thenReturn(0L);
                when(trainScheduleRepository.findRouteDistancesDescending(any())).thenReturn(List.of());
                when(trainScheduleRepository.findRouteDistancesAscending(any())).thenReturn(List.of());
                when(trainScheduleRepository.findBusiestStations(any())).thenReturn(List.of());
                when(scheduleSnapshotService.getAllOrderedByTrainThenSequence()).thenReturn(List.of());

                StatsResponse response = statsService().getStats();

                assertThat(response.longestRoute()).isNull();
                assertThat(response.shortestRoute()).isNull();
                assertThat(response.busiestStation()).isNull();
                assertThat(response.busiestStations()).isEmpty();
                assertThat(response.fastestTrains()).isEmpty();
                assertThat(response.slowestTrains()).isEmpty();
        }

        @Test
        void ranksTrainsByAverageSpeedAndSkipsIncompleteRoutes() {

                when(trainRepository.count()).thenReturn(3L);
                when(stationRepository.count()).thenReturn(2L);
                when(trainScheduleRepository.findRouteDistancesDescending(any())).thenReturn(List.of());
                when(trainScheduleRepository.findRouteDistancesAscending(any())).thenReturn(List.of());
                when(trainScheduleRepository.findBusiestStations(any())).thenReturn(List.of());

                Station a = station("AAA", "Station A");
                Station b = station("BBB", "Station B");

                // Fast: 120 km in 1h -> 120 km/h.
                Train fast = train(1L, "1001", "Fast Express");
                TrainSchedule fastStart = schedule(fast, a, 1, null, LocalTime.of(10, 0), 0);
                TrainSchedule fastEnd = schedule(fast, b, 2, LocalTime.of(11, 0), null, 120);

                // Slow: 60 km in 2h -> 30 km/h.
                Train slow = train(2L, "1002", "Slow Passenger");
                TrainSchedule slowStart = schedule(slow, a, 1, null, LocalTime.of(9, 0), 0);
                TrainSchedule slowEnd = schedule(slow, b, 2, LocalTime.of(11, 0), null, 60);

                // Incomplete: missing arrival time at the last stop - must be
                // skipped rather than crashing or being counted as 0 km/h.
                Train incomplete = train(3L, "1003", "Incomplete Train");
                TrainSchedule incompleteStart = schedule(incomplete, a, 1, null, LocalTime.of(9, 0), 0);
                TrainSchedule incompleteEnd = schedule(incomplete, b, 2, null, null, 60);

                when(scheduleSnapshotService.getAllOrderedByTrainThenSequence())
                                .thenReturn(List.of(
                                                fastStart, fastEnd,
                                                slowStart, slowEnd,
                                                incompleteStart, incompleteEnd));

                StatsResponse response = statsService().getStats();

                assertThat(response.fastestTrains()).hasSize(2);
                assertThat(response.fastestTrains().get(0).trainNumber()).isEqualTo("1001");
                assertThat(response.fastestTrains().get(0).averageSpeedKmh()).isEqualTo(120.0);

                assertThat(response.slowestTrains().get(0).trainNumber()).isEqualTo("1002");
                assertThat(response.slowestTrains().get(0).averageSpeedKmh()).isEqualTo(30.0);
        }
}
