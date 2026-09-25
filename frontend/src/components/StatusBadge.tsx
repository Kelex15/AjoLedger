import { titleCase } from '../lib/format'
import { Badge } from '@/components/ui/badge'

type BadgeVariant = 'default' | 'secondary' | 'outline' | 'destructive'

const VARIANTS: Record<string, BadgeVariant> = {
  ACTIVE: 'default',
  FORMING: 'secondary',
  COMPLETED: 'outline',
  CANCELLED: 'destructive',
  DEFAULTED: 'destructive',
  BANNED: 'destructive',
  LEFT: 'outline',
  REMOVED: 'outline',
}

export function StatusBadge({ status }: { status: string }) {
  return <Badge variant={VARIANTS[status] ?? 'secondary'}>{titleCase(status)}</Badge>
}
