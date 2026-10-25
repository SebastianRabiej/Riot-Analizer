-- Set for every player when Riot rejects a stored PUUID as issued for another
-- API key ("Exception decrypting"), i.e. the key moved to another Riot
-- application. PuuidRekeyWorker then re-resolves each flagged player by Riot ID
-- and clears the flag. Not mapped by JPA; maintained via PuuidRekeyRepository.
ALTER TABLE player ADD COLUMN puuid_check_pending BOOLEAN NOT NULL DEFAULT FALSE;
