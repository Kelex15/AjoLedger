# ADR 0011: Server-Side Capture and Local Order Tracking for PayPal Deposits

**Status:** Accepted

## Context

ADR 0009 settled webhook idempotency and reconciliation against Stripe, deferring PayPal so those questions could be answered against one rail first. PayPal itself was never the open question: `CLEARING_PAYPAL` has been in the account type constraint since V2 and seeded since V5.

What is open is that the existing design assumes a rail which settles itself. Stripe Checkout takes the money as part of its hosted flow, so the webhook and the checkout-return path only ever observe a finished charge. PayPal's Orders flow leaves an approved order uncaptured until this system calls capture, which breaks that assumption in three places: when the money actually moves, how an inbound webhook is trusted, and what reconciliation has to compare against.

## Decision

**Capture on the webhook. The browser return is the self-heal.**

Calls go through PayPal's own server SDK, `com.paypal.sdk:paypal-server-sdk`, which covers the Orders and Payments APIs and handles OAuth token acquisition and refresh on its own. That matches how Stripe and Google are already wired up.

With capture intent, PayPal leaves the order approved but uncaptured after the buyer agrees. Money moves when my server calls capture. So the approved-order event triggers capture, and the capture-completed event posts the ledger deposit. The return endpoint does the same work on demand if the webhook is slow or lost, like `verifyAndHeal` does for Stripe. Capture accepts a request id header, so a second capture returns the first result instead of charging twice. Both paths can race.

One difference from Stripe matters here. Stripe Checkout takes the money itself, so its webhook and self-heal only read: they observe a completed charge and write a ledger row. With PayPal both paths move money. A bug in the Stripe self-heal writes a bad ledger row, and reconciliation or the hash chain catches it. A bug in the PayPal self-heal charges a buyer. Capture therefore waits on PayPal saying the order is approved. It does not fire because a browser showed up at the return URL carrying an order id.

The deposit posts on capture-completed, not on approval. Approval only means the buyer agreed, and capture can still fail after that. Posting on approval would credit accounts for money that never arrived.

**Trust the webhook by re-fetching the order, not by verifying its signature.**

Read the order id off the payload, ignore the rest of it, fetch the order from PayPal, and decide from that. Status and the captured amount come from the fetch, since that is what PayPal actually took. The user and idempotency key come from this system's own record of the order rather than anything PayPal echoes back. `verifyAndHeal` already works this way for Stripe's return path; this applies the same approach to the webhook.

PayPal's signature verification is itself an API call back to PayPal, so both options cost a round trip. A signature proves the payload came from PayPal. A fetch proves what PayPal thinks right now, which is what the ledger write depends on. A forged event with a guessed order id causes one lookup and nothing else. The endpoint stays unauthenticated in `SecurityConfig` alongside Stripe's, so anyone can post to it, but nothing they post reaches a ledger write.

**Reconcile against orders this system created.**

Reconciliation needs the set of payments this system started, so it can find the ones that never came back. `ReconciliationService` gets that set from Stripe's session list API. Orders v2 has no list endpoint at all, and the only PayPal listing that exists, Transaction Search, lags by hours and is unreliable in sandbox, so comparing against it would flag payments that are merely late to a report.

With no processor-side listing to walk, the set has to be local. Creating an order records it: order id, user, idempotency key, amount, currency, timestamp. Reconciliation reads that table for the lookback window and looks up each order's current state, which is a plain order lookup with no reporting lag. Captured with no ledger entry is a missing entry. Captured for a different amount than the ledger holds is an amount mismatch. Approved but still uncaptured past a grace window is new to this rail and gets its own mismatch type.

That same record is what the webhook reads to learn which user and idempotency key an order belongs to, so it is on the deposit path rather than serving reconciliation alone.

The cost is that this can't find a PayPal payment that didn't originate here, which Stripe's listing would catch. Since capture only ever happens on this side, an order with no local record is never captured and no money moves, so the gap costs a stalled checkout rather than an untracked payment. As in 0009, the job only reads and records mismatches. It never captures.

**A discrepancy visible at settle time is recorded there, not left to the next run.**

The ledger credits what PayPal captured, so a capture for a different amount than was requested still posts. Both numbers are in hand at that moment, so the mismatch is written then. Reconciliation keeps its own amount check for drift that appears later, such as a refund or reversal after the fact.

## Alternatives considered

**Capture from the browser return only, no webhook.** Less to build: no webhook endpoint, no second processor in the events table, and the user is still there to see whether it worked. Rejected because taking the money then depends on the browser coming back. Close the tab after approving and nobody captures, the authorization expires, and the user never learns the deposit didn't happen. The Stripe integration avoids relying on its return path for the same reason.

**Authorize now, capture later.** PayPal supports holding an authorization and capturing when a circle needs the funds. Rejected because a deposit in this ledger credits checking immediately and circles draw on that balance afterward. An uncaptured authorization behind a credited balance means the ledger claims money nobody has taken, on an expiry clock PayPal controls.

**Verify signatures as well as re-fetching.** Rejected for now. The fetch already establishes everything the ledger write needs, so a signature check adds a call and a failure mode to guard work that's already guarded. What it would buy is cheap rejection of garbage before real work starts, and that only pays off at volumes of forged events this system isn't seeing.

**Calling the REST API directly through `RestClient` instead of the SDK.** The surface needed here is small, so hand-rolling it is feasible: fetch a token, create an order, capture it, look one up. Rejected because the token half of that is the part worth not writing. Acquisition, caching, refresh before expiry, and retrying a call that fails on a stale token is a chunk of stateful code whose failure mode is silent and intermittent, and the SDK already does it. Every other third-party integration in this project goes through the vendor SDK as well, so hand-rolling PayPal would be the one exception for no gain.

## Consequences

Amount conversion doesn't carry over. `StripeAmounts` turns integer minor units into a decimal using `Currency.getDecimalScale()`. PayPal wants a decimal string with exactly that many places, plus the currency code. PayPal also accepts fewer currencies per account than the four in `Currency`, so currency support becomes a per-rail question.

One migration: the order table, and a wider check constraint on mismatch type. The events table needs no change, since 0009 keyed it on processor and event id.

The grace window for uncaptured orders has the same trap as 0009's lookback window. Set it too short and the job invents mismatches for orders that were fine.

Every deposit now returns a pending redirect, so `FundingController.deposit` no longer has a branch that credits an account synchronously. That branch was the one a user could reach by picking PayPal while it was still a label, crediting themselves for free.

The order record is written before the caller gets its redirect, so failing to record one means no redirect and an order left to expire unapproved. An order PayPal holds that this system has no record of is never reachable by a buyer.

Withdrawals don't change. They stay ledger-only on both rails. PayPal Payouts is a separate API with its own onboarding, and Stripe has no real payout path either, so both get done together later.
