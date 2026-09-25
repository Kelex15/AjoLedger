import { useEffect, useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  ArrowDownLeft,
  ArrowRight,
  ArrowUpRight,
  ChevronRight,
  CircleCheck,
  Info,
  ReceiptText,
  Users,
  X,
} from 'lucide-react'
import { Link, useSearchParams } from 'react-router'
import { fetchMyAccounts } from '../api/accounts'
import { ApiError } from '../api/client'
import { fetchMyCircle } from '../api/circles'
import { settlePayPalOrder, verifyStripeCheckoutSession } from '../api/funding'
import { queryKeys } from '../api/queryKeys'
import { fetchActivity } from '../api/transactions'
import { useAuth } from '../auth/useAuth'
import { AccountIcon } from '../components/AccountIcon'
import { ActivityList, ActivitySkeleton } from '../components/ActivityList'
import { CircleCard } from '../components/CircleCard'
import { CircleDetailDialog } from '../components/CircleDetailDialog'
import { EmptyState } from '../components/EmptyState'
import { PageHeader } from '../components/PageHeader'
import { FundingDialog } from '../components/dashboard/FundingDialog'
import { accountLabel, formatMoney } from '../lib/format'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'

const RECENT_ACTIVITY_COUNT = 5

export function DashboardPage() {
  const { user } = useAuth()

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title={`Welcome back, ${user?.firstName ?? ''}`} />
      <CheckoutReturnBanner />
      <BalanceSummary />
      <AccountList />
      <div className="grid items-start gap-6 lg:grid-cols-2">
        <CircleSnapshot />
        <RecentActivity />
      </div>
    </div>
  )
}

/** Landed back here from a processor's hosted checkout. If the webhook already posted the deposit
 * this is a no-op; if it hasn't (or never will), this is what actually posts it. */
function CheckoutReturnBanner() {
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  // Clearing the params is a state update, so StrictMode's second invoke still sees them.
  const handled = useRef(false)
  const [banner, setBanner] = useState<'success' | 'cancelled' | null>(() => {
    const status = searchParams.get('depositStatus')
    return status === 'success' || status === 'cancelled' ? status : null
  })

  useEffect(() => {
    const depositStatus = searchParams.get('depositStatus')
    if (depositStatus !== 'success' && depositStatus !== 'cancelled') {
      return
    }
    if (handled.current) {
      return
    }
    handled.current = true

    const sessionId = searchParams.get('session_id')
    // PayPal names the returned order id 'token'.
    const paypalOrderId = searchParams.get('provider') === 'paypal' ? searchParams.get('token') : null
    const refresh = () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.accounts })
      queryClient.invalidateQueries({ queryKey: queryKeys.activity })
    }

    if (depositStatus === 'success') {
      if (sessionId) {
        verifyStripeCheckoutSession(sessionId).catch(() => null).finally(refresh)
      } else if (paypalOrderId) {
        settlePayPalOrder(paypalOrderId).catch(() => null).finally(refresh)
      } else {
        refresh()
      }
    }

    setSearchParams((params) => {
      params.delete('depositStatus')
      params.delete('session_id')
      params.delete('provider')
      params.delete('token')
      params.delete('PayerID')
      return params
    }, { replace: true })
  }, [searchParams, queryClient, setSearchParams])

  if (!banner) {
    return null
  }

  return (
    <Alert variant={banner === 'success' ? 'success' : 'info'}>
      {banner === 'success' ? <CircleCheck /> : <Info />}
      <AlertDescription>
        {banner === 'success'
          ? 'Payment received. Your balance will update shortly.'
          : 'Checkout cancelled, no funds were moved.'}
      </AlertDescription>
      <Button
        variant="ghost"
        size="icon-xs"
        aria-label="Dismiss"
        className="absolute top-1.5 right-1.5"
        onClick={() => setBanner(null)}
      >
        <X />
      </Button>
    </Alert>
  )
}

