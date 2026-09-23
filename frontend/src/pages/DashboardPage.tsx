import { useEffect, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useSearchParams } from 'react-router'
import type { UserAccountType } from '../api/accounts'
import { fetchLienStatus, fetchMyAccounts } from '../api/accounts'
import { ApiError } from '../api/client'
import { deposit, withdraw, type FundingType } from '../api/funding'
import { queryKeys } from '../api/queryKeys'
import { lienHold, lienWithdraw, transfer } from '../api/transactions'
import { useAuth } from '../auth/useAuth'
import { ErrorMessage } from '../components/ErrorMessage'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

export function DashboardPage() {
  const { user } = useAuth()
  const queryClient = useQueryClient()

  const { data: accounts, isLoading } = useQuery({
    queryKey: queryKeys.accounts,
    queryFn: fetchMyAccounts,
  })

  const [fundingType, setFundingType] = useState<FundingType>('STRIPE')
  const [amount, setAmount] = useState('')
  const [error, setError] = useState<string | null>(null)

  // Landed back here from Stripe's hosted checkout. The deposit itself is confirmed by the
  // webhook, not this redirect, so this banner is just UI feedback, not the source of truth.
  const [searchParams, setSearchParams] = useSearchParams()
  const [depositBanner, setDepositBanner] = useState<'success' | 'cancelled' | null>(null)
  useEffect(() => {
    const depositStatus = searchParams.get('depositStatus')
    if (depositStatus !== 'success' && depositStatus !== 'cancelled') {
      return
    }
    setDepositBanner(depositStatus)
    if (depositStatus === 'success') {
      queryClient.invalidateQueries({ queryKey: queryKeys.accounts })
    }
    setSearchParams((params) => {
      params.delete('depositStatus')
      return params
    }, { replace: true })
  }, [searchParams, queryClient, setSearchParams])

  const depositMutation = useMutation({
    mutationFn: deposit,
    onSuccess: (result) => {
      if (result.checkoutUrl) {
        window.location.href = result.checkoutUrl
        return
      }
      queryClient.invalidateQueries({ queryKey: queryKeys.accounts })
      setAmount('')
    },
  })

  async function handleDeposit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    try {
      await depositMutation.mutateAsync({ fundingType, amount: Number(amount) })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
    }
  }

  const [withdrawFundingType, setWithdrawFundingType] = useState<FundingType>('STRIPE')
  const [withdrawAmount, setWithdrawAmount] = useState('')
  const [withdrawError, setWithdrawError] = useState<string | null>(null)

  const withdrawMutation = useMutation({
    mutationFn: withdraw,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.accounts })
      setWithdrawAmount('')
    },
  })

  async function handleWithdraw(event: FormEvent) {
    event.preventDefault()
    setWithdrawError(null)
    try {
      await withdrawMutation.mutateAsync({ fundingType: withdrawFundingType, amount: Number(withdrawAmount) })
    } catch (err) {
      setWithdrawError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
    }
  }

  const [transferFromType, setTransferFromType] = useState<UserAccountType>('CHECKING')
  const [transferToAnotherUser, setTransferToAnotherUser] = useState(false)
  const [transferToUsername, setTransferToUsername] = useState('')
  const [transferToType, setTransferToType] = useState<UserAccountType>('SAVINGS')
  const [transferAmount, setTransferAmount] = useState('')
  const [transferError, setTransferError] = useState<string | null>(null)

  const transferMutation = useMutation({
    mutationFn: transfer,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.accounts })
      setTransferAmount('')
    },
  })

  async function handleTransfer(event: FormEvent) {
    event.preventDefault()
    setTransferError(null)
    try {
      await transferMutation.mutateAsync({
        fromType: transferFromType,
        toUsername: transferToAnotherUser ? transferToUsername : user!.username,
        // Transfers to another user can only land in their Checking account.
        toType: transferToAnotherUser ? 'CHECKING' : transferToType,
        amount: Number(transferAmount),
      })
    } catch (err) {
      setTransferError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
    }
  }

  const { data: lien } = useQuery({
    queryKey: queryKeys.lien,
    queryFn: fetchLienStatus,
  })

  const [lienAmount, setLienAmount] = useState('')
  const [lienError, setLienError] = useState<string | null>(null)

  function invalidateLienQueries() {
    queryClient.invalidateQueries({ queryKey: queryKeys.lien })
    queryClient.invalidateQueries({ queryKey: queryKeys.accounts })
    setLienAmount('')
  }

  const lienHoldMutation = useMutation({
    mutationFn: lienHold,
    onSuccess: invalidateLienQueries,
  })

  const lienWithdrawMutation = useMutation({
    mutationFn: lienWithdraw,
    onSuccess: invalidateLienQueries,
  })

  async function handleLienHold() {
    setLienError(null)
    try {
      await lienHoldMutation.mutateAsync(Number(lienAmount))
    } catch (err) {
      setLienError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
    }
  }

  async function handleLienWithdraw() {
    setLienError(null)
    try {
      await lienWithdrawMutation.mutateAsync(Number(lienAmount))
    } catch (err) {
      setLienError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
    }
  }

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6">
      <h1 className="text-2xl font-semibold">Welcome, {user?.firstName}</h1>

      <Card>
        <CardHeader>
          <CardTitle>Balances</CardTitle>
        </CardHeader>
        <CardContent>
          {isLoading && <p className="text-sm text-muted-foreground">Loading…</p>}
          {accounts && (
            <ul className="flex flex-col gap-1 text-sm">
              {accounts.map((account) => (
                <li key={account.accountNumber} className="flex justify-between">
                  <span>{account.accountType}</span>
                  <span>
                    {account.balance.toFixed(2)} {account.currency}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Deposit funds</CardTitle>
          <CardDescription>Deposits always land in Checking.</CardDescription>
        </CardHeader>
        <CardContent>
          {depositBanner === 'success' && (
            <Alert className="mb-4">
              <AlertDescription>Payment received. Your balance will update shortly.</AlertDescription>
            </Alert>
          )}
          {depositBanner === 'cancelled' && (
            <Alert className="mb-4">
              <AlertDescription>Checkout cancelled, no funds were moved.</AlertDescription>
            </Alert>
          )}
          <form onSubmit={handleDeposit} className="flex flex-col gap-4">
            <div className="grid gap-1.5">
              <Label htmlFor="deposit-via">Via</Label>
              <Select value={fundingType} onValueChange={(value) => setFundingType(value as FundingType)}>
                <SelectTrigger id="deposit-via">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="STRIPE">Stripe</SelectItem>
                  <SelectItem value="PAYPAL">PayPal</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="grid gap-1.5">
              <Label htmlFor="deposit-amount">Amount</Label>
              <Input
                id="deposit-amount"
                type="number"
                min="0.01"
                step="0.01"
                value={amount}
                onChange={(event) => setAmount(event.target.value)}
                required
              />
            </div>

            <ErrorMessage message={error} />

            <Button type="submit" disabled={depositMutation.isPending}>
              {depositMutation.isPending ? 'Depositing…' : 'Deposit'}
            </Button>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Withdraw funds</CardTitle>
          <CardDescription>Withdrawals always draw from Checking.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleWithdraw} className="flex flex-col gap-4">
            <div className="grid gap-1.5">
              <Label htmlFor="withdraw-via">Via</Label>
              <Select
                value={withdrawFundingType}
                onValueChange={(value) => setWithdrawFundingType(value as FundingType)}
              >
                <SelectTrigger id="withdraw-via">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="STRIPE">Stripe</SelectItem>
                  <SelectItem value="PAYPAL">PayPal</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="grid gap-1.5">
              <Label htmlFor="withdraw-amount">Amount</Label>
              <Input
                id="withdraw-amount"
                type="number"
                min="0.01"
                step="0.01"
                value={withdrawAmount}
                onChange={(event) => setWithdrawAmount(event.target.value)}
                required
              />
            </div>

            <ErrorMessage message={withdrawError} />

            <Button type="submit" disabled={withdrawMutation.isPending}>
              {withdrawMutation.isPending ? 'Withdrawing…' : 'Withdraw'}
            </Button>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Transfer between accounts</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleTransfer} className="flex flex-col gap-4">
            <div className="grid gap-1.5">
              <Label htmlFor="transfer-from">From account</Label>
              <Select
                value={transferFromType}
                onValueChange={(value) => setTransferFromType(value as UserAccountType)}
              >
                <SelectTrigger id="transfer-from">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="CHECKING">Checking</SelectItem>
                  <SelectItem value="SAVINGS">Savings</SelectItem>
                  <SelectItem value="LIEN">Lien</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="flex items-center gap-2">
              <Checkbox
                id="transfer-to-another-user"
                checked={transferToAnotherUser}
                onCheckedChange={(checked) => setTransferToAnotherUser(checked === true)}
              />
              <Label htmlFor="transfer-to-another-user">Send to another user</Label>
            </div>

            {transferToAnotherUser ? (
              <div className="grid gap-1.5">
                <Label htmlFor="transfer-username">Username</Label>
                <Input
                  id="transfer-username"
                  value={transferToUsername}
                  onChange={(event) => setTransferToUsername(event.target.value)}
                  required
                />
                <p className="text-sm text-muted-foreground">Lands in their Checking account.</p>
              </div>
            ) : (
              <div className="grid gap-1.5">
                <Label htmlFor="transfer-to">To account</Label>
                <Select value={transferToType} onValueChange={(value) => setTransferToType(value as UserAccountType)}>
                  <SelectTrigger id="transfer-to">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="CHECKING">Checking</SelectItem>
                    <SelectItem value="SAVINGS">Savings</SelectItem>
                    <SelectItem value="LIEN">Lien</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            )}

            <div className="grid gap-1.5">
              <Label htmlFor="transfer-amount">Amount</Label>
              <Input
                id="transfer-amount"
                type="number"
                min="0.01"
                step="0.01"
                value={transferAmount}
                onChange={(event) => setTransferAmount(event.target.value)}
                required
              />
            </div>

            <ErrorMessage message={transferError} />

            <Button type="submit" disabled={transferMutation.isPending}>
              {transferMutation.isPending ? 'Transferring…' : 'Transfer'}
            </Button>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Lien</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {lien && (
            <ul className="flex flex-col gap-1 text-sm">
              <li className="flex justify-between">
                <span>Balance</span>
                <span>
                  {lien.balance.toFixed(2)} {lien.currency}
                </span>
              </li>
              <li className="flex justify-between">
                <span>Withdrawal floor</span>
                <span>
                  {lien.withdrawalFloor.toFixed(2)} {lien.currency}
                </span>
              </li>
              <li className="flex justify-between">
                <span>Withdrawable</span>
                <span>
                  {lien.withdrawable.toFixed(2)} {lien.currency}
                </span>
              </li>
            </ul>
          )}

          <div className="grid gap-1.5">
            <Label htmlFor="lien-amount">Amount</Label>
            <Input
              id="lien-amount"
              type="number"
              min="0.01"
              step="0.01"
              value={lienAmount}
              onChange={(event) => setLienAmount(event.target.value)}
            />
          </div>

          <ErrorMessage message={lienError} />

          <div className="flex gap-2">
            <Button onClick={handleLienHold} disabled={lienHoldMutation.isPending || !lienAmount}>
              {lienHoldMutation.isPending ? 'Adding…' : 'Add to lien'}
            </Button>
            <Button
              variant="outline"
              onClick={handleLienWithdraw}
              disabled={lienWithdrawMutation.isPending || !lienAmount}
            >
              {lienWithdrawMutation.isPending ? 'Withdrawing…' : 'Withdraw from lien'}
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
