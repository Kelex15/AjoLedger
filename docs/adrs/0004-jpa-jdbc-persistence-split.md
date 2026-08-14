# ADR 0004: Split Persistence Strategy — JPA for State, JdbcTemplate for the Ledger

**Status:** Accepted

## Context

There are two very different kinds of data in this backend: low-stakes CRUD state (circles, memberships, user profiles, auth) and actual money movement (contributions, payouts, collateral seizure, buffer transfers). The ledger side has to be true double-entry — every transaction touches two accounts, debit and credit, always netting to zero — append-only, and safe under concurrent writes to the same account. I want to be able to explain exactly how atomicity and concurrency safety are guaranteed at the database level, not wave my hands and say "`@Transactional` handles it."

That phrase is actually the crux of the problem. `@Transactional` on a JPA call does give you a real DB transaction, but what SQL runs and when is up to Hibernate's persistence context — entities sit dirty in memory until a flush happens, and flush timing depends on flush mode and commit, not necessarily the moment `save()` is called. That's enough indirection that I can't confidently point at "this row is locked, under this isolation level, right here" — and that's exactly the question that matters for preventing a concurrent double-spend on the same account.

## Decision

Split by data category instead of picking one approach for everything.

State — circle config, member profiles, membership status — goes through Spring Data JPA. It's ordinary CRUD with nothing unusual about its transactional needs, so JPA's productivity is free money here.

The ledger (`transaction`/`ledger` packages) is hand-written SQL through `JdbcTemplate`, not JPA entity saves. Every ledger write is explicit SQL, with the isolation level and locking strategy (`SELECT ... FOR UPDATE`, say) chosen deliberately per operation and documented, instead of being an emergent property of Hibernate's flush behavior. `@Transactional` still wraps the outer atomicity boundary — both legs of a double-entry write roll back together if either fails — the difference is that concurrency control inside that boundary is explicit, not implicit.

The schema itself enforces double-entry and append-only where it can, rather than relying on application code to behave. And every money-moving write carries an idempotency key so a retried request can't double-process.

## Alternatives considered

Using JPA uniformly, including for the ledger, was the default I moved away from — for the reason above: I can't point at the exact SQL and the exact lock behind Hibernate's behavior, and for a system whose entire point is auditable correctness, that gap isn't acceptable.

Going hand-written SQL everywhere, even for the CRUD state, would've been overkill in the other direction — that data doesn't have unusual transactional needs, so the extra boilerplate wouldn't buy anything.

I also considered jOOQ for the ledger instead of raw `JdbcTemplate` — more compile-time safety than string SQL — but decided the actual goal here is being able to walk through the ledger's SQL line by line in an interview, and a query-builder library gets in the way of that more than it helps.

## Consequences

Having two persistence idioms in one small codebase is unusual, but it's deliberate — the ledger's hand-written approach exists specifically to show the isolation/locking reasoning the JPA side doesn't need to.

The ledger needs its own Testcontainers-based tests against real Postgres, targeting concurrent writes to the same account, since there's no ORM behavior to lean on for confidence — that's already planned for Phase 8. Hand-managed SQL also means schema evolution needs a real migration tool (Flyway or Liquibase) instead of JPA's `ddl-auto` — which one, I haven't decided yet, and should before Phase 3 starts.
