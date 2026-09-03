package com.riotanalizer.dto;

import java.util.List;

/**
 * "Carry Index" for the tracked player this season: were they the one carrying
 * their team, or being carried? Built purely from stored participants (all ten
 * per match), so it needs no timeline data.
 *
 * Shares are the player's percentage of their team's total (team sums include the
 * player). {@code carryIndex} is a 0..100 blend where an even 1/5 contribution
 * maps to ~50. {@code winRateWhenTopDamage} vs {@code winRateOtherwise} shows
 * whether carrying actually correlates with winning.
 */
public record CarryIndexDto(
        String gameName,
        String tagLine,
        int games,
        double avgDamageShare,
        double avgGoldShare,
        double avgKillParticipation,
        double topDamageRate,
        double winRateWhenTopDamage,
        double winRateOtherwise,
        double carryIndex,
        List<CarryPointDto> perGame
) {}
