package com.riotanalizer.service;

import com.riotanalizer.domain.TrackingLevel;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure prediction of what the scheduler will do for one player the next time
 * it picks them up, mirroring {@link FetchService#sync}, {@link CheckCadence}
 * and {@link ScheduledFetcher}. Used only for display (the fetch-queue view);
 * the real decisions are still made by those classes at run time.
 *
 * <p>What it cannot know in advance is how many new games Riot will return,
 * so the call estimate is a range: the minimum is "nothing new", the maximum
 * is "a full batch of new games".
 */
public final class FetchQueuePlanner {

    public enum CheckType { HEAD_CHECK, BACKFILL, PROFILE_ONLY }

    /**
     * @param checkType what {@link FetchService#sync} will do
     * @param steps     human-readable plan, in order
     * @param minCalls  Riot calls if nothing new turns up
     * @param maxCalls  Riot calls if a full batch of new games turns up (≈ for backfill)
     * @param reason    why the player is checked at this cadence
     */
    public record Plan(CheckType checkType, List<String> steps, int minCalls, int maxCalls, String reason) {
    }

    /** Snapshot of the player state the scheduler looks at. */
    public record State(TrackingLevel level, boolean backfillComplete, int backfillOffset,
                        Instant newestMatchAt, Instant lastProfileRefresh, Boolean inGame,
                        Instant nextCheckAt) {
    }

    private FetchQueuePlanner() {
    }

    public static Plan plan(State s, int batchSize, Instant now) {
        return plan(s, batchSize, true, now);
    }

    /**
     * @param discoveredFetchMatches {@code riot.discovery-fetch-matches}; when false,
     *                               DISCOVERED players get a profile-only plan
     */
    public static Plan plan(State s, int batchSize, boolean discoveredFetchMatches, Instant now) {
        if (s.level() != TrackingLevel.TRACKED && !discoveredFetchMatches) {
            return profileOnly(s, now);
        }
        boolean tracked = s.level() == TrackingLevel.TRACKED;
        boolean headCheck = s.backfillComplete() && s.newestMatchAt() != null;
        List<String> steps = new ArrayList<>();
        int min = 0;
        int max = 0;

        if (headCheck) {
            steps.add("Ask Riot for games since " + ago(s.newestMatchAt(), now) + " (1 call)");
            steps.add("Download any new games — usually none");
            min += 1;
            max += 1 + batchSize;
        } else if (s.newestMatchAt() == null && s.backfillOffset() == 0) {
            steps.add("First fetch: read this season's match list");
            steps.add("Download up to " + batchSize + " games");
            min += 1;
            max += 2 + batchSize;
        } else {
            steps.add(s.backfillOffset() > 0
                    ? "Continue season backfill from match #" + s.backfillOffset()
                    : "Re-walk season match list for missing games");
            steps.add("Download up to " + batchSize + " missing games");
            min += 1;
            max += 2 + batchSize;
        }

        if (profileRefreshCertain(s, now)) {
            steps.add(s.lastProfileRefresh() == null
                    ? "Fetch profile & rank (never fetched)"
                    : "Refresh profile & rank (last " + ago(s.lastProfileRefresh(), now) + ")");
            min += 2;
            max += 2;
        } else if (profileRefreshIfNewGames(s, now)) {
            steps.add("Refresh profile & rank if there are new games");
            max += 2;
        }

        if (tracked) {
            steps.add("Check for a live game");
            min += 1;
            max += 1;
        }

        return new Plan(headCheck ? CheckType.HEAD_CHECK : CheckType.BACKFILL, steps, min, max, reason(s, now));
    }

    private static Plan profileOnly(State s, Instant now) {
        List<String> steps = new ArrayList<>();
        int calls = 0;
        if (CheckCadence.profileOnlyNeedsRefresh(s.lastProfileRefresh(), now)) {
            steps.add(s.lastProfileRefresh() == null
                    ? "Fetch profile & rank (never fetched)"
                    : "Refresh profile & rank (last " + ago(s.lastProfileRefresh(), now) + ")");
            calls = 2;
        } else {
            steps.add("Profile & rank still fresh — nothing to fetch");
        }
        steps.add("Match history skipped (discovered players are profile only)");
        String reason = s.lastProfileRefresh() == null
                ? "Just discovered — profile only"
                : "Profile only — refreshed every " + human(CheckCadence.DISCOVERED_PROFILE_MAX_AGE);
        return new Plan(CheckType.PROFILE_ONLY, steps, calls, calls, reason);
    }

    /** Why the player is checked when it is — the rule {@link CheckCadence#nextDelay} applies. */
    static String reason(State s, Instant now) {
        boolean tracked = s.level() == TrackingLevel.TRACKED;
        if (s.nextCheckAt() == null && s.newestMatchAt() == null && s.lastProfileRefresh() == null) {
            return tracked ? "Just added — never fetched" : "Just discovered — never fetched";
        }
        if (!s.backfillComplete()) {
            return "Season not fully stored — re-queued right after each batch";
        }
        if (tracked && Boolean.TRUE.equals(s.inGame())) {
            return "In a live game — rechecked every " + human(CheckCadence.IN_GAME);
        }
        Duration every = CheckCadence.nextDelay(s.level(), true, 0, s.newestMatchAt(), false, now);
        if (s.newestMatchAt() == null) {
            return "No games this season — every " + human(every);
        }
        return "Last game " + ago(s.newestMatchAt(), now) + " — every " + human(every);
    }

    private static boolean profileRefreshCertain(State s, Instant now) {
        if (s.lastProfileRefresh() == null) {
            return true;
        }
        Duration age = Duration.between(s.lastProfileRefresh(), now);
        Duration maxAge = s.level() == TrackingLevel.TRACKED
                ? CheckCadence.TRACKED_PROFILE_MAX_AGE
                : CheckCadence.DISCOVERED_PROFILE_MAX_AGE;
        return age.compareTo(maxAge) >= 0;
    }

    private static boolean profileRefreshIfNewGames(State s, Instant now) {
        if (s.level() == TrackingLevel.TRACKED) {
            return true;
        }
        Duration age = Duration.between(s.lastProfileRefresh(), now);
        return age.compareTo(CheckCadence.DISCOVERED_PROFILE_MIN_AGE) >= 0;
    }

    private static String ago(Instant t, Instant now) {
        return human(Duration.between(t, now)) + " ago";
    }

    /** "45s", "12m", "3h", "2d". */
    static String human(Duration d) {
        long sec = Math.max(0, d.getSeconds());
        if (sec < 60) {
            return sec + "s";
        }
        long min = sec / 60;
        if (min < 60) {
            return min + "m";
        }
        long hr = min / 60;
        if (hr < 48) {
            return hr + "h";
        }
        return (hr / 24) + "d";
    }
}
