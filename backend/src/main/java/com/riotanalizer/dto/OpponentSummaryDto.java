package com.riotanalizer.dto;

/**
 * One row of the "vs player" dashboard: an opponent the tracked player has faced
 * on the ENEMY team this season, with the player's record against them.
 * {@code games} counts opposing games only; {@code wins}/{@code losses} and
 * {@code winRate} are from the tracked player's perspective.
 */
public record OpponentSummaryDto(
        String puuid,
        String gameName,
        String tagLine,
        int games,
        int wins,
        int losses,
        double winRate
) {}
