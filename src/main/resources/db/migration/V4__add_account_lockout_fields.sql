-- Adds account lockout tracking to users. AuthRateLimitInterceptor already
-- rate-limits /api/v1/auth/** to 10 requests/min per client IP, but that
-- alone doesn't stop a distributed brute-force attempt (many IPs, one
-- target account) or a slow-drip attempt that stays under the per-IP
-- threshold. Locking the account itself after repeated failures closes
-- that gap - see AuthService#login for the actual lockout logic that uses
-- these two columns.
ALTER TABLE users
    ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN locked_until TIMESTAMP NULL;
