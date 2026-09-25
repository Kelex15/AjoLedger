import type { ReactNode } from 'react'
import { Logo, LogoMark } from './Logo'

export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="grid min-h-screen bg-background text-foreground lg:grid-cols-2">
      <aside className="relative hidden flex-col justify-between overflow-hidden bg-sidebar p-10 lg:flex">
        <Logo className="relative z-10 text-sidebar-foreground" />

        <div className="relative z-10 flex max-w-sm flex-col gap-4">
          <h2 className="font-heading text-3xl leading-tight font-semibold tracking-tight">
            Saving together, on a ledger you can check.
          </h2>
          <p className="text-sm text-muted-foreground">
            Rotating savings circles with the accounting made explicit: every contribution, payout and
            hold is a double-entry transaction you can trace.
          </p>
        </div>

        <LogoMark className="absolute -right-24 -bottom-28 size-[26rem] text-foreground/[0.04]" />
      </aside>

      <main className="flex items-center justify-center p-6">
        <div className="flex w-full max-w-sm flex-col gap-6">
          <Logo className="lg:hidden" />
          {children}
        </div>
      </main>
    </div>
  )
}
