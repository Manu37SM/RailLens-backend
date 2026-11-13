-- Phase 6: tamper-evident security audit log.
-- Each row's hash covers the previous row's hash plus its own fields, so changing, removing or reordering any
-- past row breaks every hash after it (GET /api/v1/admin/audit/verify finds the first broken row).
-- The application role can only add rows: the trigger refuses UPDATE and DELETE, even for the table owner.
-- Personal data is kept small on purpose: usernames and client IPs, never emails, passwords or tokens.
CREATE TABLE security_audit (
    id          BIGSERIAL    PRIMARY KEY,
    occurred_at TIMESTAMPTZ  NOT NULL,
    actor       VARCHAR(100) NOT NULL,
    action      VARCHAR(60)  NOT NULL,
    target      VARCHAR(100),
    detail      TEXT,
    client_ip   VARCHAR(45),
    prev_hash   CHAR(64)     NOT NULL,
    hash        CHAR(64)     NOT NULL UNIQUE
);

CREATE INDEX ix_security_audit_occurred ON security_audit (occurred_at);

CREATE FUNCTION security_audit_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'security_audit is append-only';
END;
$$;

CREATE TRIGGER security_audit_no_update_delete
    BEFORE UPDATE OR DELETE ON security_audit
    FOR EACH ROW EXECUTE FUNCTION security_audit_append_only();

CREATE TRIGGER security_audit_no_truncate
    BEFORE TRUNCATE ON security_audit
    FOR EACH STATEMENT EXECUTE FUNCTION security_audit_append_only();
