-- Phase 4B: a RailLens account can be linked to its RailOps login (the single login for both apps).
-- Linking is ONLY by this id, never by email: neither app verifies email addresses, so matching on email would
-- let someone who registers your email at RailOps take over your RailLens account.
ALTER TABLE users ADD COLUMN railops_user_id UUID;
ALTER TABLE users ADD CONSTRAINT uq_users_railops_user_id UNIQUE (railops_user_id);

-- Profiles created from a RailOps login have no RailLens password: they sign in at RailOps.
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;
