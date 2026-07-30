package com.labs.train.train_db.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
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
import com.labs.train.train_db.model.JourneySearchResponse;
import com.labs.train.train_db.model.JourneyTrainResponse;
import com.labs.train.train_db.repository.TrainScheduleRepository;

@ExtendWith(MockitoExtension.class)
class JourneyServiceTest {

        @Mock
        private TrainScheduleRepository trainScheduleRepository;

        // Stateless, no dependencies of its own - same rationale as
        // StatsServiceTest for using the real instance rather than mocking it.
        private final JourneyDayCalculator journeyDayCalculator = new JourneyDayCalculator();

        private JourneyService journeyService() {
                return new JourneyService(trainScheduleRepository, journeyDayCalculator);
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
        void returnsEmptyResultWhenNoTrainConnectsTheTwoStations() {

                when(trainScheduleRepository.findByStation_StationCode("NDLS")).thenReturn(List.of());
                when(trainScheduleRepository.findByStation_StationCode("HWH")).thenReturn(List.of());

                JourneySearchResponse response = journeyService().search("NDLS", "HWH");

                assertThat(response.totalTrains()).isZero();
                assertThat(response.trains()).isEmpty();
        }

        @Test
        void upperCasesAndTrimsStationCodesBeforeQuerying() {

                when(trainScheduleRepository.findByStation_StationCode("NDLS")).thenReturn(List.of());
                when(trainScheduleRepository.findByStation_StationCode("HWH")).thenReturn(List.of());

                JourneySearchResponse response = journeyService().search("  ndls ", " hwh  ");

                assertThat(response.from()).isEqualTo("NDLS");
                assertThat(response.to()).isEqualTo("HWH");
        }

        @Test
        void excludesTrainsWhereDestinationComesBeforeSourceInSequence() {

                Station ndls = station("NDLS", "New Delhi");
                Station hwh = station("HWH", "Howrah Jn");

                // A train that passes HWH before NDLS on its route doesn't
                // actually go NDLS -> HWH for this passenger, even though both
                // stops exist on it.
                Train backwards = train(1L, "1001", "Backwards Express");
                TrainSchedule sourceStop = schedule(backwards, ndls, 5, LocalTime.of(10, 0), LocalTime.of(10, 5), 500);
                TrainSchedule destinationStop = schedule(backwards, hwh, 2, LocalTime.of(6, 0), LocalTime.of(6, 5), 100);

                when(trainScheduleRepository.findByStation_StationCode("NDLS")).thenReturn(List.of(sourceStop));
                when(trainScheduleRepository.findByStation_StationCode("HWH")).thenReturn(List.of(destinationStop));

                JourneySearchResponse response = journeyService().search("NDLS", "HWH");

                assertThat(response.trains()).isEmpty();
        }

        @Test
        void excludesTrainsMissingDistanceAtEitherStop() {

                Station ndls = station("NDLS", "New Delhi");
                Station hwh = station("HWH", "Howrah Jn");

                Train noDistanceTrain = train(1L, "1001", "No Distance Express");
                TrainSchedule sourceStop = schedule(noDistanceTrain, ndls, 1, null, LocalTime.of(10, 0), null);
                TrainSchedule destinationStop = schedule(noDistanceTrain, hwh, 2, LocalTime.of(20, 0), null, 500);

                when(trainScheduleRepository.findByStation_StationCode("NDLS")).thenReturn(List.of(sourceStop));
                when(trainScheduleRepository.findByStation_StationCode("HWH")).thenReturn(List.of(destinationStop));

                JourneySearchResponse response = journeyService().search("NDLS", "HWH");

                assertThat(response.trains()).isEmpty();
        }

        @Test
        void sortsMatchingTrainsByDurationAscendingAndComputesDistance() {

                Station ndls = station("NDLS", "New Delhi");
                Station hwh = station("HWH", "Howrah Jn");

                // Fast train: 08:00 -> 16:00, same day = 8h.
                Train fast = train(1L, "1001", "Fast Express");
                TrainSchedule fastSource = schedule(fast, ndls, 1, null, LocalTime.of(8, 0), 0);
                TrainSchedule fastDestination = schedule(fast, hwh, 2, LocalTime.of(16, 0), null, 1400);

                // Slow train: 08:00 -> 23:00, same day = 15h.
                Train slow = train(2L, "1002", "Slow Passenger");
                TrainSchedule slowSource = schedule(slow, ndls, 1, null, LocalTime.of(8, 0), 0);
                TrainSchedule slowDestination = schedule(slow, hwh, 2, LocalTime.of(23, 0), null, 1450);

                when(trainScheduleRepository.findByStation_StationCode("NDLS"))
                                .thenReturn(List.of(fastSource, slowSource));
                when(trainScheduleRepository.findByStation_StationCode("HWH"))
                                .thenReturn(List.of(fastDestination, slowDestination));
                when(trainScheduleRepository.findByTrain_IdInOrderByTrain_IdAscSequenceNoAsc(anyList()))
                                .thenReturn(List.of(fastSource, fastDestination, slowSource, slowDestination));

                JourneySearchResponse response = journeyService().search("NDLS", "HWH");

                assertThat(response.totalTrains()).isEqualTo(2);

                List<JourneyTrainResponse> trains = response.trains();
                assertThat(trains.get(0).trainNumber()).isEqualTo("1001");
                assertThat(trains.get(0).duration()).isEqualTo("8h 00m");
                assertThat(trains.get(0).distance()).isEqualTo(1400);

                // Direct (no intermediate stops): all 8h counts as moving, no
                // halts, and 08:00-16:00 never touches the 21:00-06:00 night
                // window.
                assertThat(trains.get(0).numHalts()).isZero();
                assertThat(trains.get(0).movingMinutes()).isEqualTo(480);
                assertThat(trains.get(0).haltedMinutes()).isZero();
                assertThat(trains.get(0).longestHaltMinutes()).isNull();
                assertThat(trains.get(0).averageMovingSpeedKmh()).isEqualTo(175.0);
                assertThat(trains.get(0).nightTravelPercent()).isEqualTo(0.0);
                assertThat(trains.get(0).dayTravelPercent()).isEqualTo(100.0);

                assertThat(trains.get(1).trainNumber()).isEqualTo("1002");
                assertThat(trains.get(1).duration()).isEqualTo("15h 00m");
        }

        @Test
        void sortsTrainsWithUnknownDurationLast() {

                Station ndls = station("NDLS", "New Delhi");
                Station hwh = station("HWH", "Howrah Jn");

                Train known = train(1L, "1001", "Known Duration Express");
                TrainSchedule knownSource = schedule(known, ndls, 1, null, LocalTime.of(8, 0), 0);
                TrainSchedule knownDestination = schedule(known, hwh, 2, LocalTime.of(12, 0), null, 400);

                // Missing departure time at the source stop - duration can't be
                // computed, so this must sort after "known", not crash.
                Train unknown = train(2L, "1002", "Unknown Duration Passenger");
                TrainSchedule unknownSource = schedule(unknown, ndls, 1, null, null, 0);
                TrainSchedule unknownDestination = schedule(unknown, hwh, 2, LocalTime.of(20, 0), null, 400);

                when(trainScheduleRepository.findByStation_StationCode("NDLS"))
                                .thenReturn(List.of(unknownSource, knownSource));
                when(trainScheduleRepository.findByStation_StationCode("HWH"))
                                .thenReturn(List.of(unknownDestination, knownDestination));
                when(trainScheduleRepository.findByTrain_IdInOrderByTrain_IdAscSequenceNoAsc(anyList()))
                                .thenReturn(List.of(knownSource, knownDestination, unknownSource, unknownDestination));

                JourneySearchResponse response = journeyService().search("NDLS", "HWH");

                assertThat(response.trains().get(0).trainNumber()).isEqualTo("1001");
                assertThat(response.trains().get(1).trainNumber()).isEqualTo("1002");
                assertThat(response.trains().get(1).duration()).isEmpty();
        }

        @Test
        void handlesOvernightJourneysThatCrossMidnight() {

                Station ndls = station("NDLS", "New Delhi");
                Station hwh = station("HWH", "Howrah Jn");

                // Departs 23:00 day 1, arrives 05:00 the next day = 6h, not a
                // negative duration.
                Train overnight = train(1L, "1001", "Overnight Express");
                TrainSchedule source = schedule(overnight, ndls, 1, null, LocalTime.of(23, 0), 0);
                TrainSchedule destination = schedule(overnight, hwh, 2, LocalTime.of(5, 0), null, 1400);

                when(trainScheduleRepository.findByStation_StationCode("NDLS")).thenReturn(List.of(source));
                when(trainScheduleRepository.findByStation_StationCode("HWH")).thenReturn(List.of(destination));
                when(trainScheduleRepository.findByTrain_IdInOrderByTrain_IdAscSequenceNoAsc(anyList()))
                                .thenReturn(List.of(source, destination));

                JourneySearchResponse response = journeyService().search("NDLS", "HWH");

                JourneyTrainResponse trip = response.trains().get(0);
                assertThat(trip.duration()).isEqualTo("6h 00m");

                // The whole 6h leg (23:00-05:00) falls inside the 21:00-06:00
                // night window.
                assertThat(trip.movingMinutes()).isEqualTo(360);
                assertThat(trip.nightTravelPercent()).isEqualTo(100.0);
                assertThat(trip.dayTravelPercent()).isEqualTo(0.0);
        }

        @Test
        void computesHaltsAndMovingSpeedForAJourneyWithAnIntermediateStop() {

                Station ndls = station("NDLS", "New Delhi");
                Station gaya = station("GAYA", "Gaya Jn");
                Station hwh = station("HWH", "Howrah Jn");

                // NDLS (dep 08:00) -> GAYA (arr 12:00 / dep 12:20, 20 min halt) ->
                // HWH (arr 16:00). Moving: 4h + 3h40m = 460 min. Halted: 20 min.
                // Total duration 08:00-16:00 = 8h = 480 min = 460 + 20, checks out.
                // Distance 1400km over 460 moving minutes.
                Train t = train(1L, "1001", "Intermediate Express");
                TrainSchedule source = schedule(t, ndls, 1, null, LocalTime.of(8, 0), 0);
                TrainSchedule mid = schedule(t, gaya, 2, LocalTime.of(12, 0), LocalTime.of(12, 20), 900);
                TrainSchedule destination = schedule(t, hwh, 3, LocalTime.of(16, 0), null, 1400);

                when(trainScheduleRepository.findByStation_StationCode("NDLS")).thenReturn(List.of(source));
                when(trainScheduleRepository.findByStation_StationCode("HWH")).thenReturn(List.of(destination));
                when(trainScheduleRepository.findByTrain_IdInOrderByTrain_IdAscSequenceNoAsc(anyList()))
                                .thenReturn(List.of(source, mid, destination));

                JourneySearchResponse response = journeyService().search("NDLS", "HWH");

                JourneyTrainResponse trip = response.trains().get(0);

                assertThat(trip.numHalts()).isEqualTo(1);
                assertThat(trip.haltedMinutes()).isEqualTo(20);
                assertThat(trip.longestHaltMinutes()).isEqualTo(20L);
                assertThat(trip.movingMinutes()).isEqualTo(460);
                assertThat(trip.averageMovingSpeedKmh()).isCloseTo(1400 / (460 / 60.0), org.assertj.core.api.Assertions.within(0.1));
        }
}
