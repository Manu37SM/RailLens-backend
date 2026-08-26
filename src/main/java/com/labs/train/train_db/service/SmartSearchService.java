package com.labs.train.train_db.service;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.labs.train.train_db.entity.Station;
import com.labs.train.train_db.model.SmartSearchResponse;
import com.labs.train.train_db.model.TrainSearchResponse;
import com.labs.train.train_db.repository.StationRepository;
import com.labs.train.train_db.service.TrainSummaryIndex.TrainSummary;
import com.labs.train.train_db.service.SmartSearchQueryParser.FewerThanHalts;
import com.labs.train.train_db.service.SmartSearchQueryParser.FromTo;
import com.labs.train.train_db.service.SmartSearchQueryParser.LongerThanHours;
import com.labs.train.train_db.service.SmartSearchQueryParser.LongerThanKm;
import com.labs.train.train_db.service.SmartSearchQueryParser.MoreThanHalts;
import com.labs.train.train_db.service.SmartSearchQueryParser.ParsedQuery;
import com.labs.train.train_db.service.SmartSearchQueryParser.ShorterThanHours;
import com.labs.train.train_db.service.SmartSearchQueryParser.ShorterThanKm;
import com.labs.train.train_db.service.SmartSearchQueryParser.StopsAt;
import com.labs.train.train_db.service.SmartSearchQueryParser.StopsAtBoth;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SmartSearchService {

        private static final int MAX_RESULTS = 50;

        private final StationRepository stationRepository;
        private final TrainSummaryIndex trainSummaryIndex;

        public SmartSearchResponse search(String query) {

                ParsedQuery parsed = SmartSearchQueryParser.parse(query);

                if (parsed == null) {
                        return new SmartSearchResponse(false, null, 0, List.of());
                }

                List<TrainSummary> index = trainSummaryIndex.buildIndex();

                return switch (parsed) {
                        case StopsAtBoth(String stationA, String stationB) -> stopsAtBoth(index, stationA, stationB);
                        case StopsAt(String station) -> stopsAt(index, station);
                        case FromTo(String from, String to) -> fromTo(index, from, to);
                        case LongerThanHours(int hours) -> filterAndRespond(
                                        index, s -> s.journeyMinutes() > hours * 60L,
                                        "Trains longer than " + hours + " hour(s)");
                        case ShorterThanHours(int hours) -> filterAndRespond(
                                        index, s -> s.journeyMinutes() > 0 && s.journeyMinutes() < hours * 60L,
                                        "Trains shorter than " + hours + " hour(s)");
                        case LongerThanKm(int km) -> filterAndRespond(
                                        index, s -> s.distanceKm() > km, "Trains longer than " + km + " km");
                        case ShorterThanKm(int km) -> filterAndRespond(
                                        index, s -> s.distanceKm() > 0 && s.distanceKm() < km,
                                        "Trains shorter than " + km + " km");
                        case MoreThanHalts(int halts) -> filterAndRespond(
                                        index, s -> s.halts() > halts, "Trains with more than " + halts + " halt(s)");
                        case FewerThanHalts(int halts) -> filterAndRespond(
                                        index, s -> s.halts() < halts, "Trains with fewer than " + halts + " halt(s)");
                };
        }

        private Optional<String> resolveStation(String token) {

                String candidate = token.trim();

                if (candidate.isEmpty()) {
                        return Optional.empty();
                }

                Optional<Station> byCode = stationRepository.findByStationCode(candidate.toUpperCase());

                if (byCode.isPresent()) {
                        return byCode.map((Station station) -> station.getStationCode());
                }

                return stationRepository.search(candidate, PageRequest.of(0, 1))
                                .stream()
                                .findFirst()
                                .map((Station station) -> station.getStationCode());
        }

        private SmartSearchResponse stopsAtBoth(List<TrainSummary> index, String tokenA, String tokenB) {

                Optional<String> codeA = resolveStation(tokenA);
                Optional<String> codeB = resolveStation(tokenB);

                if (codeA.isEmpty() || codeB.isEmpty()) {
                        String missing = codeA.isEmpty() ? tokenA : tokenB;
                        return new SmartSearchResponse(true, "Could not find a station matching \"" + missing + "\"", 0, List.of());
                }

                return filterAndRespond(
                                index,
                                s -> s.stationCodes().contains(codeA.get()) && s.stationCodes().contains(codeB.get()),
                                "Trains that stop at both " + codeA.get() + " and " + codeB.get());
        }

        private SmartSearchResponse stopsAt(List<TrainSummary> index, String token) {

                Optional<String> code = resolveStation(token);

                if (code.isEmpty()) {
                        return new SmartSearchResponse(true, "Could not find a station matching \"" + token + "\"", 0, List.of());
                }

                return filterAndRespond(
                                index, s -> s.stationCodes().contains(code.get()), "Trains that stop at " + code.get());
        }

        private SmartSearchResponse fromTo(List<TrainSummary> index, String fromToken, String toToken) {

                Optional<String> from = resolveStation(fromToken);
                Optional<String> to = resolveStation(toToken);

                if (from.isEmpty() || to.isEmpty()) {
                        String missing = from.isEmpty() ? fromToken : toToken;
                        return new SmartSearchResponse(true, "Could not find a station matching \"" + missing + "\"", 0, List.of());
                }

                return filterAndRespond(
                                index,
                                s -> s.stationCodes().contains(from.get()) && s.stationCodes().contains(to.get()),
                                "Trains from " + from.get() + " to " + to.get());
        }

        private SmartSearchResponse filterAndRespond(
                        List<TrainSummary> index, java.util.function.Predicate<TrainSummary> predicate, String interpretedAs) {

                List<TrainSummary> matches = index.stream().filter(predicate).toList();

                List<TrainSearchResponse> trains = matches.stream()
                                .limit(MAX_RESULTS)
                                .map(s -> new TrainSearchResponse(s.trainNumber(), s.trainName()))
                                .toList();

                return new SmartSearchResponse(true, interpretedAs, matches.size(), trains);
        }
}
