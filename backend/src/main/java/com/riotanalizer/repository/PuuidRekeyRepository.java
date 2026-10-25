package com.riotanalizer.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Plain-SQL operations for re-keying PUUIDs after the Riot API key moved to a
 * different application. Done with JDBC because the PUUID is the JPA primary
 * key of {@code player}, which JPA cannot update in place.
 */
@Repository
public class PuuidRekeyRepository {

    public record ParticipantRow(long id, String puuid, Integer teamId, Integer championId,
                                 String riotIdGameName, String riotIdTagLine) {
    }

    private final JdbcTemplate jdbc;

    public PuuidRekeyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Re-point every stored reference of {@code oldPuuid} to {@code newPuuid}:
     * all participant rows, cached timeline JSON, and the player row (merged
     * into an existing row if the new PUUID was already registered).
     */
    @Transactional
    public void translate(String oldPuuid, String newPuuid) {
        if (oldPuuid == null || newPuuid == null || oldPuuid.equals(newPuuid)) {
            return;
        }
        jdbc.update("UPDATE participant SET puuid = ? WHERE puuid = ?", newPuuid, oldPuuid);
        // Cached timeline analyses embed participant PUUIDs in their JSON.
        jdbc.update("""
                UPDATE match_timeline_cache
                   SET json = replace(json, ?, ?)
                 WHERE match_id IN (SELECT match_id FROM participant WHERE puuid = ?)
                   AND strpos(json, ?) > 0
                """, oldPuuid, newPuuid, newPuuid, oldPuuid);

        if (!playerExists(oldPuuid)) {
            return;
        }
        if (playerExists(newPuuid)) {
            // Same person already registered under the new key: keep that row,
            // with the stronger tracking level and the shallower discovery depth.
            jdbc.update("""
                    UPDATE player n
                       SET tracking_level = CASE
                               WHEN 'TRACKED' IN (n.tracking_level, o.tracking_level) THEN 'TRACKED'
                               WHEN 'DISCOVERED' IN (n.tracking_level, o.tracking_level) THEN 'DISCOVERED'
                               ELSE 'NONE' END,
                           discovery_depth = LEAST(n.discovery_depth, o.discovery_depth)
                      FROM player o
                     WHERE o.puuid = ? AND n.puuid = ?
                    """, oldPuuid, newPuuid);
            jdbc.update("DELETE FROM player WHERE puuid = ?", oldPuuid);
        } else {
            // summoner_id is encrypted per application too; the next profile refresh refills it.
            jdbc.update("UPDATE player SET puuid = ?, summoner_id = NULL WHERE puuid = ?", newPuuid, oldPuuid);
        }
    }

    @Transactional
    public void updateRiotId(String puuid, String gameName, String tagLine) {
        jdbc.update("""
                UPDATE player
                   SET game_name = COALESCE(?, game_name),
                       tag_line  = COALESCE(?, tag_line)
                 WHERE puuid = ?
                """, gameName, tagLine, puuid);
    }

    public List<ParticipantRow> participantsOf(String matchId) {
        return jdbc.query("""
                        SELECT id, puuid, team_id, champion_id, riot_id_game_name, riot_id_tag_line
                          FROM participant
                         WHERE match_id = ?
                        """,
                (rs, i) -> new ParticipantRow(
                        rs.getLong("id"),
                        rs.getString("puuid"),
                        (Integer) rs.getObject("team_id"),
                        (Integer) rs.getObject("champion_id"),
                        rs.getString("riot_id_game_name"),
                        rs.getString("riot_id_tag_line")),
                matchId);
    }

    /** Queue every stored match for participant re-keying. Returns how many were newly queued. */
    @Transactional
    public int enqueueAllMatches() {
        return jdbc.update("""
                INSERT INTO puuid_rekey_pending (match_id)
                SELECT match_id FROM matches
                ON CONFLICT (match_id) DO NOTHING
                """);
    }

    /** Queue every stored match the given player took part in. Returns how many were newly queued. */
    @Transactional
    public int enqueueMatchesOf(String puuid) {
        return jdbc.update("""
                INSERT INTO puuid_rekey_pending (match_id)
                SELECT DISTINCT match_id FROM participant WHERE puuid = ?
                ON CONFLICT (match_id) DO NOTHING
                """, puuid);
    }

    public List<String> nextPending(int limit) {
        return jdbc.queryForList(
                "SELECT match_id FROM puuid_rekey_pending ORDER BY enqueued_at, match_id LIMIT ?",
                String.class, limit);
    }

    public long countPending() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM puuid_rekey_pending", Long.class);
        return n == null ? 0 : n;
    }

    @Transactional
    public void markDone(String matchId) {
        jdbc.update("DELETE FROM puuid_rekey_pending WHERE match_id = ?", matchId);
    }

    private boolean playerExists(String puuid) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM player WHERE puuid = ?", Integer.class, puuid);
        return n != null && n > 0;
    }
}
