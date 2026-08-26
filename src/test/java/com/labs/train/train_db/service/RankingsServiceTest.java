package com.labs.train.train_db.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.labs.train.train_db.entity.Station;
import com.labs.train.train_db.entity.Train;
import com.labs.train.train_db.entity.TrainSchedule;
import com.labs.train.train_db.model.RankingsResponse;
import com.labs.train.train_db.service.network.RailwayNetworkService;

@ExtendWith(MockitoExtension.class)
class RankingsServiceTest {

        @Mock
        private ScheduleSnapshotService scheduleSnapshotService;

        private RankingsService service() {
                return new RankingsService(new RailwayNetworkService(scheduleSnapshotService), scheduleSnapshotService);
        }

        private static Train train(long id, String number) {
                Train train = new Train();
                train.setId(id);
                train.setTrainNumber(number);
                train.setTrainName("Train " + number);
                return train;
        }

        private static Station station(String code) {
                Station station = new Station();
                station.setStationCode(code);
                station.setStationName(code + " Station");
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
        void ranksHaltsAndStationsCorrectly() {

                Train t9001 = train(1L, "9001");
                Train t9002 = train(2L, "9002");

                Station a = station("A");
                Station b = station("B");
                Station c = station("C");
                Station d = station("D");
                Station x = station("X");
                Station y = station("Y");

                List<TrainSchedule> route9001 = List.of(
                                schedule(t9001, a, 1, null, LocalTime.of(8, 0), 0),
                                schedule(t9001, b, 2, LocalTime.of(9, 0), LocalTime.of(9, 5), 100),
                                schedule(t9001, c, 3, LocalTime.of(10, 0), LocalTime.of(10, 45), 200),
                                schedule(t9001, d, 4, LocalTime.of(11, 0), null, 300));

                List<TrainSchedule> route9002 = List.of(
                                schedule(t9002, x, 1, null, LocalTime.of(7, 0), 0),
                                schedule(t9002, b, 2, LocalTime.of(7, 30), LocalTime.of(7, 50), 50),
                                schedule(t9002, y, 3, LocalTime.of(8, 30), null, 100));

                List<TrainSchedule> allSchedules = new ArrayList<>();
                allSchedules.addAll(route9001);
                allSchedules.addAll(route9002);

                when(scheduleSnapshotService.getAllOrderedByTrainThenSequence()).thenReturn(allSchedules);

                RankingsResponse response = service().getRankings();

                assertThat(response.mostHaltsTrains().get(0).trainNumber()).isEqualTo("9001");
                assertThat(response.mostHaltsTrains().get(0).haltCount()).isEqualTo(2);

                assertThat(response.fewestHaltsTrains().get(0).trainNumber()).isEqualTo("9002");
                assertThat(response.fewestHaltsTrains().get(0).haltCount()).isEqualTo(1);

                assertThat(response.longestHalts().get(0).trainNumber()).isEqualTo("9001");
                assertThat(response.longestHalts().get(0).stationCode()).isEqualTo("C");
                assertThat(response.longestHalts().get(0).minutes()).isEqualTo(45);

                assertThat(response.shortestHalts().get(0).stationCode()).isEqualTo("B");
                assertThat(response.shortestHalts().get(0).minutes()).isEqualTo(5);

                assertThat(response.mostConnectedStations().get(0).stationCode()).isEqualTo("B");
                assertThat(response.mostConnectedStations().get(0).count()).isEqualTo(4);

                assertThat(response.mostPopularOriginStations().get(0).count()).isEqualTo(1);
                assertThat(response.mostPopularOriginStations())
                                .extracting((RankingsResponse.StationCountEntry entry) -> entry.stationCode())
                                .doesNotContain("B");
        }
}
