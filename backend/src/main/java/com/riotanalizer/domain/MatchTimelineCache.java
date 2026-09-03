package com.riotanalizer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Cached, distilled timeline analysis for one match, stored as JSON. Populated
 * on first request so the extra Riot timeline call is paid at most once.
 */
@Entity
@Table(name = "match_timeline_cache")
public class MatchTimelineCache {

    @Id
    private String matchId;

    @Column(columnDefinition = "text")
    private String json;

    /** Shape version of the cached JSON; a mismatch (or null, for pre-versioning rows) triggers a refetch. */
    private Integer schemaVersion;

    private Instant createdAt;

    public MatchTimelineCache() {
    }

    public MatchTimelineCache(String matchId, String json, Integer schemaVersion, Instant createdAt) {
        this.matchId = matchId;
        this.json = json;
        this.schemaVersion = schemaVersion;
        this.createdAt = createdAt;
    }

    public String getMatchId() {
        return matchId;
    }

    public void setMatchId(String matchId) {
        this.matchId = matchId;
    }

    public String getJson() {
        return json;
    }

    public void setJson(String json) {
        this.json = json;
    }

    public Integer getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(Integer schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
