package com.labs.train.train_db.service.network;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class RailwayNetworkSnapshot {

        public final Map<String, StationNetworkNode> stations;
        public final int totalTrains;

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
