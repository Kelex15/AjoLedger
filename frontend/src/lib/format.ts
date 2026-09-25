export function formatMoney(amount: number, currency: string) {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(amount)
}

/** Always carries an explicit + or −, for ledger lines where direction is the point. */
export function formatSignedMoney(amount: number, currency: string) {
  return `${amount < 0 ? '−' : '+'}${formatMoney(Math.abs(amount), currency)}`
}

export function formatDateTime(iso: string) {
  return new Date(iso).toLocaleString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function formatTime(iso: string) {
  return new Date(iso).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
}

/** Groups ledger lines into day sections, naming the two most recent days in relative terms. */
export function formatDayHeading(iso: string) {
  const date = new Date(iso)
  const today = new Date()
  const daysApart = Math.round(
    (new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime() -
      new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime()) /
      86_400_000,
  )
  if (daysApart === 0) return 'Today'
  if (daysApart === 1) return 'Yesterday'
  return date.toLocaleDateString(undefined, {
    day: 'numeric',
    month: 'long',
    ...(date.getFullYear() !== today.getFullYear() && { year: 'numeric' }),
  })
}

const ACCOUNT_LABELS: Record<string, string> = {
  CHECKING: 'Checking',
  SAVINGS: 'Savings',
  LIEN: 'Lien',
}

export function accountLabel(accountType: string) {
  return ACCOUNT_LABELS[accountType] ?? titleCase(accountType)
}

export function titleCase(value: string) {
  return value
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')
}
