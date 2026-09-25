import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { ArrowLeft, ArrowLeftRight, Lock, ReceiptText } from 'lucide-react'
import { Link, Navigate, useParams } from 'react-router'
import { fetchLienStatus, fetchMyAccounts } from '../api/accounts'
import { queryKeys } from '../api/queryKeys'
import { fetchActivity } from '../api/transactions'
import { AccountIcon } from '../components/AccountIcon'
import { ActivityList, ActivitySkeleton } from '../components/ActivityList'
import { EmptyState } from '../components/EmptyState'
import { LienDialog } from '../components/dashboard/LienDialog'
import { TransferDialog } from '../components/dashboard/TransferDialog'
import { accountLabel, formatMoney } from '../lib/format'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Progress } from '@/components/ui/progress'
import { Skeleton } from '@/components/ui/skeleton'

const PAGE_SIZE = 25

export function AccountPage() {
  const { accountNumber = '' } = useParams()
  const { data: accounts, isLoading } = useQuery({ queryKey: queryKeys.accounts, queryFn: fetchMyAccounts })
  const account = accounts?.find((candidate) => candidate.accountNumber === accountNumber)

  if (accounts && !account) {
    return <Navigate to="/" replace />
  }

  return (
    <div className="flex flex-col gap-6">
      <Button asChild variant="ghost" size="sm" className="-ml-2 self-start text-muted-foreground">
        <Link to="/">
          <ArrowLeft />
          Dashboard
        </Link>
      </Button>

      <Card>
        <CardContent className="flex flex-col gap-6">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div className="flex items-center gap-3">
              <span className="flex size-10 items-center justify-center rounded-full bg-primary/10 text-primary">
                <AccountIcon accountType={account?.accountType ?? ''} className="size-5" />
              </span>
              <div className="flex flex-col gap-1">
                {account ? (
                  <>
                    <h1 className="font-heading text-2xl font-semibold tracking-tight">
                      {accountLabel(account.accountType)}
                    </h1>
                    <span data-numeric className="text-xs text-muted-foreground">
                      ···· {account.accountNumber.slice(-4)}
                    </span>
                  </>
                ) : (
                  <Skeleton className="h-7 w-28" />
                )}
              </div>
            </div>

            {account && account.accountType !== 'LIEN' && (
              <TransferDialog fromAccount={account}>
                <Button>
                  <ArrowLeftRight />
                  Transfer
                </Button>
              </TransferDialog>
            )}
          </div>

          {isLoading || !account ? (
            <Skeleton className="h-10 w-48" />
          ) : (
            <span data-numeric className="font-heading text-4xl font-semibold tracking-tight">
              {formatMoney(account.balance, account.currency)}
            </span>
          )}
        </CardContent>
      </Card>

      {account?.accountType === 'LIEN' && <LienRestriction />}

      <Card>
        <CardHeader>
          <CardTitle>Transactions</CardTitle>
        </CardHeader>
        <CardContent>
          <AccountTransactions accountNumber={accountNumber} accountType={account?.accountType} />
        </CardContent>
      </Card>
    </div>
  )
}

function LienRestriction() {
  const { data: lien, isLoading } = useQuery({ queryKey: queryKeys.lien, queryFn: fetchLienStatus })

  if (isLoading) {
    return <Skeleton className="h-36 w-full rounded-xl" />
  }
  if (!lien) {
    return null
  }

  const lockedShare = lien.balance > 0 ? (lien.withdrawalFloor / lien.balance) * 100 : 0

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Lock className="size-4 text-muted-foreground" />
          Withdrawal restriction
        </CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <p className="text-sm text-muted-foreground">
          Your active circles hold part of this balance as collateral. The rest is yours to move.
        </p>

        <div className="flex flex-col gap-2">
          <Progress value={lockedShare} className="h-2" />
          <div className="flex justify-between text-xs">
            <span data-numeric className="text-muted-foreground">
              {formatMoney(lien.withdrawalFloor, lien.currency)} locked
            </span>
            <span data-numeric className="font-medium">
              {formatMoney(lien.withdrawable, lien.currency)} free
            </span>
          </div>
        </div>

        <LienDialog withdrawable={lien.withdrawable} currency={lien.currency}>
          <Button variant="outline" className="self-start">
            Manage lien
          </Button>
        </LienDialog>
      </CardContent>
    </Card>
  )
}

function AccountTransactions({ accountNumber, accountType }: { accountNumber: string; accountType?: string }) {
  const { data, isLoading, fetchNextPage, hasNextPage, isFetchingNextPage } = useInfiniteQuery({
    queryKey: queryKeys.accountActivity(accountNumber),
    queryFn: ({ pageParam }) => fetchActivity({ accountNumber, cursor: pageParam, limit: PAGE_SIZE }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
  })

  if (isLoading) {
    return <ActivitySkeleton rows={6} />
  }

  const entries = data?.pages.flatMap((page) => page.items) ?? []

  if (entries.length === 0) {
    return (
      <EmptyState
        icon={ReceiptText}
        title="No transactions yet"
        description={
          accountType
            ? `Money moving through your ${accountLabel(accountType)} account will show up here.`
            : 'Money moving through this account will show up here.'
        }
      />
    )
  }

  return (
    <div className="flex flex-col gap-6">
      <ActivityList entries={entries} grouped />
      {hasNextPage && (
        <Button
          variant="outline"
          className="self-center"
          onClick={() => fetchNextPage()}
          disabled={isFetchingNextPage}
        >
          {isFetchingNextPage ? 'Loading…' : 'Load more'}
        </Button>
      )}
    </div>
  )
}
