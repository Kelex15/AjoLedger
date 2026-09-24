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

// Exactly one field is set: transaction when the deposit completed synchronously, checkoutUrl
// when it's pending and the caller should redirect there to finish it with Stripe.
export interface DepositResult {
  transaction: FundingTransaction | null
  checkoutUrl: string | null
}

export function deposit(fields: DepositFields) {
  return apiFetch<DepositResult>('/api/funding/deposit', {
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

// Returns the transaction if the deposit was posted (either now, or already earlier by the
// webhook), or undefined if Stripe hasn't confirmed the payment yet.
export function verifyStripeCheckoutSession(sessionId: string) {
  return apiFetch<FundingTransaction | undefined>(
    `/api/funding/stripe/checkout-sessions/${encodeURIComponent(sessionId)}/verify`,
    { method: 'POST' },
  )
}
