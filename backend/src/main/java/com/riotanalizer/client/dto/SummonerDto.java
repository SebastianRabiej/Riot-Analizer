package com.riotanalizer.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SummonerDto(String id, String puuid, Integer profileIconId, Long summonerLevel) {
}
