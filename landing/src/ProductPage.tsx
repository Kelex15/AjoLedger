import { ArrowRight, Landmark, PiggyBank, Repeat, ShieldCheck, Users } from 'lucide-react'
import { ButtonLink } from './components/Button'
import { Footer } from './components/Footer'
import { Nav } from './components/Nav'
import { RotationDiagram } from './components/RotationDiagram'
import { links } from './site'

const steps = [
  {
    icon: Users,
    title: 'Join a circle',
    body: 'Pick a circle and fund your first contribution. A tenth of that amount is held in your name as collateral, half that in invite-only circles. It is locked rather than spent, and it comes back to you.',
  },
  {
    icon: Repeat,
    title: 'Contribute each round',
    body: 'Everyone pays the same amount on the same schedule. Each round the full pot goes to one member, so a six-person circle paying $100 a round pays out $600 six times over.',
  },
  {
    icon: PiggyBank,
    title: 'Take your turn',
    body: 'Your position is fixed when the circle locks, ordered by track record instead of who signed up first. Part of your payout is withheld into a reserve that stays yours, which is what earns you an earlier turn next time.',
  },
]

const layers = [
  {
    label: 'First',
    title: 'Their own reserve',
    body: 'Every payout withholds a slice into the recipient’s own locked reserve, right away rather than at the end. By the time someone could walk away, money of theirs is already sitting there to cover it.',
  },
  {
    label: 'Second',
    title: 'A shared safety net',
    body: 'A small fee taken at payout funds a pool shared across every circle on the platform. It covers what one member’s reserve cannot, and it can only give up a fraction of itself to any single default, so one bad event cannot drain it for everyone else.',
  },
  {
    label: 'Last',
    title: 'Slightly smaller payouts',
    body: 'Anything still uncovered is spread evenly across the circle’s remaining rounds, so each payout after that is a little smaller. The loss gets shared a bit at a time instead of landing on one person.',
  },
]

