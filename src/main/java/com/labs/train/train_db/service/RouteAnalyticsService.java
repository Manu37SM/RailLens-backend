package com.labs.train.train_db.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.labs.train.train_db.entity.TrainSchedule;
import com.labs.train.train_db.exception.ResourceNotFoundException;
import com.labs.train.train_db.model.RouteComparisonResponse;
import com.labs.train.train_db.repository.TrainScheduleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RouteAnalyticsService {

        private final TrainScheduleRepository trainScheduleRepository;

        public RouteComparisonResponse compareRoutes(String trainNumberA, String trainNumberB) {

                List<TrainSchedule> routeA = trainScheduleRepository
                                .findByTrain_TrainNumberOrderBySequenceNo(trainNumberA);

                if (routeA.isEmpty()) {
                        throw new ResourceNotFoundException("Train not found: " + trainNumberA);
                }

                List<TrainSchedule> routeB = trainScheduleRepository
                                .findByTrain_TrainNumberOrderBySequenceNo(trainNumberB);

                if (routeB.isEmpty()) {
                        throw new ResourceNotFoundException("Train not found: " + trainNumberB);
                }

                List<String> codesA = stationCodes(routeA);
                List<String> codesB = stationCodes(routeB);

                Set<String> setA = new HashSet<>(codesA);
                Set<String> setB = new HashSet<>(codesB);

                Set<String> shared = new HashSet<>(setA);
                shared.retainAll(setB);

                int unionSize = setA.size() + setB.size() - shared.size();
                double similarity = unionSize == 0 ? 0.0 : round1(shared.size() * 100.0 / unionSize);

                LongestCommonSegment segment = longestCommonSegment(codesA, codesB);

                String divergencePoint = segment.length() == 0
                                ? null
                                : codesA.get(segment.endA() - 1);

                String convergencePoint = segment.length() == 0
                                ? null
                                : findConvergencePoint(codesA, codesB, segment);

                return new RouteComparisonResponse(
                                trainNumberA, routeA.get(0).getTrain().getTrainName(), codesA.size(),
                                trainNumberB, routeB.get(0).getTrain().getTrainName(), codesB.size(),
                                shared.size(),
                                similarity,
                                codesA.subList(segment.endA() - segment.length(), segment.endA()),
                                divergencePoint,
                                convergencePoint,
                                isReverseOf(codesA, codesB),
                                codesA.equals(codesB));
        }

        private List<String> stationCodes(List<TrainSchedule> route) {
                return route.stream()
                                .map(schedule -> schedule.getStation().getStationCode())
                                .toList();
        }

        private record LongestCommonSegment(int endA, int endB, int length) {
        }

        private LongestCommonSegment longestCommonSegment(List<String> a, List<String> b) {

                int n = a.size();
                int m = b.size();

                int[][] dp = new int[n + 1][m + 1];

                int bestLength = 0;
                int bestEndA = 0;
                int bestEndB = 0;

                for (int i = 1; i <= n; i++) {
                        for (int j = 1; j <= m; j++) {

                                if (a.get(i - 1).equals(b.get(j - 1))) {

                                        dp[i][j] = dp[i - 1][j - 1] + 1;

                                        if (dp[i][j] > bestLength) {
                                                bestLength = dp[i][j];
                                                bestEndA = i;
                                                bestEndB = j;
                                        }
                                }
                        }
                }

                return new LongestCommonSegment(bestEndA, bestEndB, bestLength);
        }

        private String findConvergencePoint(List<String> codesA, List<String> codesB, LongestCommonSegment segment) {

                Set<String> tailB = new HashSet<>(codesB.subList(segment.endB(), codesB.size()));

                for (int i = segment.endA(); i < codesA.size(); i++) {
                        if (tailB.contains(codesA.get(i))) {
                                return codesA.get(i);
                        }
                }

                return null;
        }

        private boolean isReverseOf(List<String> a, List<String> b) {

                if (a.size() != b.size()) {
                        return false;
                }

                for (int i = 0; i < a.size(); i++) {
                        if (!a.get(i).equals(b.get(b.size() - 1 - i))) {
                                return false;
                        }
                }

                return true;
        }

        private double round1(double value) {
                return Math.round(value * 10.0) / 10.0;
        }
}
