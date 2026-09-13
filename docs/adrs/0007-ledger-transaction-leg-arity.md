# ADR 0007: Ledger Transactions Are Always Two Legs, Grouped by a Separate LedgerOperation

**Status:** Accepted

## Context

`postPayout` currently posts one compound transaction: a debit against the circle's pot, and up to three credits (the recipient's `SAVINGS`, `SYSTEM_BUFFER`, and sometimes the recipient's `LIEN`). `postDefaultWaterfall` doesn't do this. Its own compound event (seize `LIEN`, then draw from the shared buffer) is already split into two separate transactions, linked only by sharing an idempotency-key string prefix, not any real foreign key.

This came up while designing a `fromAccountId`/`toAccountId` pair for the transaction API response. That pair is unambiguous for every transaction type except payout, where "the" destination account is genuinely one of three. The choice is either push a full leg list onto every response to handle payout's case, or make payout stop being the odd one out.

Worth being explicit about what this ADR is not: a transaction with more than two legs is completely legitimate double-entry accounting, and this codebase's own deferred Postgres constraint trigger (ADR 0004) already validates that a transaction's legs sum to zero regardless of how many there are. Nothing about the current shape is incorrect. This is a decision about what the ledger's own primitive should look like, not a correctness fix.

## Decision

Every `LedgerTransaction` is exactly one debit leg and one credit leg, no exceptions. A business event that touches more than two accounts becomes several such transactions, not one bigger one.

A new `LedgerOperation` header (`operation_type`, `reference_id`) groups the transactions belonging to one business event. `LedgerService.postLedgerOperation(operationType, referenceId, body)` inserts the operation row and hands its id into `body`, which calls `postLedgerTransaction` once per transfer, tagging each with that operation id.

Atomicity is preserved by nesting, not by annotation. `postLedgerOperation` wraps its whole body in one `TransactionTemplate.execute(...)` call, and each nested `postLedgerTransaction` call's own `TransactionTemplate.execute(...)` joins that already-active transaction (default `REQUIRED` propagation) instead of starting a new one. Several logical transactions commit or roll back together as one physical database transaction, with no `@Transactional` needed anywhere, keeping the transaction boundary a visible method call, consistent with the reasoning in ADR 0004.

`postPayout` posts up to three transactions under one `PAYOUT` operation: the net payout, the buffer fee, and an optional lien skim. `postDefaultWaterfall`'s two-transaction split shares a real operation id instead of only an idempotency-key prefix. `CirclePayout` references the `LedgerOperation` id directly, not any single transaction within it, so "every transaction this payout caused" is one query away rather than a second hop through whichever transaction happened to be recorded.

This also means retiring the `transaction` package in favor of folding it into `ledger`: `TransactionService`/`Transaction`/`TransactionType` become `LedgerService`/`LedgerTransaction`/`LedgerTransactionType`. The ledger genuinely is the transaction engine, and keeping them as separate packages was starting to raise its own questions, like who calls whom and who owns the database transaction boundary, that a single package resolves by construction.

## Alternatives considered

Keep the compound multi-leg transaction and make the response DTO return a full leg list instead of a from/to pair. Rejected: it pushes the ambiguity onto every consumer instead of removing it, and every transaction type except payout genuinely has one clear source and one clear destination, so a leg list is the wrong default shape for the common case.

Relate a compound event's transactions with a self-referencing `related_transaction_id` on `LedgerTransaction`, pointing at a designated "root." This works for payout, which has a natural root (the net payout transaction), but not for the default waterfall, where either of its two transactions can be the only one that posts. There's no transaction reliably present to serve as the root. `LedgerOperation` avoids this problem entirely, since nothing needs to be a root; every transaction in the group just points at the same operation.

Keep the ledger primitive's arity open (`List<Leg>`, decided per call site) and rely entirely on the deferred trigger for correctness. Still correct, since that trigger doesn't care how many legs a transaction has, but arity decided per call site is exactly what makes "this transaction's from and to accounts" impossible to answer generically.

## Consequences

The ledger's real primitive becomes a transfer between two accounts, even though `postLedgerTransaction`'s signature still takes a leg list for symmetry. It never needs to know what a payout or a fee is, only that money moves from one account to another, so every future money-moving feature reuses that same shape instead of inventing its own leg-list convention.

A compound business event costs one extra row (the `LedgerOperation`) and a join to see everything it did, in exchange for every individual transaction staying simple to read back on its own.

`postLedgerTransaction`'s `onSuccess` callback becomes unnecessary. It exists today only so `postPayout` can learn a transaction's id after the fact, to stamp onto `CirclePayout`. Once `postLedgerOperation` hands the operation id out up front, `postPayout` can set `CirclePayout.operationId` directly instead, so that parameter has no remaining caller and drops out of the API.
