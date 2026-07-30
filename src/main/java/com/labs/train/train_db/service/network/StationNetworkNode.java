package com.labs.train.train_db.service.network;

import java.util.HashMap;
import java.util.Map;

/**
 * One station's role in the railway network graph - built once per
 * {@link RailwayNetworkSnapshot} and shared by every "intelligence" feature
 * that needs to reason about a station's connectivity rather than just its
 * own schedule rows (station importance ranking, train uniqueness/station-
 * skipping analysis, route analytics, etc.). Mutable during construction in
 * {@link RailwayNetworkService}, treated as read-only afterwards.
 */
public class StationNetworkNode {

        public final String stationCode;
        public final String stationName;

        public int stopCount = 0;
        public int originCount = 0;
        public int destinationCount = 0;
        public int transitCount = 0;

        // Undirected: an edge A-B is recorded on both A's and B's node. Value
        // is the number of distinct trains observed making that exact hop
        // (in either direction) - used as an edge "weight" for connectivity/
        // uniqueness scoring, though the graph-traversal algorithms
        // (centrality/diameter/components) deliberately treat every edge as
        // unweighted (1 hop), since those measure topological reachability,
        // not traffic volume.
        public final Map<String, Integer> neighborTrainCounts = new HashMap<>();

        // Halt-duration accumulator for stops where this station is an
        // intermediate (not origin/destination) stop on some train's route -
        // see RailwayNetworkService for why arrival/departure at the first/
        // last stop of a route isn't a "halt" in the same sense.
        public long haltMinutesSum = 0;
        public int haltMinutesCount = 0;

        // Filled in by RailwayNetworkService's graph-analysis pass, after
        // every station node already exists.
        public int componentId = -1;
        public int eccentricity = 0;
        public double closenessCentrality = 0.0;
        public double betweennessCentrality = 0.0;

        public StationNetworkNode(String stationCode, String stationName) {
                this.stationCode = stationCode;
                this.stationName = stationName;
        }

        public void recordEdge(String neighborCode) {
                // Explicit lambda rather than Integer::sum - avoids the JDT null
                // analyzer's "unchecked conversion" warning on the boxed-Integer
                // method-reference form; same behavior either way.
                neighborTrainCounts.merge(neighborCode, 1, (a, b) -> a + b);
        }

        public int degree() {
                return neighborTrainCounts.size();
        }

        public double averageHaltMinutes() {
                return haltMinutesCount == 0 ? 0.0 : (double) haltMinutesSum / haltMinutesCount;
        }
}
