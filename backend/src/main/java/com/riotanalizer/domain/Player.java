package com.riotanalizer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "player")
public class Player {

    @Id
    private String puuid;

    private String gameName;
    private String tagLine;

    private String summonerId;
    private Integer profileIconId;
    private Integer summonerLevel;

    // Solo/duo rank (queue 420)
    private String soloTier;
    private String soloRank;
    private Integer soloLeaguePoints;
    private Integer soloWins;
    private Integer soloLosses;

    // Flex rank (queue 440)
    private String flexTier;
    private String flexRank;
    private Integer flexLeaguePoints;
    private Integer flexWins;
    private Integer flexLosses;

    private Instant lastFetched;

    /** How (and whether) the background scheduler fetches this player. */
    @Enumerated(EnumType.STRING)
    @Column(name = "tracking_level", nullable = false, length = 16)
    private TrackingLevel trackingLevel = TrackingLevel.NONE;

    /**
     * Discovery hops from a TRACKED player: 0 = tracked, 1 = met in a tracked
     * player's match, 2 = met in a depth-1 player's match… NULL for NONE.
     */
    private Integer discoveryDepth;

    /** When the scheduler should look at this player again; NULL = due now. */
    private Instant nextCheckAt;

    /**
     * Earliest time the scheduler may check this player <em>before</em>
     * {@link #nextCheckAt}, when the Riot budget has spare room; NULL = any
     * time. Equals nextCheckAt after a failed check (no early retries).
     */
    private Instant earlyCheckAt;

    /** Creation time of the newest match in the player's own Riot match list, as of the last sync. */
    private Instant newestMatchAt;

    /** The whole current season has been walked and stored; only the cheap head check remains. */
    @Column(nullable = false)
    private boolean backfillComplete;

    /** Match-list offset where the unfinished backfill resumes (0 when complete). */
    @Column(nullable = false)
    private int backfillOffset;

    public String getPuuid() {
        return puuid;
    }

    public void setPuuid(String puuid) {
        this.puuid = puuid;
    }

    public String getGameName() {
        return gameName;
    }

    public void setGameName(String gameName) {
        this.gameName = gameName;
    }

    public String getTagLine() {
        return tagLine;
    }

    public void setTagLine(String tagLine) {
        this.tagLine = tagLine;
    }

    public String getSummonerId() {
        return summonerId;
    }

    public void setSummonerId(String summonerId) {
        this.summonerId = summonerId;
    }

    public Integer getProfileIconId() {
        return profileIconId;
    }

    public void setProfileIconId(Integer profileIconId) {
        this.profileIconId = profileIconId;
    }

    public Integer getSummonerLevel() {
        return summonerLevel;
    }

    public void setSummonerLevel(Integer summonerLevel) {
        this.summonerLevel = summonerLevel;
    }

    public String getSoloTier() {
        return soloTier;
    }

    public void setSoloTier(String soloTier) {
        this.soloTier = soloTier;
    }

    public String getSoloRank() {
        return soloRank;
    }

    public void setSoloRank(String soloRank) {
        this.soloRank = soloRank;
    }

    public Integer getSoloLeaguePoints() {
        return soloLeaguePoints;
    }

    public void setSoloLeaguePoints(Integer soloLeaguePoints) {
        this.soloLeaguePoints = soloLeaguePoints;
    }

    public Integer getSoloWins() {
        return soloWins;
    }

    public void setSoloWins(Integer soloWins) {
        this.soloWins = soloWins;
    }

    public Integer getSoloLosses() {
        return soloLosses;
    }

    public void setSoloLosses(Integer soloLosses) {
        this.soloLosses = soloLosses;
    }

    public String getFlexTier() {
        return flexTier;
    }

    public void setFlexTier(String flexTier) {
        this.flexTier = flexTier;
    }

    public String getFlexRank() {
        return flexRank;
    }

    public void setFlexRank(String flexRank) {
        this.flexRank = flexRank;
    }

    public Integer getFlexLeaguePoints() {
        return flexLeaguePoints;
    }

    public void setFlexLeaguePoints(Integer flexLeaguePoints) {
        this.flexLeaguePoints = flexLeaguePoints;
    }

    public Integer getFlexWins() {
        return flexWins;
    }

    public void setFlexWins(Integer flexWins) {
        this.flexWins = flexWins;
    }

    public Integer getFlexLosses() {
        return flexLosses;
    }

    public void setFlexLosses(Integer flexLosses) {
        this.flexLosses = flexLosses;
    }

    public Instant getLastFetched() {
        return lastFetched;
    }

    public void setLastFetched(Instant lastFetched) {
        this.lastFetched = lastFetched;
    }

    public TrackingLevel getTrackingLevel() {
        return trackingLevel;
    }

    public void setTrackingLevel(TrackingLevel trackingLevel) {
        this.trackingLevel = trackingLevel == null ? TrackingLevel.NONE : trackingLevel;
    }

    /** Explicitly tracked by a user. */
    public boolean isTracked() {
        return trackingLevel == TrackingLevel.TRACKED;
    }

    /**
     * Explicitly track ({@code true}: TRACKED, depth 0, due now) or untrack
     * ({@code false}: back to NONE, no longer fetched in the background).
     */
    public void setTracked(boolean tracked) {
        if (tracked) {
            if (trackingLevel != TrackingLevel.TRACKED) {
                nextCheckAt = null;
            }
            trackingLevel = TrackingLevel.TRACKED;
            discoveryDepth = 0;
        } else if (trackingLevel == TrackingLevel.TRACKED) {
            trackingLevel = TrackingLevel.NONE;
            discoveryDepth = null;
        }
    }

    public Integer getDiscoveryDepth() {
        return discoveryDepth;
    }

    public void setDiscoveryDepth(Integer discoveryDepth) {
        this.discoveryDepth = discoveryDepth;
    }

    public Instant getNextCheckAt() {
        return nextCheckAt;
    }

    public void setNextCheckAt(Instant nextCheckAt) {
        this.nextCheckAt = nextCheckAt;
    }

    public Instant getEarlyCheckAt() {
        return earlyCheckAt;
    }

    public void setEarlyCheckAt(Instant earlyCheckAt) {
        this.earlyCheckAt = earlyCheckAt;
    }

    public Instant getNewestMatchAt() {
        return newestMatchAt;
    }

    public void setNewestMatchAt(Instant newestMatchAt) {
        this.newestMatchAt = newestMatchAt;
    }

    public boolean isBackfillComplete() {
        return backfillComplete;
    }

    public void setBackfillComplete(boolean backfillComplete) {
        this.backfillComplete = backfillComplete;
    }

    public int getBackfillOffset() {
        return backfillOffset;
    }

    public void setBackfillOffset(int backfillOffset) {
        this.backfillOffset = backfillOffset;
    }
}
