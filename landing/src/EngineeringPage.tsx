import type { ReactNode } from 'react'
import { ArrowLeft, ExternalLink } from 'lucide-react'
import { Footer } from './components/Footer'
import { Nav } from './components/Nav'
import { links } from './site'

const adrs = [
  { n: '0001', slug: 'api-versioning-strategy', title: 'API versioning strategy' },
  { n: '0002', slug: 'modular-monolith-architecture', title: 'Modular monolith architecture' },
  { n: '0003', slug: 'java-spring-boot', title: 'Java and Spring Boot' },
  { n: '0004', slug: 'jpa-jdbc-persistence-split', title: 'JPA and JDBC persistence split' },
  { n: '0005', slug: 'react-vite-typescript', title: 'React, Vite, TypeScript' },
  { n: '0006', slug: 'auth-identity-security-model', title: 'Auth and identity security model' },
  { n: '0007', slug: 'ledger-transaction-leg-arity', title: 'Ledger transaction leg arity' },
  { n: '0008', slug: 'tailwind-css', title: 'Tailwind CSS' },
  { n: '0009', slug: 'webhook-idempotency-and-reconciliation', title: 'Webhook idempotency and reconciliation' },
  { n: '0010', slug: 'ledger-hash-chain-audit-trail', title: 'Ledger hash chain audit trail' },
  { n: '0011', slug: 'paypal-orders-integration', title: 'PayPal Orders integration' },
]

function Section({ id, title, children }: { id: string; title: string; children: ReactNode }) {
  return (
    <section id={id} className="border-t border-border">
      <div className="mx-auto max-w-3xl px-4 py-14 sm:px-6">
        <h2 className="font-heading text-2xl font-semibold tracking-tight sm:text-3xl">{title}</h2>
        <div className="mt-5 flex flex-col gap-4 leading-relaxed text-muted-foreground">{children}</div>
      </div>
    </section>
  )
}

function Formula({ children }: { children: string }) {
  return (
    <pre className="overflow-x-auto rounded-lg border border-border bg-muted p-4 text-sm text-foreground">
      <code>{children}</code>
    </pre>
  )
}

