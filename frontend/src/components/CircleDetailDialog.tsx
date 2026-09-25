import { useQuery } from '@tanstack/react-query'
import { Crown, HandCoins, Lock } from 'lucide-react'
import type { ReactNode } from 'react'
import { fetchCircle, fetchCircleMembers, fetchCirclePayouts } from '../api/circles'
import { queryKeys } from '../api/queryKeys'
import { useAuth } from '../auth/useAuth'
import { formatDateTime, formatMoney } from '../lib/format'
import { StatusBadge } from './StatusBadge'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Progress } from '@/components/ui/progress'
import { Skeleton } from '@/components/ui/skeleton'

function Row({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-4 py-2">
      <dt className="text-muted-foreground">{label}</dt>
      <dd data-numeric className="text-right font-medium">
        {value}
      </dd>
    </div>
  )
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-2">
      <h3 className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">{title}</h3>
      {children}
    </div>
  )
}

export function CircleDetailDialog({
  circleId,
  onOpenChange,
}: {
  circleId: string | null
  onOpenChange: (open: boolean) => void
}) {
  return (
    <Dialog open={circleId != null} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">{circleId && <CircleDetail circleId={circleId} />}</DialogContent>
    </Dialog>
  )
}

function CircleDetail({ circleId }: { circleId: string }) {
  const { user } = useAuth()

  const { data: circle } = useQuery({
    queryKey: queryKeys.circle(circleId),
    queryFn: () => fetchCircle(circleId),
  })
  const { data: members } = useQuery({
    queryKey: queryKeys.circleMembers(circleId),
    queryFn: () => fetchCircleMembers(circleId),
  })
  const { data: payouts } = useQuery({
    queryKey: queryKeys.circlePayouts(circleId),
    queryFn: () => fetchCirclePayouts(circleId),
  })

  if (!circle) {
    return (
      <>
        <DialogHeader>
          <DialogTitle className="text-xl">Circle</DialogTitle>
          <DialogDescription>Loading…</DialogDescription>
        </DialogHeader>
        <Skeleton className="h-48 w-full" />
      </>
    )
  }

  const paidOut = payouts?.reduce((sum, payout) => sum + (payout.amount ?? 0), 0) ?? 0

  return (
    <>
      <DialogHeader>
        <DialogTitle className="text-xl">{circle.name}</DialogTitle>
        <DialogDescription>{circle.description}</DialogDescription>
        <div className="flex flex-wrap items-center gap-1.5 pt-1">
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
      </DialogHeader>

      {circle.currentRound > 0 && (
        <div className="flex flex-col gap-1.5">
          <div className="flex items-baseline justify-between text-xs">
            <span className="text-muted-foreground">Progress</span>
            <span data-numeric className="font-medium">
              Round {circle.currentRound} of {circle.totalRounds}
            </span>
          </div>
          <Progress value={(circle.currentRound / Math.max(circle.totalRounds, 1)) * 100} className="h-2" />
        </div>
      )}

      <dl className="flex flex-col divide-y divide-border border-y border-border text-sm">
        <Row label="Contribution per round" value={formatMoney(circle.contributionAmount, circle.currency)} />
        <Row label="Collateral held" value={formatMoney(circle.collateral, circle.currency)} />
        <Row label="Term" value={`${circle.term} week${circle.term === 1 ? '' : 's'}`} />
        <Row label="Members" value={circle.maxMembers} />
        {paidOut > 0 && <Row label="Paid out so far" value={formatMoney(paidOut, circle.currency)} />}
        <Row label="Started" value={formatDateTime(circle.createdAt)} />
      </dl>

      <Section title="Payouts">
        {!payouts && <Skeleton className="h-16 w-full" />}
        {payouts?.length === 0 && (
          <p className="text-sm text-muted-foreground">No rounds have paid out yet.</p>
        )}
        {payouts && payouts.length > 0 && (
          <ul className="flex flex-col divide-y divide-border">
            {payouts.map((payout) => (
              <li key={payout.id} className="flex items-center gap-2.5 py-2">
                <span className="flex size-7 shrink-0 items-center justify-center rounded-full bg-success/12 text-success">
                  <HandCoins className="size-3.5" />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm">
                    {payout.recipientUserId === user?.id ? 'You' : payout.recipientUsername}
                  </p>
                  <p data-numeric className="text-xs text-muted-foreground">
                    Round {payout.roundNumber} · {formatDateTime(payout.paidAt)}
                  </p>
                </div>
                <span data-numeric className="shrink-0 text-sm font-medium text-success">
                  {payout.amount == null ? 'Pending' : formatMoney(payout.amount, payout.currency)}
                </span>
              </li>
            ))}
          </ul>
        )}
      </Section>

      <Section title="Payout order">
        {!members && <Skeleton className="h-24 w-full" />}
        {members?.length === 0 && <p className="text-sm text-muted-foreground">No members yet.</p>}
        {members && members.length > 0 && (
          <ul className="flex flex-col">
            {members.map((member) => (
              <li key={member.userId} className="flex items-center gap-2.5 py-2">
                <span
                  data-numeric
                  className="flex size-6 shrink-0 items-center justify-center rounded-full bg-muted text-xs font-semibold text-muted-foreground"
                >
                  {member.payoutPosition}
                </span>
                <Avatar className="size-7 shrink-0">
                  <AvatarFallback className="bg-primary/10 text-[0.65rem] font-semibold text-primary">
                    {member.username.slice(0, 2).toUpperCase()}
                  </AvatarFallback>
                </Avatar>
                <span className="min-w-0 flex-1 truncate text-sm">
                  {member.userId === user?.id ? 'You' : member.username}
                </span>
                {member.userId === circle.createdByUserId && (
                  <Badge variant="outline">
                    <Crown />
                    Creator
                  </Badge>
                )}
                {member.paid && (
                  <Badge variant="secondary" className="text-success">
                    Paid
                  </Badge>
                )}
                <span data-numeric className="shrink-0 text-xs text-muted-foreground">
                  {member.reputationScore.toFixed(2)}
                </span>
              </li>
            ))}
          </ul>
        )}
      </Section>

      <DialogFooter showCloseButton />
    </>
  )
}
