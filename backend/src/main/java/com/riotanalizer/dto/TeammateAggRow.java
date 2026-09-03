package com.riotanalizer.dto;

/**
 * Raw aggregation row for teammates played with (internal projection from JPQL).
 * {@code games} = shared same-team games; {@code wins} = wins in them.
 */
public record TeammateAggRow(
        String puuid,
        String gameName,
        String tagLine,
        Long games,
        Long wins
) {}
