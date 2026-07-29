package com.labs.train.train_db.service;

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

                        long durationMinutes = calculateDurationMinutes(route, source, destination);

                        scored.add(new ScoredJourney(
                                        new JourneyTrainResponse(
                                                        source.getTrain().getTrainNumber(),
                                                        source.getTrain().getTrainName(),
                                                        source.getDepartureTime(),
                                                        destination.getArrivalTime(),
                                                        formatDuration(durationMinutes),
                                                        distance),
                                        durationMinutes));
                }

                // Fastest first. Journeys with an unknown duration (missing
                // arrival/departure time in the source data) sort last rather
                // than first or being silently dropped - calculateDurationMinutes
                // returns Long.MAX_VALUE for those, see its javadoc.
                List<JourneyTrainResponse> journeys = scored.stream()
                                .sorted(java.util.Comparator.comparingLong(ScoredJourney::durationMinutes))
                                .map(ScoredJourney::response)
                                .toList();

                return new JourneySearchResponse(
                                from,
                                to,
                                journeys.size(),
                                journeys);
        }

        /**
         * Returns {@code Long.MAX_VALUE} (not -1 or null) for an unknown
         * duration specifically so a plain ascending sort naturally pushes
         * these to the end without every caller needing a null-check.
         */
        private long calculateDurationMinutes(
                        List<TrainSchedule> route,
                        TrainSchedule source,
                        TrainSchedule destination) {

                if (source.getDepartureTime() == null || destination.getArrivalTime() == null) {
                        return Long.MAX_VALUE;
                }

                List<Integer> journeyDays = journeyDayCalculator.computeJourneyDays(route);

                int sourceDay = 1;
                int destinationDay = 1;

                for (int i = 0; i < route.size(); i++) {

                        Integer sequenceNo = route.get(i).getSequenceNo();

                        if (sequenceNo.equals(source.getSequenceNo())) {
                                sourceDay = journeyDays.get(i);
                        }

                        if (sequenceNo.equals(destination.getSequenceNo())) {
                                destinationDay = journeyDays.get(i);
                        }
                }

                return journeyDayCalculator.minutesBetween(
                                sourceDay, source.getDepartureTime(),
                                destinationDay, destination.getArrivalTime());
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