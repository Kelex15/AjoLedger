import { Check, Copy } from 'lucide-react'
import { useEffect, useState } from 'react'
import type { LedgerActivity } from '../api/transactions'
import { accountLabel, formatDateTime, formatSignedMoney, titleCase } from '../lib/format'
import { ActivityIcon } from './ActivityList'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { cn } from 'cn'

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-baseline justify-between gap-4 py-2.5">
      <dt className="shrink-0 text-muted-foreground">{label}</dt>
      <dd className="text-right font-medium">{value}</dd>
    </div>
  )
}

function Reference({ label, value }: { label: string; value: string }) {
  const [state, setState] = useState<'idle' | 'copied' | 'failed'>('idle')

  async function copy() {
    try {
      await navigator.clipboard.writeText(value)
      setState('copied')
    } catch {
      setState('failed')
    }
  }

  // Reverts on its own so the button doesn't stay stuck on a stale confirmation.
  useEffect(() => {
    if (state === 'idle') {
      return
    }
    const timer = setTimeout(() => setState('idle'), 2000)
    return () => clearTimeout(timer)
  }, [state])

  return (
    <div className="flex flex-col gap-1 py-2.5">
      <dt className="flex items-center gap-1.5 text-muted-foreground">
        {label}
        <span
          aria-live="polite"
          className={cn('text-xs', state === 'failed' ? 'text-destructive' : 'text-success')}
        >
          {state === 'copied' ? 'Copied' : state === 'failed' ? "Couldn't copy" : ''}
        </span>
      </dt>
      <dd className="flex items-start gap-1">
        <code data-numeric className="min-w-0 flex-1 text-xs break-all">
          {value}
        </code>
        <Button type="button" variant="ghost" size="icon-xs" onClick={copy} aria-label={`Copy ${label}`}>
          {state === 'copied' ? <Check className="text-success" /> : <Copy />}
        </Button>
      </dd>
    </div>
  )
}

const CIRCLE_DESCRIPTIONS: Record<string, (circle: string) => string> = {
  CONTRIBUTION: (circle) => `Contribution to ${circle}`,
  CONTRIBUTION_REFUND: (circle) => `Contribution refunded from ${circle}`,
  PAYOUT: (circle) => `Payout from ${circle}`,
  PAYOUT_LIEN_SKIM: (circle) => `Held in lien from your ${circle} payout`,
  LIEN_SEIZURE: (circle) => `Seized to cover your default in ${circle}`,
  SAVINGS_SEIZURE: (circle) => `Seized to cover your default in ${circle}`,
  CHECKING_SEIZURE: (circle) => `Seized to cover your default in ${circle}`,
}

// The stored description carries internal ids, so anything tied to a circle is described by the circle instead.
function describe(entry: LedgerActivity) {
  if (!entry.circleName) {
    return entry.description
  }
  const text =
    CIRCLE_DESCRIPTIONS[entry.transactionType]?.(entry.circleName) ??
    `${titleCase(entry.transactionType)} · ${entry.circleName}`
  return entry.roundNumber == null ? text : `${text}, round ${entry.roundNumber}`
}

export function TransactionDetailDialog({
  entry,
  onOpenChange,
}: {
  entry: LedgerActivity | null
  onOpenChange: (open: boolean) => void
}) {
  const isCredit = entry ? entry.amount >= 0 : false

  return (
    <Dialog open={entry != null} onOpenChange={onOpenChange}>
      <DialogContent>
        {entry && (
          <>
            <DialogHeader>
              <span
                className={cn(
                  'flex size-10 items-center justify-center rounded-full',
                  isCredit ? 'bg-success/12 text-success' : 'bg-muted text-muted-foreground',
                )}
              >
                <ActivityIcon transactionType={entry.transactionType} className="size-5" />
              </span>
              <DialogTitle className="text-xl">{titleCase(entry.transactionType)}</DialogTitle>
              <DialogDescription>{describe(entry)}</DialogDescription>
            </DialogHeader>

            <span
              data-numeric
              className={cn(
                'font-heading text-3xl font-semibold tracking-tight',
                isCredit ? 'text-success' : 'text-foreground',
              )}
            >
              {formatSignedMoney(entry.amount, entry.currency)}
            </span>

            <dl className="flex flex-col divide-y divide-border border-y border-border text-sm">
              <Row label="Account" value={`${accountLabel(entry.accountType)} ···· ${entry.accountNumber.slice(-4)}`} />
              <Row label={isCredit ? 'Credited' : 'Debited'} value={formatDateTime(entry.createdAt)} />
              <Reference label="Reference" value={entry.transactionId} />
            </dl>

            <DialogFooter showCloseButton />
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}
