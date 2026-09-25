import {
  ArrowDownLeft,
  ArrowLeftRight,
  ArrowUpRight,
  HandCoins,
  Lock,
  ShieldAlert,
  Users,
  type LucideIcon,
} from 'lucide-react'
import { useState } from 'react'
import type { LedgerActivity } from '../api/transactions'
import { accountLabel, formatDayHeading, formatSignedMoney, formatTime, titleCase } from '../lib/format'
import { TransactionDetailDialog } from './TransactionDetailDialog'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from 'cn'

const ICONS: Record<string, LucideIcon> = {
  DEPOSIT: ArrowDownLeft,
  WITHDRAWAL: ArrowUpRight,
  TRANSFER: ArrowLeftRight,
  CONTRIBUTION: Users,
  CONTRIBUTION_REFUND: Users,
  PAYOUT: HandCoins,
  PAYOUT_BUFFER_FEE: HandCoins,
  PAYOUT_LIEN_SKIM: HandCoins,
  LIEN_HOLD: Lock,
  LIEN_WITHDRAWAL: Lock,
  LIEN_SEIZURE: ShieldAlert,
  SAVINGS_SEIZURE: ShieldAlert,
  CHECKING_SEIZURE: ShieldAlert,
  BUFFER_PAYOUT_TO_COVER_DEFAULT: ShieldAlert,
  CIRCLE_RESERVE_DISBURSEMENT: HandCoins,
}

export function ActivityIcon({ transactionType, className }: { transactionType: string; className?: string }) {
  const Icon = ICONS[transactionType] ?? ArrowLeftRight
  return <Icon className={className} />
}

function ActivityRow({ entry, onSelect }: { entry: LedgerActivity; onSelect: () => void }) {
  const isCredit = entry.amount >= 0

  return (
    <li>
      <button
        type="button"
        onClick={onSelect}
        className="flex w-full items-center gap-3 rounded-lg px-2 py-3 text-left transition-colors hover:bg-muted/60 focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
      >
        <span
          className={cn(
            'flex size-9 shrink-0 items-center justify-center rounded-full',
            isCredit ? 'bg-success/12 text-success' : 'bg-muted text-muted-foreground',
          )}
        >
          <ActivityIcon transactionType={entry.transactionType} className="size-4" />
        </span>
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-medium">{titleCase(entry.transactionType)}</p>
          <p className="truncate text-xs text-muted-foreground">
            {accountLabel(entry.accountType)} · {formatTime(entry.createdAt)}
          </p>
        </div>
        <span
          data-numeric
          className={cn('shrink-0 text-sm font-medium', isCredit ? 'text-success' : 'text-foreground')}
        >
          {formatSignedMoney(entry.amount, entry.currency)}
        </span>
      </button>
    </li>
  )
}

/** Day headings only earn their place on the full statement; the dashboard preview is short
 * enough to read without them. */
export function ActivityList({ entries, grouped = false }: { entries: LedgerActivity[]; grouped?: boolean }) {
  const [selected, setSelected] = useState<LedgerActivity | null>(null)

  const days: { heading: string; entries: LedgerActivity[] }[] = []
  if (grouped) {
    for (const entry of entries) {
      const heading = formatDayHeading(entry.createdAt)
      if (days.at(-1)?.heading !== heading) {
        days.push({ heading, entries: [] })
      }
      days.at(-1)!.entries.push(entry)
    }
  }

  return (
    <>
      {grouped ? (
        <div className="flex flex-col gap-6">
          {days.map((day) => (
            <section key={day.heading} className="flex flex-col gap-1">
              <h2 className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">{day.heading}</h2>
              <ul className="-mx-2 divide-y divide-border">
                {day.entries.map((entry) => (
                  <ActivityRow key={entry.entryId} entry={entry} onSelect={() => setSelected(entry)} />
                ))}
              </ul>
            </section>
          ))}
        </div>
      ) : (
        <ul className="-mx-2 divide-y divide-border">
          {entries.map((entry) => (
            <ActivityRow key={entry.entryId} entry={entry} onSelect={() => setSelected(entry)} />
          ))}
        </ul>
      )}

      <TransactionDetailDialog entry={selected} onOpenChange={(open) => !open && setSelected(null)} />
    </>
  )
}

export function ActivitySkeleton({ rows = 5 }: { rows?: number }) {
  return (
    <ul className="divide-y divide-border">
      {Array.from({ length: rows }, (_, index) => (
        <li key={index} className="flex items-center gap-3 py-3">
          <Skeleton className="size-9 shrink-0 rounded-full" />
          <div className="flex flex-1 flex-col gap-1.5">
            <Skeleton className="h-3.5 w-32" />
            <Skeleton className="h-3 w-20" />
          </div>
          <Skeleton className="h-3.5 w-16" />
        </li>
      ))}
    </ul>
  )
}
