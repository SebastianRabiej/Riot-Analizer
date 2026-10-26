package com.riotanalizer.dto;

import java.util.List;

/**
 * One page of DISCOVERED players (closest to the tracked players first).
 *
 * @param total          all DISCOVERED players (ignoring the search filter)
 * @param seasonSynced   how many of them have their whole current season stored
 * @param profileFetched how many of them have had their profile & rank fetched at least once
 * @param fetchMatches   {@code riot.discovery-fetch-matches}: false = discovered players are profile only
 * @param matching       players matching the search filter (= total without one)
 */
public record DiscoveredPageDto(
        long total,
        long seasonSynced,
        long profileFetched,
        boolean fetchMatches,
        long matching,
        int page,
        int size,
        List<PlayerDto> players
) {
}
