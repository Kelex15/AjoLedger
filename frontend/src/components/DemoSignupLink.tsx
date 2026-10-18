import { useNavigate } from 'react-router'
import { useAuth } from '../auth/useAuth'

/** Signs out of the demo first, or the signup and login pages would bounce the visitor straight back in. */
export function DemoSignupLink({ children }: { children: string }) {
  const { logout } = useAuth()
  const navigate = useNavigate()

  async function handleClick() {
    await logout()
    navigate('/signup')
  }

  return (
    <button type="button" onClick={handleClick} className="text-foreground underline underline-offset-4">
      {children}
    </button>
  )
}
