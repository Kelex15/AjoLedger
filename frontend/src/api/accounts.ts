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

// Everyday money first, collateral last. The API returns them in no particular order.
const DISPLAY_ORDER: UserAccountType[] = ['CHECKING', 'SAVINGS', 'LIEN']

export async function fetchMyAccounts() {
  const accounts = await apiFetch<Account[]>('/api/accounts/me')
  return accounts.toSorted(
    (a, b) =>
      DISPLAY_ORDER.indexOf(a.accountType as UserAccountType) -
      DISPLAY_ORDER.indexOf(b.accountType as UserAccountType),
  )
}

export function fetchLienStatus() {
  return apiFetch<LienStatus>('/api/ledger/lien')
}
