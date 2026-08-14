# AjoLedger — How It Works

Plain-language explanations of the genuinely complex parts of the system, so I can re-explain any of it a month from now without re-reading the code from scratch.

## Refresh token rotation and reuse detection

**What it does (one sentence):** Every time a refresh token is used to get a new access token, it's replaced with a new one and the old one is permanently killed — and if a killed token ever gets presented again, every token that descended from the same login gets killed too.
**Why it exists:** A JWT access token can't be individually revoked once issued — it's just valid until it expires. Refresh tokens fix that (they're looked up in the database on every use, so they can be revoked), but a stolen refresh token is still a real risk if it can just be reused indefinitely. Rotation plus reuse detection means a stolen token is only useful once — the moment the legitimate holder (or the attacker) uses it again after it's already been rotated, that's a clear signal of compromise, not ambiguity.
**How it works, step by step:**
1. Login (or a prior rotation) issues an opaque, random refresh token, stores its SHA-256 hash (never the raw value) in `refresh_token`, tagged with a `family_id` shared by every token that will ever descend from this one login.
2. Using a refresh token calls `RefreshTokenService.rotate()`: look up the token by hash, confirm it isn't already revoked and hasn't expired, mark it revoked, and issue a brand-new token in the same family.
3. If the token found in step 2 is already marked revoked, that's reuse — either the legitimate holder is using a stale token, or someone else got hold of a token that's already been rotated away. Either way, every currently-active token in that family gets revoked immediately, and a `REFRESH_TOKEN_REUSE_DETECTED` audit log entry is written.
4. Logout revokes the single presented token directly, without touching the rest of the family — a normal end to one session, not a compromise signal.
**Key files:** `backend/src/main/java/com/ajoledger/backend/auth/RefreshTokenService.java`, `RefreshToken.java`, `AuthService.refresh()`.
**You should know:** Why rotation *plus* reuse detection, not just rotation alone — and the `@Transactional`/`noRollbackFor` subtlety documented in the Challenges log, since the revocation-on-reuse step is exactly the kind of side effect that's easy to accidentally roll back.
**In my own words:**

## Google OAuth account linking

**What it does (one sentence):** A Google login only ever attaches to an account that already exists and already has a verified email — never creates a new account, and never trusts a Google login just because it names a matching email address.
**Why it exists:** Password signup is the only way to create an account (ADR-0006), so Google sign-in has to answer "does this Google identity belong to an existing AjoLedger account" safely. Trusting an email match alone is a known account-takeover vector if the email isn't actually verified by the identity provider presenting it.
**How it works, step by step:**
1. The frontend gets a signed ID token directly from Google (no server-side redirect flow) and posts it to `/api/auth/google`.
2. `GoogleTokenVerifierService` verifies the token's signature, issuer, audience, and expiry against Google's own public keys, and reads the `email` and `email_verified` claims.
3. If Google didn't assert `email_verified: true`, the login is rejected outright — an unverified email claim proves nothing.
4. The account is looked up by that email. If the account's own `emailVerified` isn't already `true`, the login is rejected too — both sides have to independently confirm the same mailbox is under someone's control before the identities get linked.
5. First successful Google login for that account stores the Google subject ID (`googleId`) on `UserAuth`; later logins just confirm it still matches.
**Key files:** `backend/src/main/java/com/ajoledger/backend/auth/GoogleTokenVerifierService.java`, `AuthService.loginWithGoogle()`.
**You should know:** Why not just match on email — the `email_verified` check specifically is the whole security property; without it, the account-linking logic would be exploitable.
**In my own words:**
