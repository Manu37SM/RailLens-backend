-- Phase 6: progressive lockout. How many lockouts in a row this account has had since its last successful sign-in;
-- each one doubles the lock time (capped). Existing rows start at 0, so nothing changes for anyone today.
ALTER TABLE users
    ADD COLUMN lockout_count INTEGER NOT NULL DEFAULT 0 CHECK (lockout_count >= 0);
