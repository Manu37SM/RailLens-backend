package com.labs.train.train_db.service.network;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.labs.train.train_db.config.CacheConfig;
import com.labs.train.train_db.entity.TrainSchedule;
import com.labs.train.train_db.model.NetworkStatsResponse;
import com.labs.train.train_db.service.ScheduleSnapshotService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RailwayNetworkService {

        private final ScheduleSnapshotService scheduleSnapshotService;

        @Cacheable(cacheNames = CacheConfig.NETWORK_CACHE)
        public RailwayNetworkSnapshot buildSnapshot() {

                long startedAt = System.currentTimeMillis();

                Map<Long, List<TrainSchedule>> schedulesByTrainId = scheduleSnapshotService
                                .getAllOrderedByTrainThenSequence()
                                .stream()
                                .collect(Collectors.groupingBy(schedule -> schedule.getTrain().getId()));

                Map<String, StationNetworkNode> stations = new HashMap<>();
                int totalTrains = 0;

                for (List<TrainSchedule> route : schedulesByTrainId.values()) {

                        if (route.isEmpty()) {
                                continue;
                        }

                        totalTrains++;

                        for (int i = 0; i < route.size(); i++) {

                                TrainSchedule stop = route.get(i);

                                StationNetworkNode node = stations.computeIfAbsent(
                                                stop.getStation().getStationCode(),
                                                code -> new StationNetworkNode(code, stop.getStation().getStationName()));

                                node.stopCount++;

                                boolean isOrigin = i == 0;
                                boolean isDestination = i == route.size() - 1;

                                if (isOrigin) {
                                        node.originCount++;
                                } else if (isDestination) {
                                        node.destinationCount++;
                                } else {
                                        node.transitCount++;

                                        if (stop.getArrivalTime() != null && stop.getDepartureTime() != null
                                                        && !stop.getDepartureTime().isBefore(stop.getArrivalTime())) {

                                                node.haltMinutesSum += Duration
                                                                .between(stop.getArrivalTime(), stop.getDepartureTime())
                                                                .toMinutes();
                                                node.haltMinutesCount++;
                                        }
                                }

                                if (i > 0) {

                                        StationNetworkNode previous = stations.get(
                                                        route.get(i - 1).getStation().getStationCode());

                                        previous.recordEdge(node.stationCode);
                                        node.recordEdge(previous.stationCode);
                                }
                        }
                }

                GraphAnalysis analysis = analyzeGraph(stations);

                for (int i = 0; i < analysis.components.size(); i++) {
                        for (String code : analysis.components.get(i)) {
                                stations.get(code).componentId = i;
                        }
                }

                log.info(
                                "Built railway network snapshot: {} trains, {} stations, {} connected component(s), diameter {} in {}ms",
                                totalTrains, stations.size(), analysis.components.size(), analysis.diameter,
                                System.currentTimeMillis() - startedAt);

                return new RailwayNetworkSnapshot(
                                stations, totalTrains, analysis.components, analysis.largestComponentIndex, analysis.diameter);
        }

        private static final int TOP_CENTRAL_STATIONS = 25;

        public NetworkStatsResponse getNetworkStats() {

                RailwayNetworkSnapshot snapshot = buildSnapshot();

                int totalEdges = snapshot.stations.values().stream()
                                .mapToInt((StationNetworkNode node) -> node.degree())
                                .sum() / 2;

                int totalStations = snapshot.totalStations();

                double maxPossibleEdges = totalStations <= 1
                                ? 0.0
                                : totalStations * (totalStations - 1) / 2.0;

                double routeDensity = maxPossibleEdges == 0.0 ? 0.0 : totalEdges / maxPossibleEdges;

                List<NetworkStatsResponse.CentralStation> mostCentral = snapshot.stations.values().stream()
                                .sorted(Comparator.comparingDouble(
                                                (StationNetworkNode node) -> node.betweennessCentrality).reversed())
                                .limit(TOP_CENTRAL_STATIONS)
                                .map(node -> new NetworkStatsResponse.CentralStation(
                                                node.stationCode,
                                                node.stationName,
                                                Math.round(node.betweennessCentrality * 100.0) / 100.0,
                                                Math.round(node.closenessCentrality * 1000.0) / 1000.0,
                                                node.degree()))
                                .toList();

                int largestComponentSize = snapshot.largestComponentIndex < 0
                                ? 0
                                : snapshot.connectedComponents.get(snapshot.largestComponentIndex).size();

                return new NetworkStatsResponse(
                                totalStations,
                                snapshot.totalTrains,
                                totalEdges,
                                Math.round(routeDensity * 10000.0) / 10000.0,
                                snapshot.connectedComponents.size(),
                                largestComponentSize,
                                snapshot.networkDiameter,
                                mostCentral);
        }

        private record GraphAnalysis(List<Set<String>> components, int largestComponentIndex, int diameter) {
        }

        private GraphAnalysis analyzeGraph(Map<String, StationNetworkNode> stations) {

                List<Set<String>> components = findConnectedComponents(stations);

                if (components.isEmpty()) {
                        return new GraphAnalysis(components, -1, 0);
                }

                int largestIndex = 0;
                for (int i = 1; i < components.size(); i++) {
                        if (components.get(i).size() > components.get(largestIndex).size()) {
                                largestIndex = i;
                        }
                }

                Set<String> largest = components.get(largestIndex);
                int diameter = computeCentralityAndDiameter(stations, largest);

                return new GraphAnalysis(components, largestIndex, diameter);
        }

        private List<Set<String>> findConnectedComponents(Map<String, StationNetworkNode> stations) {

                List<Set<String>> components = new ArrayList<>();
                Set<String> visited = new HashSet<>();

                for (String start : stations.keySet()) {

                        if (visited.contains(start)) {
                                continue;
                        }

                        Set<String> component = new HashSet<>();
                        Queue<String> queue = new ArrayDeque<>();
                        queue.add(start);
                        visited.add(start);

                        while (!queue.isEmpty()) {

                                String current = queue.poll();
                                component.add(current);

                                for (String neighbor : stations.get(current).neighborTrainCounts.keySet()) {
                                        if (visited.add(neighbor)) {
                                                queue.add(neighbor);
                                        }
                                }
                        }

                        components.add(component);
                }

                return components;
        }

        private int computeCentralityAndDiameter(Map<String, StationNetworkNode> stations, Set<String> component) {

                int n = component.size();

                if (n == 0) {
                        return 0;
                }

                String[] indexToCode = component.toArray(new String[0]);
                Map<String, Integer> codeToIndex = new HashMap<>(n * 2);

                for (int i = 0; i < n; i++) {
                        codeToIndex.put(indexToCode[i], i);
                }

                int[][] adjacency = new int[n][];

                for (int i = 0; i < n; i++) {

                        Set<String> neighborCodes = stations.get(indexToCode[i]).neighborTrainCounts.keySet();
                        int[] neighbors = new int[neighborCodes.size()];
                        int count = 0;

                        for (String neighborCode : neighborCodes) {
                                Integer neighborIndex = codeToIndex.get(neighborCode);
                                if (neighborIndex != null) {
                                        neighbors[count++] = neighborIndex;
                                }
                        }

                        adjacency[i] = count == neighbors.length ? neighbors : Arrays.copyOf(neighbors, count);
                }

                double[] betweenness = new double[n];
                int diameter = 0;

                int[] distance = new int[n];
                Arrays.fill(distance, -1);
                double[] sigma = new double[n];
                double[] dependency = new double[n];
                int[] queue = new int[n];
                int[] visitOrder = new int[n];

                @SuppressWarnings("unchecked")
                List<Integer>[] predecessors = new List[n];

                for (int source = 0; source < n; source++) {

                        int queueHead = 0;
                        int queueTail = 0;
                        int visitCount = 0;

                        distance[source] = 0;
                        sigma[source] = 1.0;
                        queue[queueTail++] = source;

                        while (queueHead < queueTail) {

                                int v = queue[queueHead++];
                                visitOrder[visitCount++] = v;

                                for (int w : adjacency[v]) {

                                        if (distance[w] < 0) {
                                                distance[w] = distance[v] + 1;
                                                queue[queueTail++] = w;
                                        }

                                        if (distance[w] == distance[v] + 1) {
                                                sigma[w] += sigma[v];
                                                if (predecessors[w] == null) {
                                                        predecessors[w] = new ArrayList<>();
                                                }
                                                predecessors[w].add(v);
                                        }
                                }
                        }

                        int eccentricity = 0;
                        long sumOfDistances = 0;

                        for (int i = 0; i < visitCount; i++) {

                                int d = distance[visitOrder[i]];

                                if (d > eccentricity) {
                                        eccentricity = d;
                                }
                                if (d > 0) {
                                        sumOfDistances += d;
                                }
                        }

                        stations.get(indexToCode[source]).eccentricity = eccentricity;
                        stations.get(indexToCode[source]).closenessCentrality = sumOfDistances == 0
                                        ? 0.0
                                        : (n - 1) / (double) sumOfDistances;

                        diameter = Math.max(diameter, eccentricity);

                        for (int i = visitCount - 1; i >= 0; i--) {

                                int w = visitOrder[i];
                                List<Integer> preds = predecessors[w];

                                if (preds != null) {
                                        for (int v : preds) {
                                                double contribution = (sigma[v] / sigma[w]) * (1 + dependency[w]);
                                                dependency[v] += contribution;
                                        }
                                }

                                if (w != source) {
                                        betweenness[w] += dependency[w];
                                }
                        }

                        for (int i = 0; i < visitCount; i++) {
                                int node = visitOrder[i];
                                distance[node] = -1;
                                sigma[node] = 0.0;
                                dependency[node] = 0.0;
                                predecessors[node] = null;
                        }
                }

                for (int i = 0; i < n; i++) {
                        stations.get(indexToCode[i]).betweennessCentrality = betweenness[i] / 2.0;
                }

                return diameter;
        }
}
