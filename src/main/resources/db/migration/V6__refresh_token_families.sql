-- Refresh-token families, for reuse detection (Phase 1 security plan).
--
-- A login starts a family; every rotation issues the next token in the same family and stamps the old one with
-- rotated_at. Presenting a token that was ALREADY rotated (outside a short grace window for two tabs refreshing at
-- once) means a copy of it was stolen: the whole family is revoked, logging out both the thief and the user.
--
-- Expand-only and safe while the previous app version is still running during a deploy:
--   * family_id has a DEFAULT, so the old code's INSERTs (which don't set it) still succeed. Each such token simply
--     starts its own family.
--   * Existing rows are backfilled with one family each (their history is unknown).
--   * rotated_at is nullable and ignored by the old code.
-- gen_random_uuid() is built into PostgreSQL 13+ (CI uses 16).

ALTER TABLE refresh_tokens ADD COLUMN family_id UUID DEFAULT gen_random_uuid();
UPDATE refresh_tokens SET family_id = gen_random_uuid() WHERE family_id IS NULL;
ALTER TABLE refresh_tokens ALTER COLUMN family_id SET NOT NULL;

ALTER TABLE refresh_tokens ADD COLUMN rotated_at TIMESTAMP;

CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens (family_id);
