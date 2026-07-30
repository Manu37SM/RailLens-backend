package com.labs.train.train_db.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.labs.train.train_db.entity.Station;
import com.labs.train.train_db.entity.TrainSchedule;
import com.labs.train.train_db.model.DatasetHealthResponse;
import com.labs.train.train_db.repository.StationRepository;
import com.labs.train.train_db.repository.TrainScheduleRepository;

import lombok.RequiredArgsConstructor;

/**
 * "Dataset Health" diagnostics (FEATURE.md) - see DatasetHealthResponse's
 * javadoc for how this differs from the Python import-time validator.
 * Deliberately NOT cached (unlike Stats/Rankings/FunStats/Achievements) -
 * this is an admin-only, low-traffic diagnostic endpoint an operator hits
 * after suspecting a data problem, so a stale cached "everything's fine"
 * result would defeat the point; the one-time full-table-scan cost on each
 * call is an acceptable trade-off for a rarely-hit admin endpoint.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DatasetHealthService {

        // Exact counts are always accurate; sample lists exist only to give an
        // operator somewhere to start looking, not to be exhaustive.
        private static final int MAX_SAMPLES = 20;

        // Fastest scheduled Indian trains (Vande Bharat, Gatimaan Express) top
        // out around 160-180 km/h - 200 km/h is a documented, deliberately
        // generous ceiling above that so this only flags genuinely impossible
        // data (e.g. a data-entry error implying teleportation), not merely
        // "fast."
        private static final double MAX_PLAUSIBLE_SPEED_KMH = 200.0;

        // A halt this long is unusual enough to be worth a human glance, even
        // though some real halts (e.g. engine reversal, long junction waits)
        // legitimately run this long - a documented heuristic, not a hard
        // data-integrity rule like the other checks here.
        private static final long LONG_HALT_MINUTES_THRESHOLD = 180;

        private final StationRepository stationRepository;
        private final TrainScheduleRepository trainScheduleRepository;

        public DatasetHealthResponse checkHealth() {

                Map<Long, List<TrainSchedule>> routesByTrainId = trainScheduleRepository
                                .findAllByOrderByTrain_IdAscSequenceNoAsc()
                                .stream()
                                .collect(Collectors.groupingBy(schedule -> schedule.getTrain().getId()));

                List<String> duplicateSamples = new ArrayList<>();
                List<String> missingTimingSamples = new ArrayList<>();
                List<String> distanceIssueSamples = new ArrayList<>();
                List<String> speedIssueSamples = new ArrayList<>();
                List<String> haltIssueSamples = new ArrayList<>();
                List<String> invalidRouteSamples = new ArrayList<>();

                int duplicateCount = 0;
                int missingTimingCount = 0;
                int distanceIssueCount = 0;
                int speedIssueCount = 0;
                int haltIssueCount = 0;
                int invalidRouteCount = 0;

                Set<String> stationCodesReferenced = new HashSet<>();

                for (List<TrainSchedule> route : routesByTrainId.values()) {

                        if (route.isEmpty()) {
                                continue;
                        }

                        String trainNumber = route.get(0).getTrain().getTrainNumber();

                        if (route.size() < 2) {
                                invalidRouteCount++;
                                invalidRouteSamples.add(trainNumber + " has only " + route.size() + " stop(s)");
                        }

                        Set<Integer> seenSequenceNumbers = new HashSet<>();

                        for (int i = 0; i < route.size(); i++) {

                                TrainSchedule stop = route.get(i);

                                if (stop.getStation() != null && stop.getStation().getStationCode() != null) {
                                        stationCodesReferenced.add(stop.getStation().getStationCode());
                                }

                                if (stop.getSequenceNo() != null && !seenSequenceNumbers.add(stop.getSequenceNo())) {
                                        duplicateCount++;
                                        duplicateSamples.add(trainNumber + " has more than one stop at sequence " + stop.getSequenceNo());
                                }

                                boolean isOrigin = i == 0;
                                boolean isDestination = i == route.size() - 1;

                                if (isOrigin && stop.getDepartureTime() == null) {
                                        missingTimingCount++;
                                        missingTimingSamples.add(trainNumber + " is missing a departure time at its origin");
                                } else if (isDestination && stop.getArrivalTime() == null) {
                                        missingTimingCount++;
                                        missingTimingSamples.add(trainNumber + " is missing an arrival time at its destination");
                                } else if (!isOrigin && !isDestination
                                                && (stop.getArrivalTime() == null || stop.getDepartureTime() == null)) {
                                        missingTimingCount++;
                                        missingTimingSamples.add(trainNumber + " is missing arrival/departure at stop "
                                                        + (stop.getStation() != null ? stop.getStation().getStationCode() : "?"));
                                }

                                if (!isOrigin && !isDestination
                                                && stop.getArrivalTime() != null && stop.getDepartureTime() != null
                                                && stop.getDepartureTime().isBefore(stop.getArrivalTime())) {

                                        haltIssueCount++;
                                        haltIssueSamples.add(trainNumber + " departs before it arrives at "
                                                        + (stop.getStation() != null ? stop.getStation().getStationCode() : "?"));

                                } else if (!isOrigin && !isDestination
                                                && stop.getArrivalTime() != null && stop.getDepartureTime() != null) {

                                        long haltMinutes = Duration.between(stop.getArrivalTime(), stop.getDepartureTime()).toMinutes();

                                        if (haltMinutes >= LONG_HALT_MINUTES_THRESHOLD) {
                                                haltIssueCount++;
                                                haltIssueSamples.add(trainNumber + " has an unusually long "
                                                                + haltMinutes + "-minute halt at "
                                                                + (stop.getStation() != null ? stop.getStation().getStationCode() : "?"));
                                        }
                                }

                                if (i > 0) {

                                        TrainSchedule previous = route.get(i - 1);

                                        if (previous.getDistance() != null && stop.getDistance() != null
                                                        && stop.getDistance() < previous.getDistance()) {

                                                distanceIssueCount++;
                                                distanceIssueSamples.add(trainNumber + " distance decreases from "
                                                                + previous.getDistance() + "km to " + stop.getDistance()
                                                                + "km between stop " + (i) + " and " + (i + 1));
                                        }

                                        Double speedKmh = segmentSpeedKmh(previous, stop);

                                        if (speedKmh != null && speedKmh > MAX_PLAUSIBLE_SPEED_KMH) {

                                                speedIssueCount++;
                                                speedIssueSamples.add(trainNumber + " implies " + Math.round(speedKmh)
                                                                + " km/h between stop " + i + " and " + (i + 1));
                                        }
                                }
                        }
                }

                List<String> orphanStationSamples = new ArrayList<>();
                int orphanStationCount = 0;

                for (Station station : stationRepository.findAll()) {

                        if (station.getStationCode() != null && !stationCodesReferenced.contains(station.getStationCode())) {
                                orphanStationCount++;
                                orphanStationSamples.add(station.getStationCode() + " (" + station.getStationName() + ") has no trains");
                        }
                }

                int totalIssues = duplicateCount + missingTimingCount + distanceIssueCount
                                + speedIssueCount + haltIssueCount + orphanStationCount + invalidRouteCount;

                return new DatasetHealthResponse(
                                totalIssues,
                                duplicateCount, cap(duplicateSamples),
                                missingTimingCount, cap(missingTimingSamples),
                                distanceIssueCount, cap(distanceIssueSamples),
                                speedIssueCount, cap(speedIssueSamples),
                                haltIssueCount, cap(haltIssueSamples),
                                orphanStationCount, cap(orphanStationSamples),
                                invalidRouteCount, cap(invalidRouteSamples));
        }

        // ------------------------------------------------------------

        private Double segmentSpeedKmh(TrainSchedule from, TrainSchedule to) {

                if (from.getDistance() == null || to.getDistance() == null
                                || from.getDepartureTime() == null || to.getArrivalTime() == null) {
                        return null;
                }

                int distanceKm = to.getDistance() - from.getDistance();

                if (distanceKm <= 0) {
                        return null;
                }

                long minutes = Duration.between(from.getDepartureTime(), to.getArrivalTime()).toMinutes();

                if (minutes < 0) {
                        minutes += Duration.ofHours(24).toMinutes();
                }

                if (minutes <= 0) {
                        return null;
                }

                return distanceKm / (minutes / 60.0);
        }

        private List<String> cap(List<String> samples) {
                return samples.size() <= MAX_SAMPLES ? samples : new ArrayList<>(samples.subList(0, MAX_SAMPLES));
        }
}
