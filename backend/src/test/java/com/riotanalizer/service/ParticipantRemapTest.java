package com.riotanalizer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riotanalizer.repository.PuuidRekeyRepository.ParticipantRow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParticipantRemapTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mapsByTeamAndChampionAndSkipsUnchanged() throws Exception {
        List<ParticipantRow> rows = List.of(
                new ParticipantRow(1, "old-a", 100, 1, "A", "EUNE"),
                new ParticipantRow(2, "old-b", 200, 1, "B", "EUNE"),
                new ParticipantRow(3, "same-c", 200, 2, "C", "EUNE"));
        JsonNode api = mapper.readTree("""
                [{"puuid":"new-a","teamId":100,"championId":1,"riotIdGameName":"A","riotIdTagline":"EUNE"},
                 {"puuid":"new-b","teamId":200,"championId":1,"riotIdGameName":"B","riotIdTagline":"EUNE"},
                 {"puuid":"same-c","teamId":200,"championId":2,"riotIdGameName":"C","riotIdTagline":"EUNE"}]
                """);

        assertEquals(Map.of("old-a", "new-a", "old-b", "new-b"), ParticipantRemap.pairs(rows, api));
    }

    @Test
    void fallsBackToRiotIdWhenChampionIsAmbiguous() throws Exception {
        List<ParticipantRow> rows = List.of(
                new ParticipantRow(1, "old-a", 100, 7, "A", "EUNE"),
                new ParticipantRow(2, "old-b", 100, 7, "B", "EUNE"));
        JsonNode api = mapper.readTree("""
                [{"puuid":"new-a","teamId":100,"championId":7,"riotIdGameName":"a","riotIdTagline":"eune"},
                 {"puuid":"new-b","teamId":100,"championId":7,"riotIdGameName":"B","riotIdTagline":"EUNE"}]
                """);

        assertEquals(Map.of("old-a", "new-a", "old-b", "new-b"), ParticipantRemap.pairs(rows, api));
    }
}