function AccountList() {
  const { data: accounts, isLoading } = useQuery({ queryKey: queryKeys.accounts, queryFn: fetchMyAccounts })

  return (
    <Card>
      <CardHeader>
        <CardTitle>Accounts</CardTitle>
      </CardHeader>
      <CardContent>
        {isLoading && (
          <div className="flex flex-col gap-3">
            {Array.from({ length: 3 }, (_, index) => (
              <Skeleton key={index} className="h-12 w-full" />
            ))}
          </div>
        )}
        <ul className="-mx-2 divide-y divide-border">
          {accounts?.map((account) => (
            <li key={account.accountNumber}>
              <Link
                to={`/account/${account.accountNumber}`}
                className="flex items-center gap-3 rounded-lg px-2 py-3 transition-colors hover:bg-muted/60"
              >
                <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary">
                  <AccountIcon accountType={account.accountType} className="size-4" />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-medium">{accountLabel(account.accountType)}</p>
                  <p data-numeric className="text-xs text-muted-foreground">
                    ···· {account.accountNumber.slice(-4)}
                  </p>
                </div>
                <span data-numeric className="text-sm font-medium">
                  {formatMoney(account.balance, account.currency)}
                </span>
                <ChevronRight className="size-4 shrink-0 text-muted-foreground" />
              </Link>
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  )
}

function BalanceSummary() {
  const { data: accounts, isLoading } = useQuery({ queryKey: queryKeys.accounts, queryFn: fetchMyAccounts })

  const total = accounts?.reduce((sum, account) => sum + account.balance, 0) ?? 0
  const currency = accounts?.[0]?.currency ?? 'USD'

  return (
    <Card className="overflow-hidden">
      <CardContent className="flex flex-col gap-6 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-col gap-2">
          <span className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">Total balance</span>
          {isLoading ? (
            <Skeleton className="h-10 w-48" />
          ) : (
            <span data-numeric className="font-heading text-4xl font-semibold tracking-tight">
              {formatMoney(total, currency)}
            </span>
          )}
          <div className="flex flex-wrap gap-x-4 gap-y-1">
            {accounts?.map((account) => (
              <span key={account.accountNumber} className="text-xs text-muted-foreground">
                {accountLabel(account.accountType)}{' '}
                <span data-numeric className="font-medium text-foreground">
                  {formatMoney(account.balance, account.currency)}
                </span>
              </span>
            ))}
          </div>
        </div>

        <div className="flex gap-2">
          <FundingDialog mode="deposit">
            <Button size="lg">
              <ArrowDownLeft />
              Deposit
            </Button>
          </FundingDialog>
          <FundingDialog mode="withdraw">
            <Button size="lg" variant="outline">
              <ArrowUpRight />
              Withdraw
            </Button>
          </FundingDialog>
        </div>
      </CardContent>
    </Card>
  )
}

function CircleSnapshot() {
  const [viewing, setViewing] = useState<string | null>(null)
  const { data: circle, isLoading } = useQuery({
    queryKey: queryKeys.myCircle,
    queryFn: async () => {
      try {
        return await fetchMyCircle()
      } catch (err) {
        if (err instanceof ApiError && err.status === 404) {
          return null
        }
        throw err
      }
    },
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle>Your circle</CardTitle>
      </CardHeader>
      <CardContent>
        {isLoading && <Skeleton className="h-24 w-full" />}

        {!isLoading && !circle && (
          <EmptyState
            icon={Users}
            title="You're not in a circle yet"
            description="Join an open circle or start your own to begin contributing."
            action={
              <Button asChild size="sm">
                <Link to="/circles">Browse circles</Link>
              </Button>
            }
          />
        )}

        {circle && (
          <>
            <CircleCard
              circle={circle}
              onOpen={() => setViewing(circle.id)}
              action={
                <Button asChild variant="ghost" size="sm">
                  <Link to="/circles">
                    Manage
                    <ArrowRight />
                  </Link>
                </Button>
              }
            />
            <CircleDetailDialog circleId={viewing} onOpenChange={(open) => !open && setViewing(null)} />
          </>
        )}
      </CardContent>
    </Card>
  )
}

function RecentActivity() {
  const { data, isLoading } = useQuery({
    queryKey: queryKeys.recentActivity(RECENT_ACTIVITY_COUNT),
    queryFn: () => fetchActivity({ limit: RECENT_ACTIVITY_COUNT }),
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle>Recent activity</CardTitle>
        <CardDescription>Across every account. g an account above for its full statement.</CardDescription>
      </CardHeader>
      <CardContent>
        {isLoading && <ActivitySkeleton rows={4} />}
        {data && data.items.length === 0 && (
          <EmptyState icon={ReceiptText} title="No activity yet" description="Your ledger entries will appear here." />
        )}
        {data && data.items.length > 0 && <ActivityList entries={data.items} />}
      </CardContent>
    </Card>
  )
}
