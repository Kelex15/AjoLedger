package com.ajoledger.backend.auth.exception;

import java.util.UUID;

import com.ajoledger.backend.user.User;

/** Thrown when an already-rotated (revoked) refresh token is presented again. That's the
 * actual compromise signal, distinct from a plain expired or unknown token. */
public class RefreshTokenReuseDetectedException extends RuntimeException {

    private final User user;
    private final UUID familyId;

    public RefreshTokenReuseDetectedException(User user, UUID familyId) {
        super("Refresh token reuse detected");
        this.user = user;
        this.familyId = familyId;
    }

    public User getUser() {
        return user;
    }

    public UUID getFamilyId() {
        return familyId;
    }
}
