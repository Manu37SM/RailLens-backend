package com.labs.train.train_db.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.labs.train.train_db.config.CacheConfig;
import com.labs.train.train_db.entity.Station;
import com.labs.train.train_db.entity.TrainSchedule;
import com.labs.train.train_db.model.FunStatsResponse;
import com.labs.train.train_db.model.FunStatsResponse.StationNameEntry;
import com.labs.train.train_db.model.FunStatsResponse.TrainStopEntry;
import com.labs.train.train_db.model.FunStatsResponse.WordFrequency;
import com.labs.train.train_db.repository.StationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FunStatsService {

        private final StationRepository stationRepository;
        private final ScheduleSnapshotService scheduleSnapshotService;

        @Cacheable(cacheNames = CacheConfig.FUN_STATS_CACHE)
        public FunStatsResponse getFunStats() {

                List<Station> stations = stationRepository.findAll();

                StationNameEntry longest = null;
                StationNameEntry shortest = null;

                Map<String, Integer> wordCounts = new LinkedHashMap<>();
                Map<String, Integer> firstLetterCounts = new TreeMap<>();

                for (char letter = 'A'; letter <= 'Z'; letter++) {
                        firstLetterCounts.put(String.valueOf(letter), 0);
                }

                List<String> palindromes = new ArrayList<>();

                for (Station station : stations) {

                        String name = station.getStationName();

                        if (name == null || name.isBlank()) {
                                continue;
                        }

                        int length = name.trim().length();

                        if (longest == null || length > longest.length()) {
                                longest = new StationNameEntry(station.getStationCode(), name, length);
                        }

                        if (shortest == null || length < shortest.length()) {
                                shortest = new StationNameEntry(station.getStationCode(), name, length);
                        }

                        for (String word : name.split("\\s+")) {

                                String normalized = word.replaceAll("[^A-Za-z]", "").toLowerCase(Locale.ROOT);

                                if (normalized.length() < 2) {
                                        continue;
                                }

                                wordCounts.merge(normalized, 1, (a, b) -> a + b);
                        }

                        char firstLetter = Character.toUpperCase(name.trim().charAt(0));

                        if (firstLetter >= 'A' && firstLetter <= 'Z') {
                                firstLetterCounts.merge(String.valueOf(firstLetter), 1, (a, b) -> a + b);
                        }

                        String code = station.getStationCode();

                        if (code != null && code.length() >= 2 && isPalindrome(code)) {
                                palindromes.add(code);
                        }
                }

                WordFrequency mostCommonWord = wordCounts.entrySet().stream()
                                .max(Comparator
                                                .<Map.Entry<String, Integer>>comparingInt(entry -> entry.getValue())
                                                .thenComparing((Map.Entry<String, Integer> entry) -> entry.getKey(),
                                                                Comparator.reverseOrder()))
                                .map(entry -> new WordFrequency(entry.getKey(), entry.getValue()))
                                .orElse(null);

                TrainStopEntry trainWithMostUniqueStations = mostUniqueStationsTrain();

                return new FunStatsResponse(
                                longest, shortest, mostCommonWord, firstLetterCounts,
                                trainWithMostUniqueStations, palindromes);
        }

        private boolean isPalindrome(String code) {

                String upper = code.toUpperCase(Locale.ROOT);
                int left = 0;
                int right = upper.length() - 1;

                while (left < right) {
                        if (upper.charAt(left) != upper.charAt(right)) {
                                return false;
                        }
                        left++;
                        right--;
                }

                return true;
        }

        private TrainStopEntry mostUniqueStationsTrain() {

                Map<Long, List<TrainSchedule>> schedulesByTrainId = scheduleSnapshotService
                                .getAllOrderedByTrainThenSequence()
                                .stream()
                                .collect(Collectors.groupingBy(schedule -> schedule.getTrain().getId()));

                TrainStopEntry best = null;

                for (List<TrainSchedule> route : schedulesByTrainId.values()) {

                        if (route.isEmpty()) {
                                continue;
                        }

                        long uniqueCount = route.stream()
                                        .map(schedule -> schedule.getStation().getStationCode())
                                        .distinct()
                                        .count();

                        if (best == null || uniqueCount > best.uniqueStationCount()) {
                                best = new TrainStopEntry(
                                                route.get(0).getTrain().getTrainNumber(),
                                                route.get(0).getTrain().getTrainName(),
                                                (int) uniqueCount);
                        }
                }

                return best;
        }
}
