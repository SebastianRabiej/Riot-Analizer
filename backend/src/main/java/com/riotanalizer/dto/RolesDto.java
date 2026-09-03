package com.riotanalizer.dto;

import java.util.List;

/** Per-role breakdown for a player, most-played role first. */
public record RolesDto(
        String gameName,
        String tagLine,
        int totalGames,
        List<RoleStatDto> roles
) {}
