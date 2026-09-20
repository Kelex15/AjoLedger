import { apiFetch } from './client'
import type { UserAccountType } from './accounts'

export interface LedgerTransaction {
  id: string
  amount: number
  currency: string
  description: string
  transactionType: string
  operationId: string | null
  fromAccountId: string | null
  toAccountId: string | null
  createdAt: string
}

export function lienHold(amount: number) {
  return apiFetch<LedgerTransaction>('/api/ledger/lien/hold', {
    method: 'POST',
    body: JSON.stringify({ amount, idempotencyKey: crypto.randomUUID() }),
  })
}

export function lienWithdraw(amount: number) {
  return apiFetch<LedgerTransaction>('/api/ledger/lien/withdraw', {
    method: 'POST',
    body: JSON.stringify({ amount, idempotencyKey: crypto.randomUUID() }),
  })
}

export interface TransferFields {
  fromType: UserAccountType
  toUsername: string
  toType: UserAccountType
  amount: number
}

export function transfer(fields: TransferFields) {
  return apiFetch<LedgerTransaction>('/api/ledger/transfer', {
    method: 'POST',
    body: JSON.stringify({ ...fields, idempotencyKey: crypto.randomUUID() }),
  })
}
