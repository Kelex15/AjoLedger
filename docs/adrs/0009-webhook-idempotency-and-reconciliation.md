# ADR 0009: Webhook Event Idempotency and Reconciliation Policy for Sandbox Payments

**Status:** Accepted

## Context

The funding module today is an explicit stand-in for Phase 6: a caller credits or debits their own checking account directly through a manual API call, with no processor behind it at all. The Stripe and PayPal clearing account types exist only as labels, unused by any real processor integration. Real Phase 6 work replaces that with an actual Stripe sandbox integration: a hosted checkout flow, a webhook that confirms payment asynchronously, and funds landing in the user's account only once Stripe confirms, not on the initiating API call.

Two things about that flow don't have an existing answer in this codebase, and I want to settle them before writing any of it. I'm treating Stripe-only scope (PayPal stays a label with no processor code) as established context here, not a decision this ADR needs to weigh. It has no real alternative architecture behind it; it's a scope call, not a tradeoff with consequences for the data model.

**Webhook idempotency.** Stripe delivers webhook events at-least-once. The same event can arrive twice, and delivery can arrive out of order relative to other events. The ledger write path already has an idempotency mechanism, but it's built for client-initiated requests: a caller supplies a key, and a second call with the same key either replays the first result or is rejected as a conflict if the request body differs. A webhook event isn't that. Its identity is Stripe's own event id, not anything the caller chose, and a redelivery of the same event is never a "conflicting" request. It's the literal same event arriving twice, and it should be processed exactly once regardless of how many times Stripe sends it.

**Reconciliation.** A webhook can fail to arrive at all: a deploy mid-delivery, a transient failure on my endpoint, a bug that throws before the DB write commits. When that happens, Stripe believes the payment succeeded but nothing lands in the ledger, and nothing today would ever notice.

## Decision

**A dedicated table for received webhook events**, not a reuse of the existing idempotency mechanism. It records which processor and event id it covers, the raw payload, when it was received, when it was processed, and which ledger transaction resulted, with a uniqueness guarantee on the processor/event id pair. The webhook handler claims a row for that pair before doing any ledger write; a redelivery finds the row already marked processed and returns success without reprocessing, while a redelivery of an event whose prior attempt failed retries the processing rather than being treated as claimed. Keying on the processor alongside the event id, rather than the event id alone, means this table isn't Stripe-specific. If PayPal's webhook ever goes from stub to real, it's the same table, just a different processor value.

**A scheduled reconciliation job**, following the same shape as the codebase's other scheduled jobs: a public entry point delegating to an internal worker, with per-item error handling so one bad comparison doesn't sink the run. For a lookback window, it pulls Stripe's confirmed payments and compares both directions: every Stripe-confirmed payment should have a matching received-event row whose recorded transaction amount agrees, and every ledger transaction tagged as Stripe-sourced should still be a payment Stripe recognizes. Every mismatch is logged as an error and written to its own record: which processor and event, what kind of mismatch, expected versus actual amount, and when. The job never writes to the ledger or to any account balance. It only observes and records.

## Alternatives considered

Reusing the existing idempotency mechanism for webhook events, treating the event id as the idempotency key. Less new schema, and the existing claim/succeed/fail machinery already does most of the mechanical work. Rejected because its conflict semantics don't fit: it's built to detect a client reusing a key for a genuinely different request, which can't happen with a webhook since the same event id is always the same event. Forcing it through that model also means an in-progress or failed webhook attempt looks identical to a rejected conflict, when what I actually want on redelivery of a failed attempt is a retry, not a rejection.

Auto-correcting reconciliation mismatches that look safe, for example a Stripe-confirmed payment with no conflicting ledger entry, which looks like a straightforward "webhook was lost, so post it now." Rejected because getting that classification right in every case is exactly the kind of judgment call I don't trust an unattended job with on money movement. A timing edge case (the webhook is simply still in flight when the job runs) and a genuinely lost webhook look identical from the job's side, and treating the former as the latter means auto-posting a transaction that's about to arrive for real. Log-and-alert keeps a human in the loop for the one call that actually needs one, at the cost of mismatches sitting unresolved until someone looks. That's an acceptable cost here since this is sandbox money, not real settlement.

Publishing webhook events onto a queue instead of processing them in the same request, with the received-event table acting as an outbox. Not rejected on the merits, this is the actual direction funding is headed once it's extracted into its own service, at which point a real dual-write problem exists (the funding service's database and the queue are separate systems) and an outbox genuinely earns its place. Rejected for now specifically because that extraction hasn't happened yet. While funding and the ledger share one database, claiming the idempotency row and posting the ledger transaction can just be one local transaction, so a queue would add a broker, a consumer, and a second failure mode to solve a problem this codebase doesn't have yet. Revisit this decision, not the reasoning, when the extraction ADR lands.

## Consequences

Two new tables, one migration. Storing the raw payload of each received event isn't just for debugging. Once a human confirms a mismatch is a lost webhook, that payload is what makes manual replay possible without going back to Stripe's API.

The reconciliation job's lookback window has to be wider than its own run interval, or a single missed run creates a permanent blind spot for whatever fell in the gap.
