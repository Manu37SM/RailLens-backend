package com.labs.train.train_db.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.labs.train.train_db.entity.Station;
import com.labs.train.train_db.entity.Train;
import com.labs.train.train_db.entity.TrainSchedule;
import com.labs.train.train_db.exception.ResourceNotFoundException;
import com.labs.train.train_db.model.RouteComparisonResponse;
import com.labs.train.train_db.repository.TrainScheduleRepository;

@ExtendWith(MockitoExtension.class)
class RouteAnalyticsServiceTest {

        @Mock
        private TrainScheduleRepository trainScheduleRepository;

        private RouteAnalyticsService service() {
                return new RouteAnalyticsService(trainScheduleRepository);
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

        private static List<TrainSchedule> route(Train t, String... codes) {

                return java.util.stream.IntStream.range(0, codes.length)
                                .mapToObj(i -> {
                                        TrainSchedule schedule = new TrainSchedule();
                                        schedule.setTrain(t);
                                        schedule.setStation(station(codes[i]));
                                        schedule.setSequenceNo(i + 1);
                                        return schedule;
                                })
                                .toList();
        }

        @Test
        void throwsWhenEitherTrainDoesNotExist() {

                when(trainScheduleRepository.findByTrain_TrainNumberOrderBySequenceNo("9999"))
                                .thenReturn(List.of());

                assertThatThrownBy(() -> service().compareRoutes("9999", "1111"))
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("9999");
        }

        @Test
        void findsSharedSegmentDivergenceAndConvergence() {

                Train a = train(1L, "AAAA");
                Train b = train(2L, "BBBB");

                List<TrainSchedule> routeA = route(a, "P", "Q", "R", "S", "T", "U");
                List<TrainSchedule> routeB = route(b, "M", "Q", "R", "S", "N", "U");

                when(trainScheduleRepository.findByTrain_TrainNumberOrderBySequenceNo("AAAA")).thenReturn(routeA);
                when(trainScheduleRepository.findByTrain_TrainNumberOrderBySequenceNo("BBBB")).thenReturn(routeB);

                RouteComparisonResponse response = service().compareRoutes("AAAA", "BBBB");

                assertThat(response.trainNumberA()).isEqualTo("AAAA");
                assertThat(response.trainNumberB()).isEqualTo("BBBB");
                assertThat(response.totalStationsA()).isEqualTo(6);
                assertThat(response.totalStationsB()).isEqualTo(6);

                assertThat(response.sharedStationCount()).isEqualTo(4);
                assertThat(response.routeSimilarityPercent()).isCloseTo(50.0, within(0.01));

                assertThat(response.longestCommonSegment()).containsExactly("Q", "R", "S");
                assertThat(response.divergencePoint()).isEqualTo("S");
                assertThat(response.convergencePoint()).isEqualTo("U");

                assertThat(response.isReverseRoute()).isFalse();
                assertThat(response.isSameRoute()).isFalse();
        }

        @Test
        void detectsAnExactReverseRoute() {

                Train c = train(3L, "CCCC");
                Train d = train(4L, "DDDD");

                List<TrainSchedule> routeC = route(c, "X", "Y", "Z");
                List<TrainSchedule> routeD = route(d, "Z", "Y", "X");

                when(trainScheduleRepository.findByTrain_TrainNumberOrderBySequenceNo("CCCC")).thenReturn(routeC);
                when(trainScheduleRepository.findByTrain_TrainNumberOrderBySequenceNo("DDDD")).thenReturn(routeD);

                RouteComparisonResponse response = service().compareRoutes("CCCC", "DDDD");

                assertThat(response.isReverseRoute()).isTrue();
                assertThat(response.isSameRoute()).isFalse();

                assertThat(response.sharedStationCount()).isEqualTo(3);
                assertThat(response.routeSimilarityPercent()).isCloseTo(100.0, within(0.01));
                assertThat(response.longestCommonSegment()).hasSize(1);
        }

        @Test
        void detectsAnIdenticalRoute() {

                Train e = train(5L, "EEEE");
                Train f = train(6L, "FFFF");

                List<TrainSchedule> routeE = route(e, "A", "B", "C");
                List<TrainSchedule> routeF = route(f, "A", "B", "C");

                when(trainScheduleRepository.findByTrain_TrainNumberOrderBySequenceNo("EEEE")).thenReturn(routeE);
                when(trainScheduleRepository.findByTrain_TrainNumberOrderBySequenceNo("FFFF")).thenReturn(routeF);

                RouteComparisonResponse response = service().compareRoutes("EEEE", "FFFF");

                assertThat(response.isSameRoute()).isTrue();
                assertThat(response.longestCommonSegment()).containsExactly("A", "B", "C");
                assertThat(response.divergencePoint()).isEqualTo("C");
                assertThat(response.convergencePoint()).isNull();
        }
}
