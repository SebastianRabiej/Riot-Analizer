package com.riotanalizer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Riot API related configuration, all overridable via environment variables
 * (see application.yml for the env bindings).
 */
@ConfigurationProperties(prefix = "riot")
public class RiotProperties {

    /** Riot dev/prod API key, sent as the X-Riot-Token header. Required at runtime. */
    private String apiKey;

    /** Platform host (EUNE): https://eun1.api.riotgames.com */
    private String platformHost = "https://eun1.api.riotgames.com";

    /** Regional host (account/match, EUROPE): https://europe.api.riotgames.com */
    private String regionalHost = "https://europe.api.riotgames.com";

    /** Epoch millis marking the start of the current season. */
    private long currentSeasonStartEpochMs = 1736380800000L;

    /** Scheduler fixed delay in millis. */
    private long fetchIntervalMs = 600000L;

    /** Optional initial player to register+track on startup, "gameName#tagLine". */
    private String initialPlayer;

    /** Page size when walking match ids (Match-V5 allows at most 100 per call). */
    private int matchPageSize = 100;

    /** Max NEW match details to fetch per player per cycle — the "slowly" throttle. */
    private int fetchBatchSize = 25;

    /** Absolute safety cap on how deep into history to page per player. */
    private int maxHistoryMatches = 2000;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getPlatformHost() {
        return platformHost;
    }

    public void setPlatformHost(String platformHost) {
        this.platformHost = platformHost;
    }

    public String getRegionalHost() {
        return regionalHost;
    }

    public void setRegionalHost(String regionalHost) {
        this.regionalHost = regionalHost;
    }

    public long getCurrentSeasonStartEpochMs() {
        return currentSeasonStartEpochMs;
    }

    public void setCurrentSeasonStartEpochMs(long currentSeasonStartEpochMs) {
        this.currentSeasonStartEpochMs = currentSeasonStartEpochMs;
    }

    public long getFetchIntervalMs() {
        return fetchIntervalMs;
    }

    public void setFetchIntervalMs(long fetchIntervalMs) {
        this.fetchIntervalMs = fetchIntervalMs;
    }

    public String getInitialPlayer() {
        return initialPlayer;
    }

    public void setInitialPlayer(String initialPlayer) {
        this.initialPlayer = initialPlayer;
    }

    public int getMatchPageSize() {
        return matchPageSize;
    }

    public void setMatchPageSize(int matchPageSize) {
        this.matchPageSize = matchPageSize;
    }

    public int getFetchBatchSize() {
        return fetchBatchSize;
    }

    public void setFetchBatchSize(int fetchBatchSize) {
        this.fetchBatchSize = fetchBatchSize;
    }

    public int getMaxHistoryMatches() {
        return maxHistoryMatches;
    }

    public void setMaxHistoryMatches(int maxHistoryMatches) {
        this.maxHistoryMatches = maxHistoryMatches;
    }
}