export function ProductPage() {
  return (
    <>
      <Nav
        sections={[
          { href: '#how-it-works', label: 'How it works' },
          { href: '#protection', label: 'If someone stops paying' },
          { href: '/engineering', label: 'How it is built' },
        ]}
      />

      <main>
        <section className="mx-auto grid max-w-6xl items-center gap-12 px-4 py-16 sm:px-6 lg:grid-cols-2 lg:gap-16 lg:py-24">
          <div className="flex flex-col items-start gap-6">
            <span className="inline-flex items-center gap-2 rounded-full border border-border bg-muted px-3 py-1 text-xs font-medium text-muted-foreground">
              <Landmark className="size-3.5" />
              Ajo, esusu, susu, tandas
            </span>

            <h1 className="font-heading text-4xl leading-[1.1] font-semibold tracking-tight text-balance sm:text-5xl lg:text-6xl">
              Save together, take turns.
            </h1>

            <p className="max-w-xl text-lg leading-relaxed text-muted-foreground">
              A group contributes the same amount on the same schedule, and each round the whole pot goes to
              one member until everyone has been paid. It is how people have saved together for generations.
              AjoLedger puts real collateral behind it, so it also works between people who have never met.
            </p>

            <div className="flex flex-wrap items-center gap-3">
              <ButtonLink href={links.signUp} size="lg">
                Create an account
                <ArrowRight className="size-4" />
              </ButtonLink>
              <ButtonLink href="#how-it-works" variant="outline" size="lg">
                See how it works
              </ButtonLink>
            </div>
          </div>

          <div className="mx-auto w-full max-w-md lg:max-w-none">
            <RotationDiagram />
            <p className="mt-4 text-center text-sm text-muted-foreground">
              Six members at $100 a round. Position three is being paid; everyone else is paying in.
            </p>
          </div>
        </section>

        <section id="how-it-works" className="border-t border-border bg-muted/40">
          <div className="mx-auto max-w-6xl px-4 py-16 sm:px-6 lg:py-20">
            <h2 className="font-heading text-3xl font-semibold tracking-tight sm:text-4xl">
              How a circle works
            </h2>
            <p className="mt-3 max-w-2xl text-muted-foreground">
              Three things happen, in order, and the rules are the same for everyone in the circle.
            </p>

            <ol className="mt-10 grid gap-6 md:grid-cols-3">
              {steps.map((step, index) => (
                <li
                  key={step.title}
                  className="flex flex-col gap-4 rounded-xl border border-border bg-card p-6 text-card-foreground"
                >
                  <div className="flex items-center gap-3">
                    <span className="flex size-10 items-center justify-center rounded-lg bg-primary/10 text-primary">
                      <step.icon className="size-5" />
                    </span>
                    <span data-numeric className="text-sm font-medium text-muted-foreground">
                      Step {index + 1}
                    </span>
                  </div>
                  <h3 className="font-heading text-xl font-semibold tracking-tight">{step.title}</h3>
                  <p className="text-sm leading-relaxed text-muted-foreground">{step.body}</p>
                </li>
              ))}
            </ol>
          </div>
        </section>

        <section id="protection" className="border-t border-border">
          <div className="mx-auto max-w-6xl px-4 py-16 sm:px-6 lg:py-20">
            <div className="max-w-2xl">
              <h2 className="font-heading text-3xl font-semibold tracking-tight sm:text-4xl">
                What happens if someone stops paying
              </h2>
              <p className="mt-3 text-muted-foreground">
                This is the weak point of every rotating savings group. Once a member has taken the pot, the
                only thing keeping them contributing is that they said they would. A circle between friends
                relies on the friendship. AjoLedger replaces that with three layers that absorb a loss in a
                fixed order.
              </p>
            </div>

            <ol className="mt-10 grid gap-6 md:grid-cols-3">
              {layers.map((layer) => (
                <li key={layer.title} className="flex flex-col gap-3 rounded-xl border border-border p-6">
                  <span className="text-xs font-semibold tracking-widest text-primary uppercase">
                    {layer.label}
                  </span>
                  <h3 className="font-heading text-xl font-semibold tracking-tight">{layer.title}</h3>
                  <p className="text-sm leading-relaxed text-muted-foreground">{layer.body}</p>
                </li>
              ))}
            </ol>
          </div>
        </section>

        <section className="border-t border-border bg-muted/40">
          <div className="mx-auto grid max-w-6xl gap-10 px-4 py-16 sm:px-6 lg:grid-cols-[1fr_1.1fr] lg:py-20">
            <div>
              <h2 className="font-heading text-3xl font-semibold tracking-tight text-balance sm:text-4xl">
                The longer you stay, the less it costs to start
              </h2>
              <p className="mt-3 text-muted-foreground">
                Collateral and reputation are usually two competing systems. Here they are the same balance,
                so proving yourself and putting money down converge over time.
              </p>
            </div>

            <dl className="flex flex-col divide-y divide-border">
              <div className="flex flex-col gap-1.5 pb-5">
                <dt className="font-medium">A first circle costs nothing up front</dt>
                <dd className="text-sm leading-relaxed text-muted-foreground">
                  Take the last position and there is no collateral to post at all. By the time you are paid,
                  every round including your own has already been funded, so the group carries no risk from
                  you.
                </dd>
              </div>
              <div className="flex flex-col gap-1.5 py-5">
                <dt className="font-medium">Each payout builds a reserve that is yours</dt>
                <dd className="text-sm leading-relaxed text-muted-foreground">
                  Withheld money is not a fee. It sits in your own account, it counts toward the collateral an
                  earlier position would otherwise require, and whatever exceeds what your current circle
                  still needs can be withdrawn now.
                </dd>
              </div>
              <div className="flex flex-col gap-1.5 pt-5">
                <dt className="font-medium">Your record follows you</dt>
                <dd className="text-sm leading-relaxed text-muted-foreground">
                  Reserve and history carry from one circle into the next. Members with a long record reach
                  the point where an early position costs no new cash at all.
                </dd>
              </div>
            </dl>
          </div>
        </section>

        <section className="border-t border-border">
          <div className="mx-auto max-w-6xl px-4 py-14 sm:px-6">
            <div className="flex flex-col gap-4 rounded-xl border border-border bg-card p-6 sm:flex-row sm:items-start sm:gap-5">
              <ShieldCheck className="size-6 shrink-0 text-primary" />
              <div className="flex flex-col gap-2">
                <h2 className="font-heading text-lg font-semibold tracking-tight">
                  No real money moves here
                </h2>
                <p className="text-sm leading-relaxed text-muted-foreground">
                  AjoLedger isn&rsquo;t licensed to move money. Deposits run against
                  Stripe and PayPal sandbox credentials, so balances are real inside the system but no funds
                  ever leave or enter a bank. That limit is deliberate and permanent. The circles, the
                  double-entry ledger and the risk model all work exactly as described; only the payment rails
                  are test mode.
                </p>
              </div>
            </div>
          </div>
        </section>

        <section className="border-t border-border bg-muted/40">
          <div className="mx-auto flex max-w-6xl flex-col items-start gap-6 px-4 py-16 sm:px-6 lg:flex-row lg:items-center lg:justify-between lg:py-20">
            <div>
              <h2 className="font-heading text-3xl font-semibold tracking-tight">Start a circle</h2>
              <p className="mt-2 text-muted-foreground">
                Create an account, or read how the whole thing is built underneath.
              </p>
            </div>
            <div className="flex flex-wrap gap-3">
              <ButtonLink href={links.signUp} size="lg">
                Create an account
                <ArrowRight className="size-4" />
              </ButtonLink>
              <ButtonLink href="/engineering" variant="outline" size="lg">
                How it is built
              </ButtonLink>
            </div>
          </div>
        </section>
      </main>

      <Footer />
    </>
  )
}
