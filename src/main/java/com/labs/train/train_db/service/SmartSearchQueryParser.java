package com.labs.train.train_db.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
