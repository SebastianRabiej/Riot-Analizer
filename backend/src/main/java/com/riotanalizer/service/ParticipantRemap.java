package com.riotanalizer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.riotanalizer.repository.PuuidRekeyRepository.ParticipantRow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Pairs a match's stored participant rows with the same match freshly fetched
 * under the current API key, yielding old PUUID → new PUUID for every
 * participant whose PUUID differs. Rows are matched by team + champion (unique
 * per team in every regular queue), falling back to Riot ID.
 */
final class ParticipantRemap {

    private ParticipantRemap() {
    }

    static Map<String, String> pairs(List<ParticipantRow> rows, JsonNode apiParticipants) {
        Map<String, List<ParticipantRow>> byChampion = new HashMap<>();
        Map<String, List<ParticipantRow>> byRiotId = new HashMap<>();
        for (ParticipantRow r : rows) {
            if (r.puuid() == null) {
                continue;
            }
            byChampion.computeIfAbsent(championKey(r.teamId(), r.championId()), k -> new ArrayList<>()).add(r);
            String rid = riotIdKey(r.riotIdGameName(), r.riotIdTagLine());
            if (rid != null) {
                byRiotId.computeIfAbsent(rid, k -> new ArrayList<>()).add(r);
            }
        }

        Map<String, String> result = new LinkedHashMap<>();
        if (apiParticipants == null || !apiParticipants.isArray()) {
            return result;
        }
        for (JsonNode pn : apiParticipants) {
            String newPuuid = text(pn, "puuid");
            if (newPuuid == null) {
                continue;
            }
            Integer teamId = pn.hasNonNull("teamId") ? pn.get("teamId").asInt() : null;
            Integer championId = pn.hasNonNull("championId") ? pn.get("championId").asInt() : null;
            ParticipantRow row = unique(byChampion.get(championKey(teamId, championId)));
            if (row == null) {
                String rid = riotIdKey(text(pn, "riotIdGameName"), text(pn, "riotIdTagline"));
                row = rid == null ? null : unique(byRiotId.get(rid));
            }
            if (row != null && !row.puuid().equals(newPuuid)) {
                result.put(row.puuid(), newPuuid);
            }
        }
        return result;
    }

    private static ParticipantRow unique(List<ParticipantRow> candidates) {
        return candidates != null && candidates.size() == 1 ? candidates.get(0) : null;
    }

    private static String championKey(Integer teamId, Integer championId) {
        return Objects.toString(teamId) + ":" + Objects.toString(championId);
    }

    private static String riotIdKey(String gameName, String tagLine) {
        if (gameName == null || tagLine == null) {
            return null;
        }
        return gameName.toLowerCase(Locale.ROOT) + "#" + tagLine.toLowerCase(Locale.ROOT);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
