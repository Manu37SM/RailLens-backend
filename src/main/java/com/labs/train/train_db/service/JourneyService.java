package com.labs.train.train_db.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.labs.train.train_db.entity.TrainSchedule;
import com.labs.train.train_db.model.JourneySearchResponse;
import com.labs.train.train_db.model.JourneyTrainResponse;
import com.labs.train.train_db.repository.TrainScheduleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JourneyService {

        // Every other list endpoint in this codebase caps its response size
        // (RANKED_LIST_SIZE, MAX_RESULTS, SEARCH_PAGE_SIZE,
        // PaginationConfig.MAX_PAGE_SIZE) - this one didn't, and unlike
        // those, its size scales with real train traffic through two
        // stations rather than a fixed dataset-wide top-N, so a pair of
        // very high-traffic junctions could return an unbounded response.
        // totalTrains still reports the true match count even when the
        // returned list is capped, so the frontend/mobile can show "50 of
        // 240 shown" rather than silently look wrong.
        private static final int MAX_RESULTS = 100;

        private final TrainScheduleRepository trainScheduleRepository;
        private final JourneyDayCalculator journeyDayCalculator;

        public JourneySearchResponse search(String from, String to) {

                log.info("Searching journeys from {} to {}", from, to);
                from = from.trim().toUpperCase();
                to = to.trim().toUpperCase();

                List<TrainSchedule> sourceSchedules = trainScheduleRepository.findByStation_StationCode(from);

                List<TrainSchedule> destinationSchedules = trainScheduleRepository.findByStation_StationCode(to);

                log.info("Source schedules: {}", sourceSchedules.size());
                log.info("Destination schedules: {}", destinationSchedules.size());

                Map<Long, TrainSchedule> destinationMap = destinationSchedules.stream()
                                .collect(Collectors.toMap(
                                                schedule -> schedule.getTrain().getId(),
                                                Function.identity()));

                // Filter down to the trains that actually qualify (correct
                // direction, both stops have a known distance) before touching
                // the schedule table again, so the batch route fetch below only
                // asks for data we're actually going to use.
                List<TrainSchedule> matchedSources = new ArrayList<>();

                for (TrainSchedule source : sourceSchedules) {

                        TrainSchedule destination = destinationMap.get(source.getTrain().getId());

                        if (destination == null) {
                                continue;
                        }

                        if (source.getSequenceNo() >= destination.getSequenceNo()) {
                                continue;
                        }

                        if (source.getDistance() == null || destination.getDistance() == null) {
                                continue;
                        }

                        matchedSources.add(source);
                }

                if (matchedSources.isEmpty()) {
                        return new JourneySearchResponse(from, to, 0, List.of());
                }

                /*
                 * Previously each matched train issued its own
                 * findByTrain_TrainNumberOrderBySequenceNo query inside the loop
                 * below - an N+1 query pattern that scales with the number of
                 * trains between the two stations (backend architecture
                 * review). Fetching every matched train's full route in one
                 * query and grouping in memory turns that into a single round
                 * trip regardless of result size.
                 */
                List<Long> matchedTrainIds = matchedSources.stream()
                                .map(source -> source.getTrain().getId())
                                .distinct()
                                .toList();

                Map<Long, List<TrainSchedule>> routesByTrainId = trainScheduleRepository
                                .findByTrain_IdInOrderByTrain_IdAscSequenceNoAsc(matchedTrainIds)
                                .stream()
                                .collect(Collectors.groupingBy(
                                                schedule -> schedule.getTrain().getId()));

                // Paired with its duration in minutes purely for sorting below -
                // JourneyTrainResponse itself only carries the formatted string,
                // since that's all the frontend has ever needed to display.
                record ScoredJourney(JourneyTrainResponse response, long durationMinutes) {
                }

                List<ScoredJourney> scored = new ArrayList<>();

                for (TrainSchedule source : matchedSources) {

                        TrainSchedule destination = destinationMap.get(source.getTrain().getId());

                        int distance = destination.getDistance() - source.getDistance();

                        List<TrainSchedule> route = routesByTrainId.get(source.getTrain().getId());

                        JourneySegmentAnalysis analysis = analyzeSegment(route, source, destination, distance);

                        scored.add(new ScoredJourney(
                                        new JourneyTrainResponse(
                                                        source.getTrain().getTrainNumber(),
                                                        source.getTrain().getTrainName(),
                                                        source.getDepartureTime(),
                                                        destination.getArrivalTime(),
                                                        formatDuration(analysis.durationMinutes()),
                                                        distance,
                                                        analysis.movingMinutes(),
                                                        analysis.haltedMinutes(),
                                                        analysis.numHalts(),
                                                        analysis.longestHaltMinutes(),
                                                        analysis.averageMovingSpeedKmh(),
                                                        analysis.nightTravelPercent(),
                                                        analysis.dayTravelPercent()),
                                        analysis.durationMinutes()));
                }

                // Fastest first. Journeys with an unknown duration (missing
                // arrival/departure time in the source data) sort last rather
                // than first or being silently dropped - calculateDurationMinutes
                // returns Long.MAX_VALUE for those, see its javadoc.
                // Explicit lambdas rather than ScoredJourney::durationMinutes /
                // ScoredJourney::response - the method-reference form on this
                // local record trips the JDT null analyzer's "unchecked
                // conversion for the receiver" warning; same behavior either
                // way, this just avoids the false-positive warning.
                List<JourneyTrainResponse> journeys = scored.stream()
                                .sorted(java.util.Comparator.comparingLong((ScoredJourney sj) -> sj.durationMinutes()))
                                .limit(MAX_RESULTS)
                                .map(sj -> sj.response())
                                .toList();

                return new JourneySearchResponse(
                                from,
                                to,
                                scored.size(),
                                journeys);
        }

        /**
         * "Journey Analysis" (FEATURE.md) - everything JourneyTrainResponse
         * reports beyond the original duration/distance, scoped to this one
         * source-to-destination leg rather than the train's whole route (see
         * TrainIntelligenceService for the whole-route versions of the same
         * ideas). {@code durationMinutes} keeps the original
         * {@code Long.MAX_VALUE}-for-unknown sentinel (see the old
         * calculateDurationMinutes this replaces) so sorting by it still
         * pushes unknown-duration journeys last; the other fields fall back
         * to null/zero when times are missing, since there's nothing
         * meaningful to report without them.
         */
        private record JourneySegmentAnalysis(
                        long durationMinutes,
                        long movingMinutes,
                        long haltedMinutes,
                        int numHalts,
                        Long longestHaltMinutes,
                        Double averageMovingSpeedKmh,
                        Double nightTravelPercent,
                        Double dayTravelPercent) {
        }

        private JourneySegmentAnalysis analyzeSegment(
                        List<TrainSchedule> route,
                        TrainSchedule source,
                        TrainSchedule destination,
                        int distanceKm) {

                List<Integer> journeyDays = journeyDayCalculator.computeJourneyDays(route);

                int sourceIndex = -1;
                int destinationIndex = -1;

                for (int i = 0; i < route.size(); i++) {

                        Integer sequenceNo = route.get(i).getSequenceNo();

                        if (sequenceNo.equals(source.getSequenceNo())) {
                                sourceIndex = i;
                        }

                        if (sequenceNo.equals(destination.getSequenceNo())) {
                                destinationIndex = i;
                        }
                }

                int numHalts = sourceIndex >= 0 && destinationIndex > sourceIndex
                                ? destinationIndex - sourceIndex - 1
                                : 0;

                if (source.getDepartureTime() == null || destination.getArrivalTime() == null
                                || sourceIndex < 0 || destinationIndex < 0) {
                        return new JourneySegmentAnalysis(Long.MAX_VALUE, 0, 0, numHalts, null, null, null, null);
                }

                int sourceDay = journeyDays.get(sourceIndex);
                int destinationDay = journeyDays.get(destinationIndex);

                long durationMinutes = journeyDayCalculator.minutesBetween(
                                sourceDay, source.getDepartureTime(),
                                destinationDay, destination.getArrivalTime());

                long haltedMinutes = 0;
                Long longestHalt = null;

                LocalDate base = LocalDate.of(2000, 1, 1);
                long totalMovingMinutes = 0;
                long nightMinutes = 0;

                for (int i = sourceIndex; i < destinationIndex; i++) {

                        TrainSchedule from = route.get(i);
                        TrainSchedule to = route.get(i + 1);

                        // The moving leg from this stop's departure to the next
                        // stop's arrival - excludes the halt at `to` (added
                        // separately below), same "halt vs. moving" split
                        // TrainIntelligenceService uses for the whole route.
                        if (from.getDepartureTime() != null && to.getArrivalTime() != null) {

                                LocalDateTime legStart = LocalDateTime.of(base.plusDays(journeyDays.get(i) - 1), from.getDepartureTime());
                                LocalDateTime legEnd = LocalDateTime.of(base.plusDays(journeyDays.get(i + 1) - 1), to.getArrivalTime());

                                if (legEnd.isAfter(legStart)) {
                                        long legMinutes = Duration.between(legStart, legEnd).toMinutes();
                                        totalMovingMinutes += legMinutes;
                                        nightMinutes += NightWindowCalculator.nightMinutesInRange(legStart, legEnd);
                                }
                        }

                        // Halt at `to`, but only if `to` is an intermediate stop
                        // (not the destination itself - alighting there isn't a
                        // "halt" on this leg).
                        if (i + 1 < destinationIndex
                                        && to.getArrivalTime() != null && to.getDepartureTime() != null
                                        && !to.getDepartureTime().isBefore(to.getArrivalTime())) {

                                long haltMinutes = Duration.between(to.getArrivalTime(), to.getDepartureTime()).toMinutes();
                                haltedMinutes += haltMinutes;
                                longestHalt = longestHalt == null ? haltMinutes : Math.max(longestHalt, haltMinutes);
                        }
                }

                Double averageMovingSpeedKmh = totalMovingMinutes > 0 && distanceKm > 0
                                ? Math.round(distanceKm / (totalMovingMinutes / 60.0) * 10.0) / 10.0
                                : null;

                Double nightPercent = null;
                Double dayPercent = null;

                if (totalMovingMinutes > 0) {
                        nightPercent = Math.round(nightMinutes * 1000.0 / totalMovingMinutes) / 10.0;
                        dayPercent = Math.round((100.0 - nightPercent) * 10.0) / 10.0;
                }

                return new JourneySegmentAnalysis(
                                durationMinutes, totalMovingMinutes, haltedMinutes, numHalts, longestHalt,
                                averageMovingSpeedKmh, nightPercent, dayPercent);
        }

        private String formatDuration(long minutes) {

                if (minutes == Long.MAX_VALUE) {
                        return "";
                }

                long hours = minutes / 60;
                long remainingMinutes = minutes % 60;

                return String.format("%dh %02dm", hours, remainingMinutes);
        }

}