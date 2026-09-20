import { apiFetch } from './client'

export type FundingType = 'STRIPE' | 'PAYPAL'

export interface FundingTransaction {
  id: string
  amount: number
  currency: string
  description: string
  transactionType: string
  createdAt: string
}

export interface DepositFields {
  fundingType: FundingType
  amount: number
}

export function deposit(fields: DepositFields) {
  return apiFetch<FundingTransaction>('/api/funding/deposit', {
    method: 'POST',
    body: JSON.stringify({ ...fields, idempotencyKey: crypto.randomUUID() }),
  })
}

export interface WithdrawalFields {
  fundingType: FundingType
  amount: number
}

export function withdraw(fields: WithdrawalFields) {
  return apiFetch<FundingTransaction>('/api/funding/withdraw', {
    method: 'POST',
    body: JSON.stringify({ ...fields, idempotencyKey: crypto.randomUUID() }),
  })
}
