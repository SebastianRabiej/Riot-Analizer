-- Opportunistic "early" checks (see ScheduledFetcher / EarlyCheckPolicy).
--
-- next_check_at stays the regular cadence. early_check_at is the earliest time
-- the scheduler may check the player *before* that, but only while the Riot
-- budget has plenty of headroom. Set after every check (check time + a minimum
-- gap per tracking level); set equal to next_check_at after a failed check so
-- error / not-found back-offs are never cut short. NULL = no restriction.
ALTER TABLE player ADD COLUMN early_check_at TIMESTAMP(6) WITH TIME ZONE;
