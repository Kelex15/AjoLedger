import { apiFetch } from './client'

export type UserAccountType = 'CHECKING' | 'SAVINGS' | 'LIEN'

export interface Account {
  accountNumber: string
  accountType: string
  balance: number
  currency: string
}

export interface LienStatus {
  accountNumber: string
  currency: string
  balance: number
  withdrawalFloor: number
  withdrawable: number
}

export function fetchMyAccounts() {
  return apiFetch<Account[]>('/api/accounts/me')
}

export function fetchLienStatus() {
  return apiFetch<LienStatus>('/api/ledger/lien')
}
