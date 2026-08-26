package com.labs.train.train_db.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.labs.train.train_db.repository.StationRepository;
import com.labs.train.train_db.repository.TrainRepository;
import org.springframework.cache.CacheManager;

@ExtendWith(MockitoExtension.class)
class RailwayDataImportServiceTest {

        @Mock
        private StationRepository stationRepository;

        @Mock
        private TrainRepository trainRepository;

        @Mock
        private CacheManager cacheManager;

        @Mock
        private RailwayImportBatchService batchService;

        private RailwayDataImportService service() {
                return new RailwayDataImportService(
                                stationRepository, trainRepository, cacheManager, batchService);
        }

        private String cleanText(String value) {
                return ReflectionTestUtils.invokeMethod(service(), "cleanText", value);
        }

        private LocalTime parseTime(String value) {
                return ReflectionTestUtils.invokeMethod(service(), "parseTime", value);
        }

        private Integer parseInteger(String value) {
                return ReflectionTestUtils.invokeMethod(service(), "parseInteger", value);
        }

        @Test
        void cleanTextTrimsWhitespace() {
                assertThat(cleanText("  NDLS  ")).isEqualTo("NDLS");
        }

        @Test
        void cleanTextStripsSurroundingQuotes() {
                assertThat(cleanText("\"New Delhi\"")).isEqualTo("New Delhi");
        }

        @Test
        void cleanTextStripsTrailingCommasLeftBehindByMalformedRows() {
                assertThat(cleanText("New Delhi,,,")).isEqualTo("New Delhi");
        }

        @Test
        void cleanTextReturnsNullForNullInput() {
                assertThat(cleanText(null)).isNull();
        }

        @Test
        void cleanTextLeavesAlreadyCleanTextUnchanged() {
                assertThat(cleanText("Rajdhani Express")).isEqualTo("Rajdhani Express");
        }

        @Test
        void parseTimeParsesAValidHMTime() {
                assertThat(parseTime("8:05")).isEqualTo(LocalTime.of(8, 5));
                assertThat(parseTime("23:59")).isEqualTo(LocalTime.of(23, 59));
        }

        @Test
        void parseTimeReturnsNullForBlankOrNaOrNull() {
                assertThat(parseTime(null)).isNull();
                assertThat(parseTime("")).isNull();
                assertThat(parseTime("  ")).isNull();
                assertThat(parseTime("NA")).isNull();
                assertThat(parseTime("na")).isNull();
        }

        @Test
        void parseTimeReturnsNullRatherThanThrowingForGarbageInput() {
                assertThat(parseTime("not-a-time")).isNull();
                assertThat(parseTime("25:99")).isNull();
        }

        @Test
        void parseIntegerParsesAValidNumber() {
                assertThat(parseInteger("1447")).isEqualTo(1447);
        }

        @Test
        void parseIntegerReturnsNullForBlankOrNaOrNull() {
                assertThat(parseInteger(null)).isNull();
                assertThat(parseInteger("")).isNull();
                assertThat(parseInteger("NA")).isNull();
        }

        @Test
        void parseIntegerReturnsNullRatherThanThrowingForGarbageInput() {
                assertThat(parseInteger("not-a-number")).isNull();
        }

        @Test
        void parseIntegerParsesWholeNumbersWrittenWithATrailingDecimal() {
                assertThat(parseInteger("245.0")).isEqualTo(245);
        }
}
