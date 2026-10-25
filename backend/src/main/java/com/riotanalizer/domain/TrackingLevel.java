package com.riotanalizer.domain;

/**
 * How the background scheduler treats a stored player. Order matters: earlier
 * constants are fetched first (see {@code ScheduledFetcher}).
 */
public enum TrackingLevel {

    /** Explicitly tracked by a user: history kept fresh at tracked priority. */
    TRACKED,

    /**
     * Met in a stored match of a TRACKED (or, with deeper discovery, another
     * DISCOVERED) player. Their history is fetched with spare rate-limit budget only.
     */
    DISCOVERED,

    /** Known (searched / looked up) but not fetched in the background. */
    NONE
}
