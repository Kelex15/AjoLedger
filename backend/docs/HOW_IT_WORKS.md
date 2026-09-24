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

## Double-entry ledger: transactions, legs, and the write path

**What it does (one sentence):** Every money movement is recorded as one `transaction` header row plus exactly two `ledger_entry` "leg" rows — one leg per account touched, debited or credited by a specific amount — written together so the books can never go unbalanced.
**Why it exists:** ADR-0004 requires the ledger to be auditable at the database level, not an emergent property of application code behaving correctly. Standard double-entry accounting's answer to "prove the books always balance" is: break every event into legs, and require the sum of debits to always equal the sum of credits for that event. "Leg" is the term for one of those pieces — one account, one direction (debit or credit), one amount.
**How it works, step by step:**
1. A business event is broken into legs before anything is written — always exactly 2 (debit one account, credit the other). A compound event that touches more than two accounts (a payout: pot, recipient `SAVINGS`, `SYSTEM_BUFFER`, and sometimes recipient `LIEN`) isn't one bigger transaction — it's several 2-leg transactions, one per pair, grouped under one `LedgerOperation` header row (`operation_type`, `reference_id`) so they're queryable together (`WHERE operation_id = ?`) without needing more than 2 legs on any single transaction.
2. `LedgerService.postLedgerOperation` inserts the `LedgerOperation` row and hands its id into a callback, all inside one `TransactionTemplate.execute(...)` boundary; the callback then calls `postLedgerTransaction` once per 2-leg sub-event, tagging each with that operation id. Nested `TransactionTemplate.execute` calls join the same physical transaction (default `REQUIRED` propagation), so every sub-transaction commits or rolls back together with no `@Transactional` annotation needed anywhere.
3. `LedgerService.postLedgerTransaction` locks every account a given pair of legs touches (`SELECT ... FOR UPDATE`, deterministic order to avoid deadlocks), checks the debit leg's account actually has enough balance, and only then writes the `transaction` header row and its 2 `ledger_entry` rows together, adjusting each account's cached balance by its net change.
4. A deferred Postgres trigger checks, at commit time, that every transaction's legs sum to zero and that there are at least two of them — the database itself refuses an unbalanced write, it isn't just trusted application logic.
5. Every write also carries an idempotency key so a retried request can't double-process.
**Key files:** `backend/src/main/java/com/ajoledger/backend/ledger/LedgerService.java` (the `postLedgerOperation`/`postLedgerTransaction` helpers), `Leg.java`, `LedgerOperation.java`, `LedgerDao.java`, `db/migration/V3__create_ledger_tables.sql` (the balance-check trigger), `V16__add_ledger_operation_and_widen_transaction_type.sql` (the operation-grouping table).
**You should know:** Why "leg" specifically, rather than treating debits and credits as separate standalone concepts — it's the unit a transaction is built out of, and the balancing invariant is defined in terms of legs summing to zero *within one transaction*, not in terms of any single leg being correct on its own. Also why a compound event is several linked 2-leg transactions rather than one N-leg transaction: it keeps "from account"/"to account" unambiguous for every transaction, no per-event-type special-casing needed to read one back.

## Circle reserve account and gradual default recovery

**What it does (one sentence):** When a defaulting member's lien and the shared buffer recover money on the group's behalf, that recovery lands in a per-circle reserve account and gets released into the pot in even shares across the circle's remaining rounds, instead of landing as a lump sum on whichever round happens to resolve next.
**Why it exists:** The default waterfall (design/v2.md Section 5) sizes what it seizes as a buyout of the defaulter's entire remaining contribution obligation, not just one round's shortfall — so crediting it straight to the pot would make one arbitrary future recipient better off purely by chance, instead of fairly spreading the recovered loss across everyone the default actually affected.
**How it works, step by step:**
1. `CircleAccount` gained a `circleAccountType` discriminator (`POT`/`RESERVE`), mirroring how `UserAccount` already distinguishes `CHECKING`/`SAVINGS`/`LIEN`/etc. Every circle gets both accounts at creation.
2. `postDefaultWaterfall`'s two legs (`LIEN_SEIZURE`, `BUFFER_PAYOUT_TO_COVER_DEFAULT`) now credit the circle's `RESERVE` account instead of its `POT`.
3. Each time `resolveRoundIfComplete` is about to pay out a round, it first checks the reserve's balance. If it's nonzero, it releases `reserveBalance ÷ roundsRemaining` into the pot as a `CIRCLE_RESERVE_DISBURSEMENT` transaction, where `roundsRemaining` counts the current round plus every round still ahead.
4. Because the release is recalculated from the reserve's live balance every round rather than following a schedule fixed at default time, a second default before the first has fully released simply folds into the same ongoing release, and the circle's last round always empties the reserve exactly (dividing by 1 leaves nothing behind).
**Key files:** `backend/src/main/java/com/ajoledger/backend/circle/CircleService.java` (`postDefaultWaterfall`, `releaseReserveShare`, `resolveRoundIfComplete`), `account/entity/CircleAccount.java`, `account/CircleAccountType.java`.
**You should know:** Why the reserve is per-circle rather than folded into the shared platform-wide buffer — the buffer absorbs losses the lien can't cover at all, while the reserve only ever holds money that's already been recovered on one specific circle's behalf, so it has to stay scoped to that circle to be released back into that circle's own rounds.

