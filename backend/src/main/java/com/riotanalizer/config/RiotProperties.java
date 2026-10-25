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

    /**
     * How often the scheduler wakes up to look for players that are due. A tick
     * that finds nobody due costs one DB query and no Riot calls; how often a
     * player is actually fetched is decided per player (see CheckCadence).
     */
    private long schedulerTickMs = 15000L;

    /**
     * How many hops away from a TRACKED player discovery goes: 1 = only people
     * met in tracked players' matches; 2 = also people met in those players'
     * matches; -1 = unbounded. 0 disables discovery.
     */
    private int discoveryMaxDepth = 1;

    /** Hard cap on the number of DISCOVERED players; 0 = no cap. */
    private int discoveryMaxPlayers = 0;

    /** Riot rate limits (defaults = development / personal key). */
    private int rateLimitShortRequests = 20;
    private long rateLimitShortWindowMs = 1_000L;
    private int rateLimitLongRequests = 100;
    private long rateLimitLongWindowMs = 120_000L;

    /**
     * Long-window permits background work leaves free so user-facing requests
     * (refresh, analysis) start immediately: tracked history may not use the
     * last {@code rateLimitTrackedReserve} permits, discovered history the last
     * {@code rateLimitDiscoveredReserve}.
     */
    private int rateLimitTrackedReserve = 10;
    private int rateLimitDiscoveredReserve = 25;

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

    public long getSchedulerTickMs() {
        return schedulerTickMs;
    }

    public void setSchedulerTickMs(long schedulerTickMs) {
        this.schedulerTickMs = schedulerTickMs;
    }

    public int getDiscoveryMaxDepth() {
        return discoveryMaxDepth;
    }

    public void setDiscoveryMaxDepth(int discoveryMaxDepth) {
        this.discoveryMaxDepth = discoveryMaxDepth;
    }

    public int getDiscoveryMaxPlayers() {
        return discoveryMaxPlayers;
    }

    public void setDiscoveryMaxPlayers(int discoveryMaxPlayers) {
        this.discoveryMaxPlayers = discoveryMaxPlayers;
    }

    public int getRateLimitShortRequests() {
        return rateLimitShortRequests;
    }

    public void setRateLimitShortRequests(int rateLimitShortRequests) {
        this.rateLimitShortRequests = rateLimitShortRequests;
    }

    public long getRateLimitShortWindowMs() {
        return rateLimitShortWindowMs;
    }

    public void setRateLimitShortWindowMs(long rateLimitShortWindowMs) {
        this.rateLimitShortWindowMs = rateLimitShortWindowMs;
    }

    public int getRateLimitLongRequests() {
        return rateLimitLongRequests;
    }

    public void setRateLimitLongRequests(int rateLimitLongRequests) {
        this.rateLimitLongRequests = rateLimitLongRequests;
    }

    public long getRateLimitLongWindowMs() {
        return rateLimitLongWindowMs;
    }

    public void setRateLimitLongWindowMs(long rateLimitLongWindowMs) {
        this.rateLimitLongWindowMs = rateLimitLongWindowMs;
    }

    public int getRateLimitTrackedReserve() {
        return rateLimitTrackedReserve;
    }

    public void setRateLimitTrackedReserve(int rateLimitTrackedReserve) {
        this.rateLimitTrackedReserve = rateLimitTrackedReserve;
    }

    public int getRateLimitDiscoveredReserve() {
        return rateLimitDiscoveredReserve;
    }

    public void setRateLimitDiscoveredReserve(int rateLimitDiscoveredReserve) {
        this.rateLimitDiscoveredReserve = rateLimitDiscoveredReserve;
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
