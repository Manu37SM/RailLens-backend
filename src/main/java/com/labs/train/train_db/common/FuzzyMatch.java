package com.labs.train.train_db.common;

/**
 * Plain-Java Levenshtein distance, used as a fallback when the primary
 * {@code LIKE '%query%'} search (see TrainRepository/StationRepository)
 * finds nothing - e.g. the user typed "Rajdani" instead of "Rajdhani".
 * Deliberately NOT a Postgres trigram (pg_trgm) approach: this project has
 * no migration tool (no Flyway/Liquibase, see CLAUDE.md/project memory -
 * ddl-auto=update only), so adding a database extension would need a
 * manual one-off DBA step outside the app's normal deploy path. Plain-Java
 * distance avoids that entirely at the cost of being O(n*m) per
 * comparison - acceptable here because it only runs on the rare
 * zero-exact-results path, over a small, cached candidate list (see
 * TrainService#fuzzySearchIndex / StationService#fuzzySearchIndex), not on
 * every search request.
 */
public final class FuzzyMatch {

    private FuzzyMatch() {
    }

    /**
     * How many edits (insert/delete/substitute) a query is allowed to be
     * off by before a candidate is considered a typo match rather than
     * noise. Scales with query length so a 2-character query doesn't match
     * half the database, and a long query tolerates a couple more typos.
     */
    public static int maxDistanceFor(int queryLength) {
        if (queryLength <= 3) {
            return 1;
        }
        if (queryLength <= 6) {
            return 2;
        }
        return 3;
    }

    public static int distance(String a, String b) {

        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];

        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {

            current[0] = i;

            for (int j = 1; j <= b.length(); j++) {

                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;

                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost);
            }

            int[] swap = previous;
            previous = current;
            current = swap;
        }

        return previous[b.length()];
    }
}
