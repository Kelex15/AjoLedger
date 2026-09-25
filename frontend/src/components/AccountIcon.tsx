import { Lock, PiggyBank, Wallet, type LucideIcon } from 'lucide-react'

const ICONS: Record<string, LucideIcon> = {
  CHECKING: Wallet,
  SAVINGS: PiggyBank,
  LIEN: Lock,
}

export function AccountIcon({ accountType, className }: { accountType: string; className?: string }) {
  const Icon = ICONS[accountType] ?? Wallet
  return <Icon className={className} />
}
