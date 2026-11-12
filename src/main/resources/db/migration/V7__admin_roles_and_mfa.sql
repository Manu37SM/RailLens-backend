-- Phase 2A: per-person admin accounts with TOTP MFA, replacing the shared X-Admin-Key.
-- Expand-only: every new column has a default or is nullable, so the previous app version keeps working
-- against this schema during a zero-downtime deploy.

ALTER TABLE users ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'USER';
ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'));

-- TOTP secret, AES-256-GCM encrypted by the app (raillens.mfa.encryption-key); never stored in clear.
ALTER TABLE users ADD COLUMN mfa_secret BYTEA;
ALTER TABLE users ADD COLUMN mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE;
-- Last accepted 30-second time step: a code is accepted once, even inside its validity window (replay guard).
ALTER TABLE users ADD COLUMN mfa_last_step BIGINT;

-- Whether the session (refresh-token family) was established with MFA. Admin rights are granted to a session
-- only if it was; a refresh carries the flag forward.
ALTER TABLE refresh_tokens ADD COLUMN mfa BOOLEAN NOT NULL DEFAULT FALSE;
