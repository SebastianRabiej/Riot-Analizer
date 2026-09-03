package com.riotanalizer.service;

/**
 * Small pure helpers for the stat formulas defined in the API contract.
 * Kept dependency-free so they can be unit tested without Spring or a DB.
 */
public final class StatMath {

    private StatMath() {
    }

    /** kda = (kills + assists) / max(1, deaths). */
    public static double kda(long kills, long deaths, long assists) {
        return (kills + assists) / (double) Math.max(1, deaths);
    }

    /** winRate as a 0..100 percentage; 0 when no games. */
    public static double winRate(int wins, int games) {
        if (games <= 0) {
            return 0.0;
        }
        return round2(wins * 100.0 / games);
    }

    /** csPerMin = totalCs / (durationSec / 60); 0 when duration is 0. */
    public static double csPerMin(long totalCs, long durationSec) {
        if (durationSec <= 0) {
            return 0.0;
        }
        return totalCs / (durationSec / 60.0);
    }

    public static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    public static double avg(long sum, int count) {
        if (count <= 0) {
            return 0.0;
        }
        return round2((double) sum / count);
    }

    /** part as a 0..100 percentage of total; 0 when total is 0. */
    public static double share(long part, long total) {
        if (total <= 0) {
            return 0.0;
        }
        return round2(part * 100.0 / total);
    }
}
