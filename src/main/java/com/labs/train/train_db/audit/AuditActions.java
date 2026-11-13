package com.labs.train.train_db.audit;

/** Names used in security_audit.action (Phase 6). Stable: dashboards and alerts may match on them. */
public final class AuditActions {
        public static final String LOGIN_SUCCEEDED = "LOGIN_SUCCEEDED";
        public static final String LOGIN_FAILED = "LOGIN_FAILED";
        public static final String LOGIN_BLOCKED_LOCKED = "LOGIN_BLOCKED_LOCKED";
        public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
        public static final String MFA_CODE_FAILED = "MFA_CODE_FAILED";
        public static final String MFA_ENROLLED = "MFA_ENROLLED";
        public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
        public static final String ACCOUNT_DELETED = "ACCOUNT_DELETED";
        public static final String REGISTERED = "REGISTERED";
        public static final String REFRESH_TOKEN_REUSED = "REFRESH_TOKEN_REUSED";
        public static final String RAILOPS_PROFILE_LINKED = "RAILOPS_PROFILE_LINKED";
        public static final String RAILOPS_PROFILE_CREATED = "RAILOPS_PROFILE_CREATED";
        public static final String RAILOPS_ACCOUNTS_LINKED = "RAILOPS_ACCOUNTS_LINKED";

        private AuditActions() {
        }
}
