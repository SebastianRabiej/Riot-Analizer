package com.riotanalizer.dto;

/**
 * One game on the Carry Index scatter: the tracked player's carry score (0..100)
 * for that game, with enough context to render a tooltip and open the match.
 */
public record CarryPointDto(
        String matchId,
        long gameCreation,
        String championName,
        String teamPosition,
        boolean win,
        double damageShare,
        double goldShare,
        double killParticipation,
        boolean topDamage,
        double carryScore
) {}
