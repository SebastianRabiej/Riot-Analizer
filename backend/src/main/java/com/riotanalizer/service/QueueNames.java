package com.riotanalizer.service;

import java.util.Map;

public final class QueueNames {

    private static final Map<Integer, String> NAMES = Map.of(
            420, "Ranked Solo/Duo",
            440, "Ranked Flex",
            400, "Normal Draft",
            430, "Normal Blind",
            450, "ARAM",
            700, "Clash",
            490, "Quickplay"
    );

    private QueueNames() {
    }

    public static String of(Integer queueId) {
        if (queueId == null) {
            return "Unknown";
        }
        String name = NAMES.get(queueId);
        return name != null ? name : "Queue " + queueId;
    }
}
