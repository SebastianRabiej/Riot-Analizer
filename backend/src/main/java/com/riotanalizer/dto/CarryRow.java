package com.riotanalizer.dto;

/**
 * Raw per-game aggregation row for the Carry Index (internal JPQL projection).
 * The tracked player's own numbers for one game, plus the totals (and top damage)
 * of their team in that game — everything needed to compute the player's share of
 * their team's output and whether they were the team's top damage dealer.
 * Team sums include the tracked player themselves.
 *
 * Types match what Hibernate returns: direct entity attributes keep their type,
 * SUM(int) comes back as Long, and MAX(int) as Integer.
 */
public record CarryRow(
        String matchId,
        long gameCreation,
        String championName,
        String teamPosition,
        boolean win,
        int myDamage,
        int myGold,
        int myKills,
        int myAssists,
        Long teamDamage,
        Long teamGold,
        Long teamKills,
        Long teamAssists,
        Integer teamMaxDamage
) {}
