import { Eye } from 'lucide-react'
import { useAuth } from '../auth/useAuth'
import { DemoSignupLink } from './DemoSignupLink'

export function DemoBanner() {
  const { isDemo } = useAuth()
  if (!isDemo) {
    return null
  }
  return (
    <div className="mb-6 flex items-start gap-3 rounded-lg border border-border bg-muted/50 p-4 text-sm">
      <Eye className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
      <div className="flex flex-col gap-1">
        <p className="font-medium">You're looking around the demo account.</p>
        <p className="text-muted-foreground">
          You can see everything here, but nothing can be changed. Its history was created by running the real
          circle and ledger flows all at once, so the entries share one date.{' '}
          <DemoSignupLink>Create your own account</DemoSignupLink> whenever you're ready.
        </p>
      </div>
    </div>
  )
}
