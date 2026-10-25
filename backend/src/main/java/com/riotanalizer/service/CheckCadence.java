package com.riotanalizer.service;

import com.riotanalizer.domain.TrackingLevel;

import java.time.Duration;
import java.time.Instant;

/**
 * Pure rules for <em>when</em> a player is stale enough to be worth Riot calls.
 *
 * <p>The idea: a player's history can only change when they play, so the best
 * predictor of "new games since we last looked" is how recently they played.
 * Someone who finished a game 20 minutes ago is probably still queueing;
 * someone whose newest game is two weeks old almost certainly isn't.
 */
public final class CheckCadence {

    /** Re-check a tracked player this soon while they're in a live game (it ends → new match). */
    static final Duration IN_GAME = Duration.ofMinutes(4);

    /** Back-off after a failed check, so one broken player can't hot-loop the scheduler. */
    static final Duration TRACKED_ERROR_BACKOFF = Duration.ofMinutes(5);
    static final Duration DISCOVERED_ERROR_BACKOFF = Duration.ofHours(1);
    static final Duration NOT_FOUND_BACKOFF = Duration.ofDays(7);

    /** Rank/profile can only move after games; this catches decay, renames and icon changes. */
    static final Duration TRACKED_PROFILE_MAX_AGE = Duration.ofHours(6);
    static final Duration DISCOVERED_PROFILE_MAX_AGE = Duration.ofDays(7);
    static final Duration DISCOVERED_PROFILE_MIN_AGE = Duration.ofDays(1);

    private CheckCadence() {
    }

    /**
     * How long to wait before the next check.
     *
     * @param newMatches    matches stored by the check that just ran
     * @param newestMatchAt creation time of the player's newest known match, or null if none this season
     * @param inGame        whether the player is in a live game right now (only known for TRACKED)
     */
    public static Duration nextDelay(TrackingLevel level, boolean backfillComplete, int newMatches,
                                     Instant newestMatchAt, boolean inGame, Instant now) {
        if (!backfillComplete) {
            if (newMatches == 0) {
                // Backfill made no progress (e.g. a match keeps failing): don't
                // spin on it — retry later like any other error.
                return errorBackoff(level);
            }
            // Still walking the season: come back as soon as the scheduler (and,
            // through the rate limiter, the priority order) allows.
            return Duration.ZERO;
        }
        Duration idle = newestMatchAt == null ? null : Duration.between(newestMatchAt, now);
        if (level == TrackingLevel.TRACKED) {
            if (inGame) {
                return IN_GAME;
            }
            if (idle == null) {
                return Duration.ofHours(12);
            }
            if (idle.compareTo(Duration.ofHours(1)) < 0) {
                return Duration.ofMinutes(5);
            }
            if (idle.compareTo(Duration.ofHours(6)) < 0) {
                return Duration.ofMinutes(15);
            }
            if (idle.compareTo(Duration.ofDays(3)) < 0) {
                return Duration.ofHours(1);
            }
            if (idle.compareTo(Duration.ofDays(14)) < 0) {
                return Duration.ofHours(3);
            }
            return Duration.ofHours(12);
        }
        // DISCOVERED: far fewer checks; they only fill in context around tracked players.
        if (idle == null) {
            return Duration.ofDays(3);
        }
        if (idle.compareTo(Duration.ofDays(1)) < 0) {
            return Duration.ofHours(2);
        }
        if (idle.compareTo(Duration.ofDays(7)) < 0) {
            return Duration.ofHours(12);
        }
        return Duration.ofDays(3);
    }

    /**
     * Whether to spend the 2 calls (summoner + league) on a profile/rank refresh.
     *
     * @param lastProfileRefresh when the profile was last refreshed, or null if never
     * @param newMatches         matches stored by this check
     */
    public static boolean needsProfileRefresh(TrackingLevel level, Instant lastProfileRefresh,
                                              int newMatches, Instant now) {
        if (lastProfileRefresh == null) {
            return true;
        }
        Duration age = Duration.between(lastProfileRefresh, now);
        if (level == TrackingLevel.TRACKED) {
            return newMatches > 0 || age.compareTo(TRACKED_PROFILE_MAX_AGE) >= 0;
        }
        return age.compareTo(DISCOVERED_PROFILE_MAX_AGE) >= 0
                || (newMatches > 0 && age.compareTo(DISCOVERED_PROFILE_MIN_AGE) >= 0);
    }

    public static Duration errorBackoff(TrackingLevel level) {
        return level == TrackingLevel.TRACKED ? TRACKED_ERROR_BACKOFF : DISCOVERED_ERROR_BACKOFF;
    }
}
