import { useAuth } from '../auth/useAuth'
import { DemoSignupLink } from './DemoSignupLink'

/** Sits next to actions the demo account can't take, so the reason is where the visitor is looking. */
export function DemoNote() {
  const { isDemo } = useAuth()
  if (!isDemo) {
    return null
  }
  return (
    <p className="text-sm text-muted-foreground">
      The demo can't make changes. <DemoSignupLink>Create an account</DemoSignupLink> to try this yourself.
    </p>
  )
}
