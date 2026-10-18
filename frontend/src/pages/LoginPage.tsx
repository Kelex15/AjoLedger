import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/useAuth'
import { AuthLayout } from '../components/AuthLayout'
import { ErrorMessage } from '../components/ErrorMessage'
import { FieldError } from '../components/FieldError'
import { GoogleSignInButton } from '../components/GoogleSignInButton'
import { PasswordInput } from '../components/PasswordInput'
import { ResendVerificationButton } from '../components/ResendVerificationButton'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Separator } from '@/components/ui/separator'
import { useFormErrors } from '../hooks/useFormErrors'

export function LoginPage() {
  const { user, login, loginWithGoogle, loginAsDemo } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [unverifiedEmail, setUnverifiedEmail] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [demoError, setDemoError] = useState<string | null>(null)
  const [isStartingDemo, setIsStartingDemo] = useState(false)
  const { errors, validate, clearError } = useFormErrors()

  if (user) {
    const redirectTo = (location.state as { from?: string } | null)?.from ?? '/'
    return <Navigate to={redirectTo} replace />
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setUnverifiedEmail(null)
    if (!validate(event.currentTarget)) {
      return
    }
    setIsSubmitting(true)
    try {
      await login(email, password)
      navigate('/', { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
      // Login only returns 403 for a correct password on an account that hasn't verified its email.
      if (err instanceof ApiError && err.status === 403) {
        setUnverifiedEmail(email)
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  async function handleGoogleCredential(idToken: string) {
    setError(null)
    try {
      await loginWithGoogle(idToken)
      navigate('/', { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
    }
  }

  async function handleTryDemo() {
    setDemoError(null)
    setIsStartingDemo(true)
    try {
      await loginAsDemo()
      navigate('/', { replace: true })
    } catch (err) {
      setDemoError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
    } finally {
      setIsStartingDemo(false)
    }
  }

  return (
    <AuthLayout>
      <Card>
        <CardHeader>
          <CardTitle className="text-xl">Welcome back</CardTitle>
          <CardDescription>Log in to pick up where you left off.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
            <div className="grid gap-1.5">
              <Label htmlFor="login-email">Email</Label>
              <Input
                id="login-email"
                name="email"
                type="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                onInput={() => clearError('email')}
                aria-invalid={Boolean(errors.email)}
                data-required-message="Enter your email address."
                required
              />
              <FieldError message={errors.email} />
            </div>

            <div className="grid gap-1.5">
              <Label htmlFor="login-password">Password</Label>
              <PasswordInput
                id="login-password"
                name="password"
                autoComplete="current-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                onInput={() => clearError('password')}
                aria-invalid={Boolean(errors.password)}
                data-required-message="Enter your password."
                required
              />
              <FieldError message={errors.password} />
            </div>

            <ErrorMessage message={error} />
            {unverifiedEmail && <ResendVerificationButton email={unverifiedEmail} />}

            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Logging in…' : 'Log in'}
            </Button>

            <p className="text-center text-sm text-muted-foreground">
              Don't have an account?{' '}
              <Link to="/signup" className="text-foreground underline underline-offset-4">
                Sign up
              </Link>
            </p>
          </form>

          <div className="my-4 flex items-center gap-3">
            <Separator className="flex-1" />
            <span className="text-xs text-muted-foreground">or</span>
            <Separator className="flex-1" />
          </div>

          <GoogleSignInButton onCredential={handleGoogleCredential} />

          <div className="mt-6 flex flex-col gap-2 rounded-lg border border-border p-4">
            <p className="text-sm font-medium">Just want to look around?</p>
            <p className="text-sm text-muted-foreground">
              Explore a demo account with real circle history. No sign-up needed.
            </p>
            <Button type="button" variant="secondary" onClick={handleTryDemo} disabled={isStartingDemo}>
              {isStartingDemo ? 'Opening the demo…' : 'Try the demo'}
            </Button>
            <ErrorMessage message={demoError} />
          </div>
        </CardContent>
      </Card>
    </AuthLayout>
  )
}
