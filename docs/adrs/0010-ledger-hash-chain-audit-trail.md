# ADR 0010: Async Batch-Sealed Hash Chain for Ledger Tamper Evidence

**Status:** Accepted

## Context

Append-only triggers on the ledger's core tables stop ordinary UPDATE/DELETE, but nothing proves that history hasn't been rewritten by something that bypasses those triggers. I wanted an answer to a specific question: if someone with enough DB privilege disabled a trigger and edited a historical amount, would anything ever notice.

## Decision

**Hash every row the ledger writes into one append-only chain, sealed asynchronously by a scheduled job instead of at insert time.**

A new append-only table records, per sealed row: its position in the chain, a pointer back to the row it covers (which table, which id), the previous entry's hash, and its own hash. A scheduled job finds every ledger row with no matching chain entry yet and, for each one, hashes its table name, its position, the previous entry's hash, and its own current content together, chaining off whatever the last run left as the tip. A separate verification routine walks the whole chain, refetches each covered row's current content, recomputes the same hash, and compares it against what was recorded at seal time. A mismatch means the row's content changed after sealing. A broken link between neighboring entries means one was removed or reordered.

The hash covers the row's actual content, not just its identity. A chain of bare references would prove a row with a given id existed at a given position, but nothing would catch someone changing an amount on that same row afterward, which is the one thing this exists to catch. Hashing the row generically, rather than a hand-picked list of fields, means the hash never silently drops a newly added column as the schema evolves, at the cost of also covering fields that aren't strictly money-relevant. That's a conservative tradeoff I'm fine with.

Sealing happens outside any lock the application's own writes touch. It only reads the ledger's tables (ordinary reads, no blocking) and writes to a table nothing else touches. A row not yet committed when a seal pass runs is simply invisible to that pass and gets picked up by the next one. Each ledger table also gets a purely internal, monotonic counter, used only as a tiebreaker for the seal job's own ordering, since rows belonging to the same originating transaction can share an identical timestamp.

## Alternatives considered

**Hashing synchronously, at insert time, instead of in a batch afterward.** Simpler to reason about since every row is chained the instant it's written, no seal latency. Measured against a throwaway benchmark replicating that approach: throughput plateaued at a small fraction of unhashed throughput regardless of concurrency, because every insert had to acquire the same lock to read the current tip and advance it. At meaningful concurrency that's over an order of magnitude of throughput lost, and proportionally higher latency. Rejected once measured; a synchronous per-row lock doesn't scale past light concurrency, and this is a financial ledger, not a place to accept that tradeoff by default.

**Independent chains per table instead of one shared chain**, on the theory that removing cross-table contention would let unrelated writes proceed in parallel. Also measured, using a workload matching this system's real ratio of row types per event. Splitting didn't meaningfully help. The highest-volume table alone accounts for most of the write load in any real event, so its own independent lock has to serialize nearly the same volume the shared lock already was. The bottleneck was never really about which lock, it was synchronous hashing itself.

**Storing each covered row's content alongside its hash**, not just a pointer to it. Rejected as a straightforward duplication of the ledger for no benefit: verification always refetches the current content anyway, since that's the whole point of comparing current state against a historical commitment, so archiving a second copy alongside the hash buys nothing.

## Consequences

Tamper evidence has a delay equal to the sealing interval, not immediate. A row written moments ago and not yet sealed carries the same protection an unsealed row always had: none, until the next pass picks it up. A short default interval makes that window small, not zero.

This proves internal self-consistency, not immunity from every actor. Someone with enough privilege to disable a trigger and edit a historical row could, with equal privilege, edit the chain's own stored hashes to match, since both live in the same database under the same authority. More pointedly, anyone with commit access and a working deployment pipeline could ship a change that drops the append-only protections, mutates whatever it wants, and restores them, leaving no trace inside the database itself. That's not a gap specific to this design, it's the ceiling of what any single-database, single-authority hash chain can promise. The real fix is external anchoring: periodically publishing the current tip hash somewhere outside this system's own deploy authority, so a change that rewrites history can no longer produce a chain that matches a tip already witnessed elsewhere.

The sealing job assumes one caller at a time, consistent with every other scheduled job in this codebase (ADR 0002's modular monolith is a single deployable). If this ever ran on more than one instance, sealing would need explicit coordination to avoid two passes racing on the same tip. That coordination problem is once-per-interval, not once-per-write, so it doesn't reintroduce the throughput cost the synchronous version had.

Two developer-only endpoints exist to trigger sealing and inspect verification results on demand, without waiting on the schedule.
