# ADR 0012: Transactional Email via an Outbox and Resend

**Status:** Proposed

## Context

Email has never actually worked in production. `ConsoleEmailSender` writes the verification link to the logs, and it's the only implementation of `EmailSender`, so right now someone who signs up on the live site has no way to verify unless I dig the link out of the container logs for them.

There are three places that send email: the verification link in `AuthService`, default notifications in `CircleRoundService`, and circle invites in `CircleService`. All of them call the sender in the middle of a database transaction. That was fine while the sender only logged. Once it makes a real HTTP call, a slow provider keeps the transaction open, and a provider error can roll back whatever triggered the email. The default notification is the one that bothers me most. By the time it's sent, the waterfall has already moved money, and I don't want an email outage to be able to undo that.

ADR 0009 talked about an outbox and put it off because webhook processing never wrote to two systems at once. Email does. Postgres and the email provider can't commit together, so this is the case that ADR was waiting for.

## Decision

I'm splitting sending into two steps. The code that wants an email sent still calls `EmailSender`, but the implementation now just inserts a row into an `email_outbox` table. Because that insert happens in the caller's transaction, the email is queued only if the rest of the work commits. None of the three callers have to change.

A scheduled job, built like the others in `scheduler/`, picks up rows that are due and sends them through a transport. There are two transports: Resend in production, and the current console logger for local development. Each row's result is saved in its own short transaction, so the HTTP call never happens while a transaction is open.

The row stores the recipient's address as plain text and has no foreign key to `user`. Signup deletes stale unverified accounts so the real owner of an email can try again, and I don't want a queued email to block that delete or get wiped out by it.

How a failure gets retried depends on what Resend says. Only a 400 or 422 means this particular email can never go out, usually because of a malformed address, so those rows are marked failed right away. Everything else gets retried with exponential backoff, starting at 30 seconds and doubling, tracked as a next-attempt time on the row. That includes timeouts, 5xx responses and 429s, and it also includes 401 and 403. Those mean a bad API key or a sending domain that isn't verified yet. That's a problem with my configuration, not with the email, and if I deploy before the DNS records verify I don't want every queued email thrown away. After eight attempts, about an hour of trying, the row is marked failed. Every failure is logged as an error, and the provider's response is kept on the row. Verification emails also stop retrying once their token expires, since a link that shows up after that is useless.

The row's id goes to Resend as the idempotency key. If the job crashes after Resend has accepted a message but before the row is marked sent, the next run will send it again, and Resend will recognize the key and drop the copy.

For the provider I picked Resend over SES. My DNS is at Namecheap, so the domain records get added by hand whichever one I use. That leaves SES with one real advantage: it could send using the EC2 instance's IAM role, with no API key to manage. The problem is that new SES accounts start in a sandbox where they can only email addresses I've verified myself, and getting out of it means a manual review by AWS. For a public demo, a visitor whose verification email never shows up is a dealbreaker. So I'm accepting one long-lived API key, kept in Secrets Manager next to the PayPal credentials. Mail goes out from `noreply@ajoledger.xyz`, and the SPF and DKIM records sit on a sending subdomain so they don't clash with anything on the main domain.

## Alternatives considered

The option I came closest to picking was sending after commit: publish an event during the transaction and let an async listener send the email once it commits. It solves the rollback problem with far less code, and for notifications at my volume it would probably be fine. My issue with it is the gap between the commit and the send, where the email only exists in memory. Every deploy restarts the container, a Resend outage would mean the email is simply dropped, and nothing anywhere records that it was supposed to go out. I'd only find out about a missing verification email when someone told me.

Sending synchronously inside the transaction, the way it works now, isn't really an option for the rollback reason above.

SES with the instance role is still a reasonable design, and if DNS ever moves to Route 53 I'd look at it again. At that point the DNS records, the sending identity and the permission could all be in Terraform, with no secret anywhere. The sandbox approval is the only thing stopping me today.

## Consequences

This adds one table and its migration, one more scheduled job, and a new secret that has to be wired through `secrets.tf`, the cloud-init template and `deploy.sh`.

The job assumes there's only one backend instance. On a single instance, scheduled runs don't overlap, so a row can't be picked up twice. If I ever run more than one, the job will need a proper claim step, probably `FOR UPDATE SKIP LOCKED` or a lease column. ADR 0006 has the same caveat about the login lockout counter.

The email job runs on its own scheduler thread. Spring otherwise runs every scheduled job on one shared thread, which would let a Resend outage stall round transitions and reconciliation, and let those hold up a verification email. The other jobs stay together on the shared thread, since the circle jobs have always run one at a time and I don't want to start running them concurrently as a side effect of this.

Emails now go out on the job's schedule instead of instantly. The job has to run every few seconds, or a new user will be sitting there waiting for a verification email that seems like it's never coming.

Verification emails in the outbox contain a working verification link. The same token is already stored in plain text on `user_auth`, so this doesn't expose anything new. Still, sent rows shouldn't hold onto it forever, and I'll clear or delete them after some retention period.
