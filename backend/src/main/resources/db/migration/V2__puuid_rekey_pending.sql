-- Matches whose participant PUUIDs still have to be re-keyed after the Riot API
-- key changed application (e.g. development key -> personal key). PUUIDs are
-- encrypted per application, so every stored PUUID becomes unusable with the
-- new key. Rows are queued at startup by PuuidMigrationRunner and drained in
-- the background by PuuidRekeyWorker (one Match-V5 call per match).
CREATE TABLE puuid_rekey_pending (
    match_id     VARCHAR(255) NOT NULL,
    enqueued_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT puuid_rekey_pending_pkey PRIMARY KEY (match_id)
);
