package com.labs.train.train_db.service.network;

import java.util.HashMap;
import java.util.Map;

public class StationNetworkNode {

        public final String stationCode;
        public final String stationName;

        public int stopCount = 0;
        public int originCount = 0;
        public int destinationCount = 0;
        public int transitCount = 0;

        public final Map<String, Integer> neighborTrainCounts = new HashMap<>();

        public long haltMinutesSum = 0;
        public int haltMinutesCount = 0;

        public int componentId = -1;
        public int eccentricity = 0;
        public double closenessCentrality = 0.0;
        public double betweennessCentrality = 0.0;

        public StationNetworkNode(String stationCode, String stationName) {
                this.stationCode = stationCode;
                this.stationName = stationName;
        }

        public void recordEdge(String neighborCode) {
                neighborTrainCounts.merge(neighborCode, 1, (a, b) -> a + b);
        }

        public int degree() {
                return neighborTrainCounts.size();
        }

        public double averageHaltMinutes() {
                return haltMinutesCount == 0 ? 0.0 : (double) haltMinutesSum / haltMinutesCount;
        }
}
