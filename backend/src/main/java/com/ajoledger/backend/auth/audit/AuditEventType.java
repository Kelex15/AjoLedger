package com.ajoledger.backend.auth.audit;

public enum AuditEventType {
    SIGNUP,
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    ACCOUNT_LOCKED,
    EMAIL_VERIFIED,
    GOOGLE_LINKED,
    LOGOUT,
    REFRESH_TOKEN_REUSE_DETECTED,
    VERIFICATION_RESENT
}
