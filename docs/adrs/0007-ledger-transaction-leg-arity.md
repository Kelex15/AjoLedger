# ADR 0007: Ledger Transactions Are Always Two Legs, Grouped by a Separate Operation Record

**Status:** Accepted

## Context

Payout currently posts one compound transaction: a debit against the circle's pot, and up to three credits (the recipient's savings, the platform buffer, and sometimes the recipient's collateral). The default waterfall doesn't do this. Its own compound event (seize collateral, then draw from the shared buffer) is already split into two separate transactions, linked only by sharing an idempotency-key string prefix, not any real foreign key.

This came up while designing a single source/destination account pair for the transaction API response. That pair is unambiguous for every transaction type except payout, where "the" destination account is genuinely one of three. The choice is either push a full leg list onto every response to handle payout's case, or make payout stop being the odd one out.

Worth being explicit about what this ADR is not: a transaction with more than two legs is completely legitimate double-entry accounting, and this codebase's own deferred database constraint (ADR 0004) already validates that a transaction's legs sum to zero regardless of how many there are. Nothing about the current shape is incorrect. This is a decision about what the ledger's own primitive should look like, not a correctness fix.

## Decision

Every ledger transaction is exactly one debit leg and one credit leg, no exceptions. A business event that touches more than two accounts becomes several such transactions, not one bigger one.

A new operation record groups the transactions belonging to one business event, tagged with a type and a reference id back to whatever triggered it. One call inserts the operation row and hands its id to the caller, who posts one transaction per transfer, tagging each with that operation id.

Atomicity is preserved by nesting, not by annotation. The operation call wraps its whole body in one `TransactionTemplate` execution, and each individual transaction post joins that already-active transaction instead of starting a new one. Several logical transactions commit or roll back together as one physical database transaction, with no `@Transactional` needed anywhere, keeping the transaction boundary a visible, explicit call rather than an annotation, consistent with the reasoning in ADR 0004.

Payout posts up to three transactions under one payout operation: the net payout, the buffer fee, and an optional collateral skim. The default waterfall's two-transaction split shares a real operation id instead of only an idempotency-key prefix. The payout record references the operation id directly, not any single transaction within it, so "every transaction this payout caused" is one query away rather than a second hop through whichever transaction happened to be recorded.

This also means folding what used to be a separate transaction-processing module into the ledger module itself, renamed to reflect that. The ledger genuinely is the transaction engine, and keeping them as separate modules was starting to raise its own questions, like who calls whom and who owns the database transaction boundary, that a single module resolves by construction.

## Alternatives considered

Keep the compound multi-leg transaction and make the API response return a full leg list instead of a source/destination pair. Rejected: it pushes the ambiguity onto every consumer instead of removing it, and every transaction type except payout genuinely has one clear source and one clear destination, so a leg list is the wrong default shape for the common case.

Relate a compound event's transactions with a self-referencing pointer on each transaction, pointing at a designated root transaction. This works for payout, which has a natural root (the net payout transaction), but not for the default waterfall, where either of its two transactions can be the only one that posts. There's no transaction reliably present to serve as the root. The operation record avoids this problem entirely, since nothing needs to be a root; every transaction in the group just points at the same operation.

Keep the ledger primitive's arity open, decided per call site, and rely entirely on the deferred database trigger for correctness. Still correct, since that trigger doesn't care how many legs a transaction has, but arity decided per call site is exactly what makes "this transaction's from and to accounts" impossible to answer generically.

## Consequences

The ledger's real primitive becomes a transfer between two accounts, even though the underlying method signature still takes a leg list for symmetry. It never needs to know what a payout or a fee is, only that money moves from one account to another, so every future money-moving feature reuses that same shape instead of inventing its own leg-list convention.

A compound business event costs one extra row (the operation record) and a join to see everything it did, in exchange for every individual transaction staying simple to read back on its own.

A success callback that let a caller learn a transaction's id after the fact becomes unnecessary. It existed only so payout could stamp that id onto its own payout record. Once the operation id is available up front, payout can set that reference directly instead, so the callback has no remaining caller and drops out of the API.
