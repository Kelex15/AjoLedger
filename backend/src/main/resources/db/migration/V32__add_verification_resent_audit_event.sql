ALTER TABLE audit_log DROP CONSTRAINT audit_log_event_type_check;
ALTER TABLE audit_log ADD CONSTRAINT audit_log_event_type_check
    CHECK (event_type IN (
        'SIGNUP', 'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'ACCOUNT_LOCKED',
        'EMAIL_VERIFIED', 'GOOGLE_LINKED', 'LOGOUT', 'REFRESH_TOKEN_REUSE_DETECTED',
        'VERIFICATION_RESENT'
    ));
