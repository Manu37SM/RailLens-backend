package com.labs.train.train_db.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FuzzyMatchTest {

    @Test
    void identicalStringsHaveZeroDistance() {
        assertThat(FuzzyMatch.distance("rajdhani", "rajdhani")).isEqualTo(0);
    }

    @Test
    void singleSubstitutionHasDistanceOne() {
        assertThat(FuzzyMatch.distance("rajdhani", "rajdgani")).isEqualTo(1);
    }

    @Test
    void singleCharacterDropHasDistanceOne() {
        assertThat(FuzzyMatch.distance("rajdhani", "rajdani")).isEqualTo(1);
    }

    @Test
    void completelyDifferentStringsHaveDistanceCloseToLength() {
        assertThat(FuzzyMatch.distance("delhi", "zzzzz")).isEqualTo(5);
    }

    @Test
    void emptyStringDistanceIsOtherStringsLength() {
        assertThat(FuzzyMatch.distance("", "abc")).isEqualTo(3);
        assertThat(FuzzyMatch.distance("abc", "")).isEqualTo(3);
    }

    @Test
    void thresholdScalesWithQueryLength() {
        assertThat(FuzzyMatch.maxDistanceFor(2)).isEqualTo(1);
        assertThat(FuzzyMatch.maxDistanceFor(3)).isEqualTo(1);
        assertThat(FuzzyMatch.maxDistanceFor(4)).isEqualTo(2);
        assertThat(FuzzyMatch.maxDistanceFor(6)).isEqualTo(2);
        assertThat(FuzzyMatch.maxDistanceFor(7)).isEqualTo(3);
        assertThat(FuzzyMatch.maxDistanceFor(20)).isEqualTo(3);
    }
}
