import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { UserAccountType } from '../api/accounts'
import { fetchLienStatus, fetchMyAccounts } from '../api/accounts'
import { ApiError } from '../api/client'
import { deposit, withdraw, type FundingType } from '../api/funding'
import { queryKeys } from '../api/queryKeys'
import { lienHold, lienWithdraw, transfer } from '../api/transactions'
import { useAuth } from '../auth/AuthContext'

export function DashboardPage() {
  const { user } = useAuth()
  const queryClient = useQueryClient()

  const { data: accounts, isLoading } = useQuery({
    queryKey: queryKeys.accounts,
    queryFn: fetchMyAccounts,
  })

  const [targetType, setTargetType] = useState<UserAccountType>('CHECKING')
  const [fundingType, setFundingType] = useState<FundingType>('STRIPE')
  const [amount, setAmount] = useState('')
  const [error, setError] = useState<string | null>(null)

  const depositMutation = useMutation({
    mutationFn: deposit,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.accounts })
      setAmount('')
    },
  })

  async function handleDeposit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    try {
      await depositMutation.mutateAsync({ targetType, fundingType, amount: Number(amount) })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
    }
  }

  const [sourceType, setSourceType] = useState<UserAccountType>('CHECKING')
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
      await withdrawMutation.mutateAsync({ sourceType, fundingType: withdrawFundingType, amount: Number(withdrawAmount) })
    } catch (err) {
      setWithdrawError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
    }
  }

  const [transferFromType, setTransferFromType] = useState<UserAccountType>('CHECKING')
  const [transferToUserId, setTransferToUserId] = useState('')
  const [transferToType, setTransferToType] = useState<UserAccountType>('CHECKING')
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
        toUserId: transferToUserId,
        toType: transferToType,
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
    <div>
      <h1>Welcome, {user?.firstName}</h1>

      <h2>Balances</h2>
      {isLoading && <p>Loading…</p>}
      {accounts && (
        <ul>
          {accounts.map((account) => (
            <li key={account.accountNumber}>
              {account.accountType}: {account.balance.toFixed(2)} {account.currency}
            </li>
          ))}
        </ul>
      )}

      <h2>Deposit funds</h2>
      <form onSubmit={handleDeposit}>
        <label>
          To account
          <select value={targetType} onChange={(event) => setTargetType(event.target.value as UserAccountType)}>
            <option value="CHECKING">Checking</option>
            <option value="SAVINGS">Savings</option>
            <option value="LIEN">Lien</option>
          </select>
        </label>

        <label>
          Via
          <select value={fundingType} onChange={(event) => setFundingType(event.target.value as FundingType)}>
            <option value="STRIPE">Stripe</option>
            <option value="PAYPAL">PayPal</option>
          </select>
        </label>

        <label>
          Amount
          <input
            type="number"
            min="0.01"
            step="0.01"
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
            required
          />
        </label>

        {error && <p role="alert">{error}</p>}

        <button type="submit" disabled={depositMutation.isPending}>
          {depositMutation.isPending ? 'Depositing…' : 'Deposit'}
        </button>
      </form>

      <h2>Withdraw funds</h2>
      <form onSubmit={handleWithdraw}>
        <label>
          From account
          <select value={sourceType} onChange={(event) => setSourceType(event.target.value as UserAccountType)}>
            <option value="CHECKING">Checking</option>
            <option value="SAVINGS">Savings</option>
            <option value="LIEN">Lien</option>
          </select>
        </label>

        <label>
          Via
          <select
            value={withdrawFundingType}
            onChange={(event) => setWithdrawFundingType(event.target.value as FundingType)}
          >
            <option value="STRIPE">Stripe</option>
            <option value="PAYPAL">PayPal</option>
          </select>
        </label>

        <label>
          Amount
          <input
            type="number"
            min="0.01"
            step="0.01"
            value={withdrawAmount}
            onChange={(event) => setWithdrawAmount(event.target.value)}
            required
          />
        </label>

        {withdrawError && <p role="alert">{withdrawError}</p>}

        <button type="submit" disabled={withdrawMutation.isPending}>
          {withdrawMutation.isPending ? 'Withdrawing…' : 'Withdraw'}
        </button>
      </form>

      <h2>Transfer between accounts</h2>
      <form onSubmit={handleTransfer}>
        <label>
          From account
          <select
            value={transferFromType}
            onChange={(event) => setTransferFromType(event.target.value as UserAccountType)}
          >
            <option value="CHECKING">Checking</option>
            <option value="SAVINGS">Savings</option>
            <option value="LIEN">Lien</option>
          </select>
        </label>

        <label>
          To user id
          <input value={transferToUserId} onChange={(event) => setTransferToUserId(event.target.value)} required />
        </label>

        <label>
          To account
          <select value={transferToType} onChange={(event) => setTransferToType(event.target.value as UserAccountType)}>
            <option value="CHECKING">Checking</option>
            <option value="SAVINGS">Savings</option>
            <option value="LIEN">Lien</option>
          </select>
        </label>

        <label>
          Amount
          <input
            type="number"
            min="0.01"
            step="0.01"
            value={transferAmount}
            onChange={(event) => setTransferAmount(event.target.value)}
            required
          />
        </label>

        {transferError && <p role="alert">{transferError}</p>}

        <button type="submit" disabled={transferMutation.isPending}>
          {transferMutation.isPending ? 'Transferring…' : 'Transfer'}
        </button>
      </form>

      <h2>Lien</h2>
      {lien && (
        <ul>
          <li>Balance: {lien.balance.toFixed(2)} {lien.currency}</li>
          <li>Withdrawal floor: {lien.withdrawalFloor.toFixed(2)} {lien.currency}</li>
          <li>Withdrawable: {lien.withdrawable.toFixed(2)} {lien.currency}</li>
        </ul>
      )}

      <label>
        Amount
        <input
          type="number"
          min="0.01"
          step="0.01"
          value={lienAmount}
          onChange={(event) => setLienAmount(event.target.value)}
        />
      </label>

      {lienError && <p role="alert">{lienError}</p>}

      <button onClick={handleLienHold} disabled={lienHoldMutation.isPending || !lienAmount}>
        {lienHoldMutation.isPending ? 'Adding…' : 'Add to lien'}
      </button>{' '}
      <button onClick={handleLienWithdraw} disabled={lienWithdrawMutation.isPending || !lienAmount}>
        {lienWithdrawMutation.isPending ? 'Withdrawing…' : 'Withdraw from lien'}
      </button>
    </div>
  )
}
