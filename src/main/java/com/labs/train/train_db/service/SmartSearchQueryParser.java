package com.labs.train.train_db.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Smart Search" (FEATURE.md) - a small, fixed grammar of natural-language-
 * ish query shapes ("trains that stop at both X and Y", "trains longer than
 * 10 hours"), not a general NLP/LLM query understanding system (this
 * project deliberately uses no external APIs - see FEATURE.md's Travel
 * Tools note - and a real intent parser is out of scope for a dataset
 * browser). Each recognized shape is a single regex; the first one that
 * matches wins, in the order below (more specific patterns are checked
 * before more general ones that would otherwise shadow them - e.g. "stop at
 * both X and Y" must be tried before the single-station "stop at X"
 * pattern, since the latter's greedy capture would otherwise swallow "both
 * X and Y" as one station name).
 *
 * Station/number tokens are only extracted here, not resolved - resolving a
 * station token to an actual station code requires a database lookup, which
 * belongs in SmartSearchService, not this stateless parser.
 */
final class SmartSearchQueryParser {

        private SmartSearchQueryParser() {
        }

        sealed interface ParsedQuery
                        permits StopsAtBoth, StopsAt, FromTo, LongerThanHours, ShorterThanHours,
                        LongerThanKm, ShorterThanKm, MoreThanHalts, FewerThanHalts {
        }

        record StopsAtBoth(String stationA, String stationB) implements ParsedQuery {
        }

        record StopsAt(String station) implements ParsedQuery {
        }

        record FromTo(String from, String to) implements ParsedQuery {
        }

        record LongerThanHours(int hours) implements ParsedQuery {
        }

        record ShorterThanHours(int hours) implements ParsedQuery {
        }

        record LongerThanKm(int km) implements ParsedQuery {
        }

        record ShorterThanKm(int km) implements ParsedQuery {
        }

        record MoreThanHalts(int halts) implements ParsedQuery {
        }

        record FewerThanHalts(int halts) implements ParsedQuery {
        }

        private static final Pattern STOPS_AT_BOTH = Pattern.compile(
                        "trains?\\s+(?:that\\s+)?stop(?:s)?\\s+at\\s+both\\s+(.+?)\\s+and\\s+(.+)",
                        Pattern.CASE_INSENSITIVE);

        private static final Pattern STOPS_AT = Pattern.compile(
                        "trains?\\s+(?:that\\s+)?stop(?:s)?\\s+at\\s+(.+)",
                        Pattern.CASE_INSENSITIVE);

        private static final Pattern FROM_TO = Pattern.compile(
                        "trains?\\s+from\\s+(.+?)\\s+to\\s+(.+)",
                        Pattern.CASE_INSENSITIVE);

        private static final Pattern LONGER_THAN_HOURS = Pattern.compile(
                        "trains?\\s+longer\\s+than\\s+(\\d+)\\s*h(?:ou)?rs?",
                        Pattern.CASE_INSENSITIVE);

        private static final Pattern SHORTER_THAN_HOURS = Pattern.compile(
                        "trains?\\s+shorter\\s+than\\s+(\\d+)\\s*h(?:ou)?rs?",
                        Pattern.CASE_INSENSITIVE);

        private static final Pattern LONGER_THAN_KM = Pattern.compile(
                        "trains?\\s+longer\\s+than\\s+(\\d+)\\s*km",
                        Pattern.CASE_INSENSITIVE);

        private static final Pattern SHORTER_THAN_KM = Pattern.compile(
                        "trains?\\s+shorter\\s+than\\s+(\\d+)\\s*km",
                        Pattern.CASE_INSENSITIVE);

        private static final Pattern MORE_THAN_HALTS = Pattern.compile(
                        "trains?\\s+with\\s+more\\s+than\\s+(\\d+)\\s+halts?",
                        Pattern.CASE_INSENSITIVE);

        private static final Pattern FEWER_THAN_HALTS = Pattern.compile(
                        "trains?\\s+with\\s+fewer\\s+than\\s+(\\d+)\\s+halts?",
                        Pattern.CASE_INSENSITIVE);

        static ParsedQuery parse(String query) {

                String trimmed = query.trim();

                Matcher matcher;

                // "longer/shorter than N km" must be tried before "longer/shorter
                // than N hours" would even apply (they're mutually exclusive
                // patterns by unit, so order between them doesn't matter, but
                // both must be tried before the halts patterns since none of
                // these overlap in what they match).
                if ((matcher = STOPS_AT_BOTH.matcher(trimmed)).matches()) {
                        return new StopsAtBoth(matcher.group(1).trim(), matcher.group(2).trim());
                }

                if ((matcher = FROM_TO.matcher(trimmed)).matches()) {
                        return new FromTo(matcher.group(1).trim(), matcher.group(2).trim());
                }

                if ((matcher = STOPS_AT.matcher(trimmed)).matches()) {
                        return new StopsAt(matcher.group(1).trim());
                }

                if ((matcher = LONGER_THAN_HOURS.matcher(trimmed)).matches()) {
                        return new LongerThanHours(Integer.parseInt(matcher.group(1)));
                }

                if ((matcher = SHORTER_THAN_HOURS.matcher(trimmed)).matches()) {
                        return new ShorterThanHours(Integer.parseInt(matcher.group(1)));
                }

                if ((matcher = LONGER_THAN_KM.matcher(trimmed)).matches()) {
                        return new LongerThanKm(Integer.parseInt(matcher.group(1)));
                }

                if ((matcher = SHORTER_THAN_KM.matcher(trimmed)).matches()) {
                        return new ShorterThanKm(Integer.parseInt(matcher.group(1)));
                }

                if ((matcher = MORE_THAN_HALTS.matcher(trimmed)).matches()) {
                        return new MoreThanHalts(Integer.parseInt(matcher.group(1)));
                }

                if ((matcher = FEWER_THAN_HALTS.matcher(trimmed)).matches()) {
                        return new FewerThanHalts(Integer.parseInt(matcher.group(1)));
                }

                return null;
        }
}