export function EngineeringPage() {
  return (
    <>
      <Nav
        sections={[
          { href: '#problem', label: 'The problem' },
          { href: '#model', label: 'The model' },
          { href: '#ledger', label: 'The ledger' },
          { href: '#adrs', label: 'Decisions' },
        ]}
      />

      <main>
        <section className="mx-auto max-w-3xl px-4 py-14 sm:px-6 lg:py-20">
          <a
            href="/"
            className="inline-flex items-center gap-1.5 rounded-md text-sm text-muted-foreground transition-colors hover:text-foreground"
          >
            <ArrowLeft className="size-4" />
            Back to AjoLedger
          </a>

          <h1 className="mt-6 font-heading text-4xl leading-tight font-semibold tracking-tight text-balance sm:text-5xl">
            How it is built
          </h1>

          <p className="mt-5 text-lg leading-relaxed text-muted-foreground">
            The accounting in a rotating savings platform is the easy half. Double-entry bookkeeping has been
            settled for centuries, and I still wrote it by hand so I could account for every guarantee. The
            hard half is that the product only works if strangers can rely on each other, which is a design
            problem before it is a code problem. This page covers both, and links to the decision records
            behind them.
          </p>

          <dl className="mt-10 grid gap-4 sm:grid-cols-3">
            {[
              { k: 'Backend', v: 'Java 21, Spring Boot, Postgres' },
              { k: 'Shape', v: 'Modular monolith, package by feature' },
              { k: 'Deployed', v: 'EC2 via Terraform, Vercel, GitHub Actions' },
            ].map((item) => (
              <div key={item.k} className="rounded-lg border border-border p-4">
                <dt className="text-xs font-semibold tracking-widest text-muted-foreground uppercase">
                  {item.k}
                </dt>
                <dd className="mt-1.5 text-sm">{item.v}</dd>
              </div>
            ))}
          </dl>
        </section>

        <Section id="problem" title="The problem worth solving">
          <p>
            In a rotating savings group, members contribute a fixed amount on a fixed schedule and the full
            pot goes to a different member each round until everyone has been paid once. The structural
            weakness is obvious once you look for it: after a member receives their payout, nothing stops
            them from walking away and leaving everyone else short. Whoever is earliest in the rotation has
            the least remaining incentive to keep paying.
          </p>
          <p>
            That is not a bug introduced by digitizing anything. Traditional groups handle it socially,
            because the members are family, colleagues or neighbours, and the cost of defaulting is not
            financial. A platform meant to work between people who have never met has to replace that with
            something structural.
          </p>

          <h3 className="mt-4 font-heading text-lg font-semibold text-foreground">
            Why ordering alone does not fix it
          </h3>
          <p>
            My first instinct was to schedule unproven members late in the rotation, so that by the time they
            are paid they have already demonstrated reliability. That helps in a large group, but it breaks
            down at the minimum size. In a two-person circle somebody has to go first, and there is no
            sequence to arrange around it. Ordering reduces exposure; it cannot eliminate first-mover risk.
          </p>

          <h3 className="mt-4 font-heading text-lg font-semibold text-foreground">
            Why a trust score alone does not either
          </h3>
          <p>
            The obvious alternative is a reputation number that grows with completed circles and gates access
            to early payout slots. I ruled that out because a score with nothing behind it cannot absorb a
            loss. If a member with an excellent score defaults, the group is still short the money and there
            is nothing to claw back. Reputation has to be funded to mean anything financially, which is what
            pushed the design toward collateral that reputation gradually replaces.
          </p>
        </Section>

        <Section id="model" title="Accounts, and one deliberate unification">
          <p>
            Every member holds three accounts. <code className="text-foreground">CHECKING</code> and{' '}
            <code className="text-foreground">SAVINGS</code> are ordinary balances.{' '}
            <code className="text-foreground">LIEN</code> is the interesting one. Each circle owns a{' '}
            <code className="text-foreground">POT</code> and a <code className="text-foreground">RESERVE</code>
            , and the platform holds a <code className="text-foreground">SYSTEM_BUFFER</code> plus a clearing
            account per payment processor.
          </p>
          <p>
            An earlier version of the model treated a member&rsquo;s personal reserve, built from withheld
            payouts, as separate from collateral posted up front for an early position. Writing it out made
            clear they are the same money doing the same job at different times, so they became one account.
            A member&rsquo;s <code className="text-foreground">LIEN</code> balance grows every time they are
            paid, carries between circles, and offsets whatever collateral their next position would
            otherwise require. That is what makes reputation and collateral converge instead of compete.
          </p>

          <h3 className="mt-4 font-heading text-lg font-semibold text-foreground">Sizing the exposure</h3>
          <p>
            A member paid in position P of N rounds still owes a contribution for each of the N minus P rounds
            that follow their own payout. That figure is exactly what the group loses if they disappear right
            after being paid, so it is what collateral is sized against.
          </p>
          <Formula>{`potential group loss = contribution * (total rounds - payout position)
required collateral  = max(0, potential group loss - banked LIEN balance)`}</Formula>
          <p>
            The last position needs no collateral at all, since by the time they are paid every round
            including their own has already been funded. A brand new member taking an early slot either posts
            real cash or takes a late one. An established member with enough banked{' '}
            <code className="text-foreground">LIEN</code> takes an early slot with no new cash.
          </p>

          <h3 className="mt-4 font-heading text-lg font-semibold text-foreground">Who gets paid first</h3>
          <p>
            Positions are assigned when the circle locks rather than as people join, so every candidate is
            scored on equal footing. Higher reputation earns the earlier and more valuable slot, which lines
            up with the collateral logic, since a member with a strong record plausibly already has the{' '}
            <code className="text-foreground">LIEN</code> balance to cover it.
          </p>
          <Formula>{`successRate       = (successes + prior) / (successes + prior + penalizedFailures + prior)
lienCoverageRatio = min(1, LIEN / (contribution * (total rounds - 1)))
reputationScore   = w * successRate + (1 - w) * lienCoverageRatio`}</Formula>
          <p>
            A default counts three times as heavily as a withdrawal, because a default costs the group real
            money and a withdrawal does not. The Laplace prior keeps a member with no history at a neutral
            0.5, so one data point cannot swing them to either extreme. The coverage denominator is
            the worst-case exposure from the collateral formula above, reused deliberately instead of
            inventing a second measure of the same thing.
          </p>

          <h3 className="mt-4 font-heading text-lg font-semibold text-foreground">
            Withholding, and a floor that closes a loophole
          </h3>
          <p>
            The slice withheld from a payout is sized to what the recipient still needs, not charged at a
            flat rate, and it is capped so nobody loses most of their pot to it.
          </p>
          <Formula>{`neededTopUp      = max(0, potential group loss - current LIEN)
lienShare        = min(neededTopUp, gross payout * 0.30)
withdrawal floor = contribution * (total rounds - current round + 1)`}</Formula>
          <p>
            That floor applies to every active member in a locked circle, paid or not, and the reason is a
            gaming path I had to close.{' '}
            <code className="text-foreground">LIEN</code> is what justifies an early position at the moment a
            circle locks. If the floor only kicked in after payout, a member could top up right before lock to
            win an early slot, withdraw it all before ever being paid, and leave the group backed by nothing.
            The floor has to hold from the moment the balance is used to claim standing, not from the moment
            risk is realised.
          </p>
        </Section>

        <Section id="waterfall" title="When a member stops paying">
          <p>
            Two different things can go wrong here, and they are not variations of one rule. Which one applies
            turns on whether the member has already taken their payout, because only one of the two actually
            leaves the group short. Treating them as the same event was the mistake I most wanted to avoid.
          </p>

          <h3 className="mt-4 font-heading text-lg font-semibold text-foreground">
            A default, after the payout
          </h3>
          <p>
            This is the real loss. The member has taken the pot and owes a contribution for every round that
            follows. Losses are absorbed in a fixed order, and the last step is the one I spent longest on.
          </p>
          <ol className="flex list-decimal flex-col gap-3 pl-5 marker:text-primary marker:tabular-nums">
            <li>
              The member&rsquo;s own accounts are drawn down, in order:{' '}
              <code className="text-foreground">LIEN</code> first, since that is the money posted for exactly
              this purpose, then <code className="text-foreground">SAVINGS</code>, then{' '}
              <code className="text-foreground">CHECKING</code>. Each gives up what it holds until either the
              group loss is covered or that member&rsquo;s seizure ceiling runs out, whichever binds first,
              and each posts its own ledger entry crediting the circle&rsquo;s{' '}
              <code className="text-foreground">RESERVE</code>.
            </li>
            <li>
              The shared buffer covers what that did not, capped at a percentage of the buffer&rsquo;s own
              current balance. One large or unlucky default should not be able to take the whole pool, which
              is the scenario where correlated defaults across the platform hurt everyone at once.
            </li>
            <li>
              Whatever remains spreads passively across the circle&rsquo;s remaining rounds, so each future
              payout is proportionally smaller for the rest of its life.
            </li>
          </ol>
          <p>
            Step three is never a forced transfer. Nothing is debited from a remaining member&rsquo;s account
            to cover a shortfall, because a member who cannot afford a surprise assessment is precisely the
            member who defaults next, and that correlated failure is the thing the whole model exists to
            avoid.
          </p>
          <p>
            What steps one and two recover does not land in the pot directly either. It is credited to the
            circle&rsquo;s <code className="text-foreground">RESERVE</code> and released in an even share
            across each remaining round. The loss belonged to the group, so its recovery belongs to the group,
            not to whichever member&rsquo;s payout happens to fall next.
          </p>
          <p>
            One more guard sits underneath all of it. Every locked membership carries a persisted seizure
            ceiling, and it is a single budget covering all three accounts, decremented once by the total
            taken, so the order they are visited in cannot be used to extract more in aggregate. It is checked
            at the moment of seizure and never recomputed from the live formula in that same code path, with a
            database <code className="text-foreground">CHECK</code> constraint backing it. A future bug in the
            group-loss calculation can therefore under-seize, but it can never take more from a member than
            was ever legitimately owed.
          </p>

          <h3 className="mt-6 font-heading text-lg font-semibold text-foreground">
            A withdrawal, before the payout
          </h3>
          <p>
            Missing a contribution before you have been paid is not a default, and the waterfall above does
            not apply to it. There is no group loss to seize collateral against, because the member has taken
            nothing out of the pot yet. They are withdrawn instead. Their position drops out of the rotation
            and every position behind it compacts down by one.
          </p>
          <p>
            They have still lost something real, though. Contributions they made in earlier rounds already
            funded somebody else&rsquo;s payout, and at the time nothing comes back. Settlement happens once,
            when the circle completes: whatever is left in{' '}
            <code className="text-foreground">RESERVE</code> sweeps into the{' '}
            <code className="text-foreground">POT</code>, the shared buffer takes its usual cut, and the
            remainder splits across every withdrawn member weighted by how many rounds each of them actually
            contributed. Two people who drop out at different points get shares proportional to what they each
            put in, not an equal split that ignores it.
          </p>
          <p>
            This deliberately stops short of making a withdrawn member whole. An early contribution that has
            already funded someone else&rsquo;s payout is simply gone. Settlement only hands back what the
            circle&rsquo;s own accounts still hold, and it never draws on the shared buffer, since that pool
            is sized for defaults happening elsewhere on the platform.
          </p>
        </Section>

        <Section id="ledger" title="The ledger underneath">
          <p>
            State and financial history are kept deliberately separate. Circle configuration, memberships and
            profiles are ordinary CRUD handled by Spring Data JPA. Anything that moves money is hand-written
            SQL through <code className="text-foreground">JdbcTemplate</code>, so I can point at exactly where
            atomicity and concurrency safety come from instead of asserting that an annotation takes care of
            it.
          </p>
          <ul className="flex list-disc flex-col gap-2.5 pl-5 marker:text-primary">
            <li>
              True double-entry. Every transaction&rsquo;s legs net to zero, and that is enforced at the
              schema level, so application code cannot quietly violate it.
            </li>
            <li>
              Append-only. Corrections happen through new offsetting entries, never edits or deletes, and a
              database trigger enforces it so no code path can get around it.
            </li>
            <li>
              Each operation is sealed into an asynchronous hash chain, which makes tampering with history
              detectable after the fact.
            </li>
            <li>
              Every money-moving operation carries an idempotency key, so a retried request cannot
              double-process. The same discipline covers inbound payment webhooks, which arrive more than
              once by design.
            </li>
            <li>
              Tests run against real Postgres through Testcontainers, with property-based tests over the
              ledger and waterfall invariants. Concurrent writes to the same account get the most attention,
              since that is where a plausible-looking implementation actually breaks.
            </li>
          </ul>
        </Section>

        <Section id="stack" title="Stack and infrastructure">
          <p>
            The backend is a modular monolith organised by feature, not by layer, so each domain owns
            its own controller, service, repository and DTOs. API versioning is header-based, resolved from{' '}
            <code className="text-foreground">X-API-Version</code> with a default so unaware clients keep
            working. Money is <code className="text-foreground">BigDecimal</code> everywhere, never a float.
          </p>
          <p>
            Authentication uses httpOnly access and refresh cookies with rotation and revocation, credentials
            split into their own entity so they can never serialize into a response, mandatory email
            verification, login rate limiting, and Google OAuth restricted to linking an already
            authenticated account rather than silently merging on a matching email address.
          </p>
          <p>
            It runs on an EC2 instance provisioned by Terraform, with RDS for Postgres. Images are built in
            GitHub Actions and pushed to ECR, and CI authenticates to AWS through OIDC roles so there are no
            long-lived access keys anywhere. The frontend and this page are on Vercel. Payment integrations
            are sandbox only and will stay that way, because operating them for real means money transmission
            licensing.
          </p>
        </Section>

        <Section id="adrs" title="Decision records">
          <p>
            Every non-trivial architectural choice has a record explaining the context, what I picked, what I
            turned down, and what it cost. The trust model has its own versioned design document, where each
            rule change forks a new file, so the reasoning stays honest about how the model evolved instead
            of reading like it arrived fully formed.
          </p>
          <ul className="mt-2 grid gap-2 sm:grid-cols-2">
            {adrs.map((adr) => (
              <li key={adr.n}>
                <a
                  href={`${links.adrs}/${adr.n}-${adr.slug}.md`}
                  className="flex items-center gap-2 rounded-lg border border-border px-4 py-3 text-sm text-foreground transition-colors hover:bg-accent hover:text-accent-foreground"
                >
                  <span data-numeric className="text-muted-foreground">
                    {adr.n}
                  </span>
                  <span>{adr.title}</span>
                </a>
              </li>
            ))}
          </ul>
          <div className="mt-4 flex flex-wrap gap-4 text-sm">
            <a href={links.designDoc} className="inline-flex items-center gap-1.5 text-primary hover:underline">
              The trust and risk model in full
              <ExternalLink className="size-3.5" />
            </a>
            <a href={links.repo} className="inline-flex items-center gap-1.5 text-primary hover:underline">
              Source on GitHub
              <ExternalLink className="size-3.5" />
            </a>
          </div>
        </Section>
      </main>

      <Footer />
    </>
  )
}
