# ADR 0013: A Shared, Read-Only Demo Account

**Status:** Accepted

## Context

Right now the only way to see what AjoLedger does is to sign up, verify an email, and fund an account. A lot of people just want to look around first. Some of them will decide to create an account after that, and some will only ever want a look. Either way, asking for an email address before they've seen anything is a lot to ask, and an empty new account wouldn't show them much anyway, since a circle only means something once it has members and rounds behind it.

Letting someone in without signing up goes against ADR 0006, which says no session is issued until the account's email is verified. So this is a deliberate exception, and it has to be safe enough that the verification rule isn't missed.

## Decision

There's one demo account that every visitor shares. It can see everything and change nothing.

A "Try the demo" button on the login page calls `POST /api/auth/demo`, which starts a session as the demo user. There's no password, so there's nothing to share or guess, and nobody can lock other visitors out by getting it wrong. The demo user has no credentials record, so no password login or Google link can ever reach it.

Users get an `is_demo` flag, and a demo session gets a `DEMO` authority. The security config only lets a `DEMO` session send `GET` requests, plus logout and refresh so the session can work and end. Every endpoint that changes anything is a `POST`, so that one rule blocks all of them, and since it's checked on the server, calling the API directly with the demo cookie doesn't get around it. This depends on `GET` endpoints never changing anything, which is true today and which I'm treating as a rule from here on.

Because nothing can be changed, the demo is only as good as what's already in it. The account's history is created once, at startup, by running the real flows: creating circles, joining them, closing enrollment and processing rounds through the same services the scheduler uses, with a few seeded member accounts around the demo user. That gives a visitor one circle that finished its rotation, one where a member defaulted and the waterfall covered the loss, an active circle where the demo user is waiting for their payout, and an open circle to browse. I didn't want to insert this history with SQL, because then the balances, fees and waterfall a visitor sees wouldn't actually have come from the code.

Seeded money comes from a new `DEMO_SEED` funding source with its own clearing account, so the ledger still balances. Labelling it as Stripe would make reconciliation report every seeded deposit as a payment Stripe never saw. Seeded accounts use addresses on the reserved `.invalid` domain, and the outbox skips them, so seeding never sends mail to addresses that would bounce.

The ledger stamps every entry with the time it's written and won't allow backdating, so all the seeded history shares one date. A banner on the demo says when it was generated and that it came from running the real flows. After that, the active circle carries on under the real scheduler.

Actions stay visible but disabled for the demo user, with a short note pointing to signup, so someone who likes what they see knows how to get an account of their own.

## Alternatives considered

The simplest version was a shared account with its password on the login page and full access. Anyone could lock everyone else out by getting the password wrong five times, change the email or password, or leave behind a circle name the next visitor would see. A nightly reset would usually fix that, but the ledger can't be reset.

A separate guest account for each visitor, with write access, would let people actually try deposits and joining circles without getting in each other's way. I held off because every guest leaves permanent ledger rows behind, and each one would need its own populated circles to be worth anything. It's a reasonable step to take later, and nothing here rules it out.

## Consequences

Seeded data is permanent like everything else in the ledger, and it's marked as demo so it can be excluded from anything that counts real usage.

Seeding only runs when no demo user exists. Once the active circle finishes its rotation, refreshing the demo will need a deliberate re-seed, which I'll add when it's needed. Until then, the seeded members need enough money to keep paying into that circle.

Each click on the demo button creates a refresh token and an audit row. That's fine at this traffic, and per-IP rate limiting is the answer if it's ever abused.
