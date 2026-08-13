# AjoLedger — Challenges Log

Real problems hit while building this, and how I solved them — the messy part in between the ADRs (decisions made before writing code) and HOW_IT_WORKS.md (how the finished mechanism works).

## Deciding not to track payout amounts/accounts in CirclePayout

**Date:** 2026-08-13
**What I was trying to do:** Model a way to track a circle's rotation payouts — who was paid, in which round — and considered also recording which account received the money (personal balance vs. a lien/collateral account) once that idea came up.
**What went wrong:** I almost added an `account` reference (and implicitly, an amount) directly to the `CirclePayout` entity so the record would show exactly where payout money landed.
**Why it happened:** I hadn't yet checked that idea against ADR 0004's reasoning — that actual money movement (crediting accounts, in whatever split) is deliberately the ledger's job alone, so there's one source of truth for where money went. Adding amount/account fields to `CirclePayout` would have created a second, parallel record of money movement that could drift from whatever the real ledger transaction eventually records.
**How I fixed it:** Kept `CirclePayout` as a pure rotation-schedule fact — circle, recipient, round number, timestamp — with no amount or account field. The actual crediting, including any lien/collateral account split, stays entirely the ledger's responsibility once Phase 3 exists. Logged the lien-account idea itself in the design doc's open questions (Section 7) instead of building it prematurely.
**What I'd do differently / what this taught me:** Before adding a money-shaped field to a non-ledger entity, check it against the ledger boundary in ADR 0004 first — "would this create a second place that claims to know where money went" is the concrete test, not just "does this field seem useful."
**Interview framing:** While designing the circle-rotation entities, I almost added an account/amount field to track payouts directly — then realized that would create a second, driftable record of money movement running in parallel to the ledger I'd already decided (ADR 0004) should be the single source of truth for that. I kept the rotation record as pure schedule metadata and left actual money movement to the ledger.
