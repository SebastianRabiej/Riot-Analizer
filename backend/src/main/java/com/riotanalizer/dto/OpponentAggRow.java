package com.riotanalizer.dto;

/**
 * Raw aggregation row for opponents faced (internal projection from JPQL).
 * {@code games} = opposing games; {@code wins} = tracked player's wins in them.
 */
public record OpponentAggRow(
        String puuid,
        String gameName,
        String tagLine,
        Long games,
        Long wins
) {}
