package com.riotanalizer.dto;

import java.util.List;

public record HeadToHeadDto(
        PlayerRef player,
        PlayerRef opponent,
        int totalSharedGames,
        int sameTeamGames,
        int opposingGames,
        int playerWinsWhenOpposing,
        int opponentWinsWhenOpposing,
        List<H2HMatchDto> matches
) {
    public record PlayerRef(String gameName, String tagLine) {
    }
}
