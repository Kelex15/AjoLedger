# ADR 0006: Auth & Identity Security Model

**Status:** Accepted

## Context

AjoLedger is neobank-adjacent, so how I handle credentials, sessions, and identity isn't scaffolding I bolt on around the real project — it's part of what's actually being evaluated. I'd initially been treating auth as a "basic" Phase 1 checkbox (Spring Security, JWT, nothing more), but that undersells it: a real app dealing with people's money can't treat login as an afterthought, and there's genuine engineering depth here worth doing properly and being able to explain. This ADR is the design pass I should have done before writing any auth code at all.

I did first consider Google-OAuth-only, on the reasoning that it delegates credential storage risk to Google entirely and avoids me owning password security. I'm reversing that here: outsourcing the primary login path entirely means there's no actual auth engineering left to demonstrate, and for this specific project, owning that surface *correctly* is part of the point.

## Decision

### Identity/credential split
`User` (profile data returned in API responses) and credentials live on separate entities. A new `UserAuth` entity holds `passwordHash`, `googleId`, `emailVerified`, failed-login tracking, and similar — nothing security-sensitive ever sits on `User`, and nothing on `UserAuth` is ever serialized into a DTO. `UserAuth` stays in the `auth` package (per ADR 0002's module boundaries), so nothing outside `auth` can reach it directly.

### Password primary, Google OAuth auto-linked once the account's email is verified
Every account is created through password signup — Google OAuth is never a way to create a new account, only a way to log into one that already exists. Once a user has verified their email (via the signup verification link), a later "Sign in with Google" attempt auto-links to that account, provided Google's ID token asserts `email_verified: true` for an email that exactly matches the account's own already-verified email. Both AjoLedger's own verification link and Google's `email_verified` claim are ultimately proving the same thing — current control of that mailbox — so trusting the match at that point is reasonable, not a gap.

The actual safeguard here is that check specifically: linking only happens off `email_verified: true`, never off the mere presence of an email field on the token. An OAuth response that returns an email without asserting Google itself has verified it doesn't get to link — that's the real account-takeover vector (an IdP, or a misconfigured OIDC flow, handing back an unverified email an attacker could claim). If no `UserAuth` record has a verified email matching the Google account's, the Google sign-in attempt is rejected and the user is told to sign up with a password first.

### Email verification gates every session, regardless of method
`emailVerified` starts `false` on signup. No session — password or Google — is issued while it's false, and Google linking specifically requires the account's own `emailVerified` to already be `true` before it will even attempt the match. This check happens in one place in the login flow, checked unconditionally, rather than being assumed true for OAuth logins just because Google's token also claims verification — both have to be true.

### Rate limiting on login attempts
Failed attempts are tracked per `UserAuth` record (count + timestamp of last failure), not via an external store — five consecutive failures locks the account for 15 minutes, and a successful login resets the counter. Good enough for a single-instance v1; if this ever runs horizontally scaled, that counter needs to move to something shared (Redis) instead of living on the row, since concurrent instances checking/incrementing a DB column isn't safe under real concurrency the way it would need to be — noting that now so it isn't forgotten later.

### Refresh token rotation with revocation
Short-lived access JWT (I'm dropping the current 1-hour default down to something in the 15-minute range once this is implemented) paired with a longer-lived refresh token, stored server-side in its own table (token hash, user, expiry, revoked flag) so it can actually be revoked — a JWT alone can't be. Each use of a refresh token issues a new one and invalidates the old; if an already-rotated refresh token gets used again, that's a signal the token was stolen and the whole session family gets revoked. Logout revokes the current refresh token outright.

### Real password strength checking
Minimum length plus zxcvbn-style strength estimation (likely `com.nulab-inc:zxcvbn4j`, a Java port) — not a character-class regex. Regex rules like "one uppercase, one number, one symbol" are well-documented as pushing people toward predictable patterns (`Password1!`) while being more annoying than they're worth; entropy-based estimation actually rejects weak-but-technically-compliant passwords.

### Audit logging
A simple, append-only `AuditLog` entity: user (nullable — a failed login with a bad username may not resolve to a real user), event type (`LOGIN_SUCCESS`, `LOGIN_FAILURE`, `PASSWORD_CHANGE`, `GOOGLE_LINKED`, `LOGOUT`, etc.), timestamp, IP address. Same append-only bias as the ledger and `CirclePayout` — a security log that can be edited after the fact isn't much of a log.

## Explicitly deferred (v1)

**2FA/MFA is not being built for v1.** This is a deliberate scope decision, not an oversight — noting it here, and I'll note it again in `backend/docs/HOW_IT_WORKS.md` once auth is actually implemented, so it reads as a known gap rather than something that got missed.

## Alternatives considered

Google-OAuth-only, as I described above — reversed because it removes the actual auth engineering this ADR exists to capture, and ties every account to having a Google identity, which isn't a constraint this project needs.

A managed identity provider (Auth0, Clerk, Firebase Auth, Cognito) would hand off essentially everything in this document. Rejected for the same reason: there'd be no auth design left to own or explain.

Regex-based password complexity rules instead of strength estimation — rejected above; noting it here too since it's a real, commonly-reached-for alternative that I'm deliberately not using.

## Consequences

This is a meaningfully bigger Phase 1 auth scope than "Spring Security, JWT" implied — `UserAuth`, the linking policy, email verification (which now needs an actual email-sending mechanism I haven't picked yet — SMTP/SES, or a console-logged link for local dev, is an open question for implementation time), rate limiting, refresh token storage, a password-strength dependency, and an audit log, all before any circle/ledger-facing endpoint gets to sit behind real auth.

The auto-link policy depends entirely on correctly checking Google's `email_verified` claim rather than just the email field — that check has to be right, since it's the only thing standing between "safe, convenient linking" and the exact takeover vector this ADR is trying to avoid.
