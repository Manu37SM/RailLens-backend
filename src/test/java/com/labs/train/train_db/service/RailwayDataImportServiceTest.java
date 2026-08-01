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

/**
 * importCsv() itself reads a fixed classpath resource
 * ("data/train_dataset.csv") rather than taking an injected reader, so a
 * full end-to-end test of it would need a real CSV fixture on the test
 * classpath and a real (or very elaborately mocked) persistence layer -
 * out of scope for a fast unit test, and risky to fake without a working
 * database to verify against. The actual per-row persistence logic now
 * lives in {@link RailwayImportBatchService} (see that class's javadoc and
 * {@code RailwayImportBatchServiceTest}) since the transaction-per-batch
 * refactor split parsing (here) from persisting (there).
 *
 * These tests instead target the three private parsing helpers
 * (cleanText/parseTime/parseInteger) via reflection - the actual
 * per-row logic most likely to break on real-world messy CSV input
 * (trailing commas, quoted fields, "NA" sentinels, malformed times), and
 * therefore the highest-value, lowest-risk part of this class to cover.
 */
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
                // parseInteger now fails soft, the same as parseTime - a
                // malformed (non-blank, non-"NA") value logs a warning and
                // returns null rather than propagating a NumberFormatException,
                // so one bad Distance cell doesn't need special-case handling
                // by importCsv()'s per-row catch block.
                assertThat(parseInteger("not-a-number")).isNull();
        }

        @Test
        void parseIntegerParsesWholeNumbersWrittenWithATrailingDecimal() {
                // Some source CSVs write whole numbers as e.g. "245.0" - see
                // the 2026-08-01 import failure this was added for.
                assertThat(parseInteger("245.0")).isEqualTo(245);
        }
}