## Reputation-based payout position assignment

**What it does (one sentence):** When a circle locks, its members' payout order isn't join order — it's decided by a reputation score blending each member's lifetime track record with how much of their own `LIEN` already covers the risk of an early slot, and the highest score gets position 1.
**Why it exists:** The collateral formula (design/v3.md Section 4) already lets a member with enough banked `LIEN` take an early slot without new cash. Deciding *who* actually gets that slot by reputation instead of who happened to click join first rewards proven reliability with something people actually want, and it naturally lines up with the collateral math, since a high-reputation member plausibly already has the `LIEN` to back it.
**How it works, step by step:**
1. `activateCircle` fetches every `ACTIVE` membership, sets `lockedMemberCount`, then scores each member once via `computeReputationScore` before sorting — never inside the comparator itself, since a sort can invoke it more than once per element.
2. The score blends a Laplace-smoothed lifetime success rate (`COMPLETED` memberships vs. `WITHDRAWN`/`DEFAULTED`, with a default penalized 3× a withdrawal) with a `LIEN` coverage ratio (current balance against the circle's worst-case exposure at position 1).
3. Members are sorted by score descending, tiebroken by `joinedAt`, and positions 1..N are assigned in that order.
**Key files:** `backend/src/main/java/com/ajoledger/backend/circle/CircleService.java` (`activateCircle`, `computeReputationScore`), `CircleMembershipRepository.countByUser_IdAndStatus`.
**You should know:** Why the score is computed live from `CircleMembership` rows instead of a running counter on `User` — same reasoning as balances being aggregated from the ledger rather than cached: a live count can't drift out of sync with the rows it's supposed to summarize.

## Ledger hash chain: tamper-evident audit trail

**What it does (one sentence):** Every `ledger_operation`/`transaction`/`ledger_entry` row gets hashed into one append-only chain, sealed by a background job rather than at write time, so a later verification pass can prove whether any row's content has changed since it was sealed.
**Why it exists:** ADR-0010: the ledger's append-only triggers stop ordinary UPDATE/DELETE, but don't prove history hasn't been rewritten by something that bypasses them. Hashing every row's content, chained to the one before it, turns "did anything change" into something that can actually be checked instead of assumed.
**How it works, step by step:**
1. `LedgerHashChainSealingScheduler` runs on a cron (`ajoledger.scheduler.hash-chain-seal-cron`, default every minute) and calls `LedgerHashChainSealingService.sealPendingRows()`.
2. `LedgerHashChainDao.findPendingRows()` finds every row across the three ledger tables with no matching `ledger_hash_chain_entry` yet, ordered by `insert_seq` (a tiebreaker column added purely because sibling legs of one transaction share an identical `created_at`).
3. For each pending row, `LedgerHashChainHasher.compute` hashes its table name, its position in the chain, the previous entry's hash, and its own current content (`to_jsonb(row)`), chaining off whatever the last seal left as the tip.
4. `LedgerHashChainVerificationService.verifyChain()` walks the whole chain, refetches each covered row's *current* content, recomputes the same hash, and compares it against what was stored at seal time. A content mismatch or a broken `prev_hash`/`row_hash` link between neighbors means something was altered or removed after sealing.
5. Two dev-only endpoints (`POST /api/dev/ledger/seal-hash-chain`, `GET /api/dev/ledger/verify-hash-chain`) trigger sealing and verification on demand, without waiting on the cron.
**Key files:** `backend/src/main/java/com/ajoledger/backend/ledger/hashchain/` (`LedgerHashChainDao.java`, `LedgerHashChainSealingService.java`, `LedgerHashChainVerificationService.java`, `LedgerHashChainHasher.java`), `scheduler/LedgerHashChainSealingScheduler.java`, `db/migration/V29__replace_ledger_hash_chain_with_async_sealing.sql`.
**You should know:** This was originally a synchronous trigger computing the hash on every INSERT, which collapsed ledger write throughput to roughly 500-1,200 tps regardless of concurrency (measured), because every insert across all three tables serialized on one lock. Moving hashing off the write path into a periodic batch job removed that cost entirely, at the price of tamper evidence having a delay equal to the sealing interval rather than being immediate. Also worth knowing: this proves internal self-consistency, not immunity from someone with database migration authority, who could in principle alter both a row and its recorded hash together. See ADR-0010 for the full reasoning, the rejected alternatives, and that limitation.
