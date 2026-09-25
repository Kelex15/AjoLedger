import { Lock } from 'lucide-react'
import type { KeyboardEvent, ReactNode } from 'react'
import type { Circle } from '../api/circles'
import { formatMoney } from '../lib/format'
import { StatusBadge } from './StatusBadge'
import { Badge } from '@/components/ui/badge'
import { Progress } from '@/components/ui/progress'
import { cn } from 'cn'

export function CircleCard({
  circle,
  onOpen,
  action,
  className,
}: {
  circle: Circle
  onOpen?: () => void
  action?: ReactNode
  className?: string
}) {
  const started = circle.currentRound > 0

  function handleKeyDown(event: KeyboardEvent) {
    if (onOpen && (event.key === 'Enter' || event.key === ' ')) {
      event.preventDefault()
      onOpen()
    }
  }

  return (
    <div
      {...(onOpen && { role: 'button', tabIndex: 0, onClick: onOpen, onKeyDown: handleKeyDown })}
      className={cn(
        'flex flex-col gap-3 rounded-xl border border-border p-4 text-left transition-colors',
        onOpen &&
          'cursor-pointer hover:border-primary/40 hover:bg-muted/40 focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none',
        className,
      )}
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-1.5">
          <div className="flex flex-wrap items-center gap-1.5">
            <span className="font-heading text-base font-semibold tracking-tight">{circle.name}</span>
            <StatusBadge status={circle.status} />
            {circle.gated && (
              <Badge variant="outline">
                <Lock />
                Invite-only
              </Badge>
            )}
            {circle.minReputationScore != null && (
              <Badge variant="secondary">Min reputation {circle.minReputationScore}</Badge>
            )}
          </div>
          <p className="text-sm text-muted-foreground">{circle.description}</p>
        </div>
        {/* Stops the card's own click from firing when the action is what was pressed. */}
        {action && (
          <div className="shrink-0" onClick={(event) => event.stopPropagation()}>
            {action}
          </div>
        )}
      </div>

      {started && (
        <div className="flex flex-col gap-1.5">
          <div className="flex items-baseline justify-between text-xs">
            <span className="text-muted-foreground">Progress</span>
            <span data-numeric className="font-medium">
              Round {circle.currentRound} of {circle.totalRounds}
            </span>
          </div>
          <Progress value={(circle.currentRound / Math.max(circle.totalRounds, 1)) * 100} className="h-1.5" />
        </div>
      )}

      <div data-numeric className="flex flex-wrap gap-x-3 gap-y-1 text-xs text-muted-foreground">
        <span>{formatMoney(circle.contributionAmount, circle.currency)}/round</span>
        <span aria-hidden>·</span>
        <span>{circle.term}wk term</span>
        <span aria-hidden>·</span>
        <span>{circle.maxMembers} members</span>
        <span aria-hidden>·</span>
        <span>{formatMoney(circle.collateral, circle.currency)} collateral</span>
      </div>
    </div>
  )
}
