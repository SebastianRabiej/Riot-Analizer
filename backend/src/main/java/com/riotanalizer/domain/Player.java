package com.riotanalizer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Column(nullable = false)
    private boolean tracked;

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

    public boolean isTracked() {
        return tracked;
    }

    public void setTracked(boolean tracked) {
        this.tracked = tracked;
    }
}
