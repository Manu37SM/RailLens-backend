package com.labs.train.train_db.service.network;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A full-network graph snapshot: every station as a node, a direct hop
 * between two consecutive stops on any train's route as an (undirected,
 * unweighted for graph-traversal purposes) edge. Built once by
 * {@link RailwayNetworkService} and cached (see CacheConfig.NETWORK_CACHE) -
 * every "intelligence" feature that needs network-wide context (station
 * importance, connectivity, centrality, train uniqueness/station-skipping)
 * reads from this shared snapshot instead of re-scanning the schedule table
 * itself.
 */
public class RailwayNetworkSnapshot {

        public final Map<String, StationNetworkNode> stations;
        public final int totalTrains;

        // The graph-traversal metrics (eccentricity/closeness/betweenness/
        // diameter) are only meaningful within a connected component - a
        // station unreachable from another has no finite shortest-path
        // distance between them. Indian Railways' network is not fully
        // connected in this dataset (some stations only ever appear as an
        // isolated origin/destination pair with no other trains through
        // them), so "network diameter" is reported for the largest
        // connected component only, not a nonsensical network-wide max.
        public final List<Set<String>> connectedComponents;
        public final int largestComponentIndex;
        public final int networkDiameter;

        public RailwayNetworkSnapshot(
                        Map<String, StationNetworkNode> stations,
                        int totalTrains,
                        List<Set<String>> connectedComponents,
                        int largestComponentIndex,
                        int networkDiameter) {

                this.stations = stations;
                this.totalTrains = totalTrains;
                this.connectedComponents = connectedComponents;
                this.largestComponentIndex = largestComponentIndex;
                this.networkDiameter = networkDiameter;
        }

        public StationNetworkNode station(String stationCode) {
                return stations.get(stationCode);
        }

        public int totalStations() {
                return stations.size();
        }
}
