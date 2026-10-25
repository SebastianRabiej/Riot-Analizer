package com.riotanalizer.dto;

/**
 * A stored player. The trailing fields expose the background-fetch state:
 * {@code trackingLevel} is TRACKED / DISCOVERED / NONE, {@code discoveryDepth}
 * is the number of hops from a tracked player (0 for tracked), and
 * {@code backfillComplete} / {@code newestMatchAt} / {@code nextCheckAt} show
 * how fresh their history is and when the scheduler looks at them next.
 */
public record PlayerDto(
        String puuid,
        String gameName,
        String tagLine,
        int summonerLevel,
        int profileIconId,
        RankDto soloRank,
        RankDto flexRank,
        boolean tracked,
        String lastFetched,
        String trackingLevel,
        Integer discoveryDepth,
        boolean backfillComplete,
        String newestMatchAt,
        String nextCheckAt
) {
}
