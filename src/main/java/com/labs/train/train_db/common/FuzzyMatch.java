package com.labs.train.train_db.common;

public final class FuzzyMatch {

    private FuzzyMatch() {
    }

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
