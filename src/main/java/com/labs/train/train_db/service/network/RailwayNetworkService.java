package com.labs.train.train_db.service.network;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
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
import com.labs.train.train_db.repository.TrainScheduleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Builds the shared railway network graph (stations as nodes, a direct hop
 * between consecutive stops on any train's route as an edge) that every
 * "Railway Intelligence" feature added in this pass reads from - station
 * importance/connectivity, train uniqueness/station-skipping, route
 * analytics, and the graph-theoretic metrics under "Railway Network"
 * (diameter, centrality, connected components) all need the same underlying
 * structure, so it's built once here rather than each feature re-scanning
 * the schedule table and re-deriving adjacency independently.
 *
 * A single full-table pass (same {@code findAllByOrderByTrain_IdAscSequenceNoAsc}
 * query StatsService already uses for train speeds) plus one BFS per station
 * in the largest connected component (Brandes' algorithm, for betweenness/
 * closeness/diameter together in one pass) - expensive enough that it must
 * stay cached (NETWORK_CACHE) rather than recomputed per request, but a
 * one-time cost on cache miss, same trade-off StatsService already makes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RailwayNetworkService {

        private final TrainScheduleRepository trainScheduleRepository;

        @Cacheable(cacheNames = CacheConfig.NETWORK_CACHE)
        public RailwayNetworkSnapshot buildSnapshot() {

                long startedAt = System.currentTimeMillis();

                Map<Long, List<TrainSchedule>> schedulesByTrainId = trainScheduleRepository
                                .findAllByOrderByTrain_IdAscSequenceNoAsc()
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

        private static final int TOP_CENTRAL_STATIONS = 10;

        /**
         * Network-wide summary (FEATURE.md's "Railway Network" section) -
         * built from the same cached snapshot #buildSnapshot produces, so
         * this is cheap on top of an existing cache hit and only pays the
         * full graph-analysis cost on the same cache miss buildSnapshot
         * would anyway.
         */
        public NetworkStatsResponse getNetworkStats() {

                RailwayNetworkSnapshot snapshot = buildSnapshot();

                // Explicit lambda rather than StationNetworkNode::degree - avoids
                // the JDT null analyzer's "unchecked conversion for the
                // receiver" warning on the method-reference form (same reasoning
                // as the fixes elsewhere in this codebase); same behavior either
                // way.
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

        // ------------------------------------------------------------

        private record GraphAnalysis(List<Set<String>> components, int largestComponentIndex, int diameter) {
        }

        /**
         * Connected components (BFS flood-fill) over every station, then
         * betweenness/closeness centrality and eccentricity (Brandes'
         * algorithm - one BFS per node, unweighted) restricted to the largest
         * component only. Stations outside it keep their centrality/
         * eccentricity at the {@link StationNetworkNode} default of 0 - a
         * shortest-path distance to an unreachable station isn't a number,
         * not a real zero, but 0 is a safer default for API consumers than
         * throwing or returning a sentinel like -1 that a naive caller might
         * plot on a chart.
         */
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

        /**
         * Brandes' algorithm (Brandes, 2001) - computes betweenness
         * centrality for every node in one BFS-per-source pass rather than
         * the naive O(V^3) all-pairs-shortest-paths approach, and closeness
         * centrality / eccentricity fall out of the same BFS distances for
         * free. Undirected graph, so raw betweenness accumulation double-
         * counts each shortest path (once from each endpoint's BFS) - halved
         * at the end, the standard correction for undirected Brandes.
         */
        private int computeCentralityAndDiameter(Map<String, StationNetworkNode> stations, Set<String> component) {

                Map<String, Double> betweenness = new HashMap<>();
                for (String code : component) {
                        betweenness.put(code, 0.0);
                }

                int diameter = 0;

                for (String source : component) {

                        Map<String, Integer> distance = new HashMap<>();
                        Map<String, Double> sigma = new HashMap<>();
                        Map<String, List<String>> predecessors = new HashMap<>();

                        for (String code : component) {
                                distance.put(code, -1);
                                sigma.put(code, 0.0);
                                predecessors.put(code, new ArrayList<>());
                        }

                        distance.put(source, 0);
                        sigma.put(source, 1.0);

                        Queue<String> queue = new ArrayDeque<>();
                        queue.add(source);

                        List<String> visitOrder = new ArrayList<>();

                        while (!queue.isEmpty()) {

                                String v = queue.poll();
                                visitOrder.add(v);

                                for (String w : stations.get(v).neighborTrainCounts.keySet()) {

                                        if (!component.contains(w)) {
                                                continue;
                                        }

                                        if (distance.get(w) < 0) {
                                                distance.put(w, distance.get(v) + 1);
                                                queue.add(w);
                                        }

                                        if (distance.get(w) == distance.get(v) + 1) {
                                                sigma.put(w, sigma.get(w) + sigma.get(v));
                                                predecessors.get(w).add(v);
                                        }
                                }
                        }

                        int eccentricity = 0;
                        long sumOfDistances = 0;

                        for (String code : component) {
                                int d = distance.get(code);
                                if (d > eccentricity) {
                                        eccentricity = d;
                                }
                                if (d > 0) {
                                        sumOfDistances += d;
                                }
                        }

                        stations.get(source).eccentricity = eccentricity;
                        stations.get(source).closenessCentrality = sumOfDistances == 0
                                        ? 0.0
                                        : (component.size() - 1) / (double) sumOfDistances;

                        diameter = Math.max(diameter, eccentricity);

                        Map<String, Double> dependency = new HashMap<>();
                        for (String code : component) {
                                dependency.put(code, 0.0);
                        }

                        for (int i = visitOrder.size() - 1; i >= 0; i--) {

                                String w = visitOrder.get(i);

                                for (String v : predecessors.get(w)) {
                                        double contribution = (sigma.get(v) / sigma.get(w)) * (1 + dependency.get(w));
                                        dependency.put(v, dependency.get(v) + contribution);
                                }

                                if (!w.equals(source)) {
                                        betweenness.put(w, betweenness.get(w) + dependency.get(w));
                                }
                        }
                }

                for (String code : component) {
                        // Halved: undirected-graph correction (see method javadoc).
                        stations.get(code).betweennessCentrality = betweenness.get(code) / 2.0;
                }

                return diameter;
        }
}
