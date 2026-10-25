package com.riotanalizer.ratelimit;

/**
 * Priority of an outbound Riot call. When the rate limiter frees a permit it
 * goes to the highest-priority waiter (lowest ordinal); calls of equal priority
 * are served first-come, first-served.
 */
public enum FetchPriority {

    /** A user is waiting: manual refresh, track, search/register, live lookup. */
    REFRESH,

    /** A user opened a match analysis (timeline). */
    ANALYSIS,

    /** Background: keeping TRACKED players' history fresh (and PUUID re-keying). */
    TRACKED_HISTORY,

    /** Background, spare budget only: DISCOVERED players' history. */
    DISCOVERED_HISTORY;

    public boolean isBackground() {
        return this == TRACKED_HISTORY || this == DISCOVERED_HISTORY;
    }
}
