package com.riotanalizer.dto;

import java.util.List;

/**
 * One page of DISCOVERED players (closest to the tracked players first).
 *
 * @param total         all DISCOVERED players (ignoring the search filter)
 * @param seasonSynced  how many of them have their whole current season stored
 * @param matching      players matching the search filter (= total without one)
 */
public record DiscoveredPageDto(
        long total,
        long seasonSynced,
        long matching,
        int page,
        int size,
        List<PlayerDto> players
) {
}
