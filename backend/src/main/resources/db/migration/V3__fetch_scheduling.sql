-- Priority + staleness-aware fetching (see ScheduledFetcher / FetchService).
--
-- tracking_level replaces the old boolean `tracked`:
--   TRACKED    – explicitly tracked by a user (was tracked = true)
--   DISCOVERED – appeared in a stored match of a TRACKED/DISCOVERED player;
--                fetched in the background with spare rate-limit budget only
--   NONE       – known (searched / looked up) but not fetched in the background
--
-- discovery_depth: 0 for TRACKED, 1 for players met in a tracked player's
-- match, 2 for players met in a depth-1 player's match, ... (NULL for NONE).
-- How deep discovery goes is riot.discovery.max-depth (-1 = unbounded).
ALTER TABLE player ADD COLUMN tracking_level VARCHAR(16) NOT NULL DEFAULT 'NONE';
ALTER TABLE player ADD COLUMN discovery_depth INTEGER;

UPDATE player SET tracking_level = 'TRACKED', discovery_depth = 0 WHERE tracked;

ALTER TABLE player DROP COLUMN tracked;

-- Scheduling / staleness state.
--   next_check_at     – when the scheduler should look at this player again
--                       (NULL = due now). Derived from recent activity.
--   newest_match_at   – creation time of the newest match in the player's own
--                       Riot match list, as of the last sync. The cheap
--                       "anything new?" head check asks Riot only for ids since then.
--   backfill_complete – the whole current season has been walked and stored;
--                       from then on only the 1-call head check runs.
--   backfill_offset   – where an unfinished backfill resumes in the match list.
ALTER TABLE player ADD COLUMN next_check_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE player ADD COLUMN newest_match_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE player ADD COLUMN backfill_complete BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE player ADD COLUMN backfill_offset INTEGER NOT NULL DEFAULT 0;

CREATE INDEX idx_player_schedule ON player (tracking_level, next_check_at);
