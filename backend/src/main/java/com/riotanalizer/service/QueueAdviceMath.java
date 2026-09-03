package com.riotanalizer.service;

/**
 * Pure helpers for the "should I queue again?" advice. Dependency-free (JDK only)
 * so they can be unit tested without Spring or a DB. The service layer gathers the
 * historical buckets and current session state; these functions turn them into a
 * predicted win rate and a GO / CAUTION / STOP signal.
 */
public final class QueueAdviceMath {

    private QueueAdviceMath() {
    }

    /**
     * Shrink a bucket's win rate toward the player's overall win rate by a
     * pseudo-count {@code k}, so thin buckets don't dominate. With 0 games it
     * returns the overall win rate exactly.
     */
    public static double shrink(int games, double bucketWinRate, double overallWinRate, double k) {
        return (games * bucketWinRate + k * overallWinRate) / (games + k);
    }

    /** Weighted average of parallel value/weight arrays; 0 when all weights are 0. */
    public static double weightedAverage(double[] values, double[] weights) {
        double sum = 0, wsum = 0;
        for (int i = 0; i < values.length; i++) {
            sum += values[i] * weights[i];
            wsum += weights[i];
        }
        return wsum <= 0 ? 0 : sum / wsum;
    }

    /**
     * Session position the next game would take: one past the last game's position
     * if it starts within {@code gapMs} of the last game's end, otherwise 1 (a new
     * session). Returns 1 when there is no prior game ({@code lastPos <= 0}).
     */
    public static int nextSessionPosition(long lastEndMs, long nowMs, int lastPos, long gapMs) {
        if (lastPos <= 0) {
            return 1;
        }
        return (nowMs - lastEndMs) <= gapMs ? lastPos + 1 : 1;
    }

    /**
     * GO / CAUTION / STOP from the predicted win rate plus red flags. A losing
     * streak deep into a session is a hard stop; a healthy prediction with no
     * active losing streak is a go; everything else is caution.
     */
    public static String signal(double predicted, int streak, int nextPos) {
        if (predicted < 45.0 || (streak <= -3 && nextPos >= 4)) {
            return "STOP";
        }
        if (predicted >= 52.0 && streak > -2) {
            return "GO";
        }
        return "CAUTION";
    }
}
