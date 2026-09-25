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

// Every deposit goes through a processor, so this is always a URL to redirect to.
export interface DepositResult {
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

// Undefined while Stripe hasn't confirmed the payment yet.
export function verifyStripeCheckoutSession(sessionId: string) {
  return apiFetch<FundingTransaction | undefined>(
    `/api/funding/stripe/checkout-sessions/${encodeURIComponent(sessionId)}/verify`,
    { method: 'POST' },
  )
}

// Undefined while the order is approved but not yet captured.
export function settlePayPalOrder(orderId: string) {
  return apiFetch<FundingTransaction | undefined>(
    `/api/funding/paypal/orders/${encodeURIComponent(orderId)}/settle`,
    { method: 'POST' },
  )
}
