import { useState, type FormEvent } from 'react'
import { MailCheck } from 'lucide-react'
import { Link } from 'react-router'
import { ApiError } from '../api/client'
import { signup } from '../api/auth'
import { AuthLayout } from '../components/AuthLayout'
import { ErrorMessage } from '../components/ErrorMessage'
import { FieldError } from '../components/FieldError'
import { PasswordInput } from '../components/PasswordInput'
import { PasswordStrength } from '../components/PasswordStrength'
import { ResendVerificationButton } from '../components/ResendVerificationButton'
import { useFormErrors } from '../hooks/useFormErrors'
import { usePasswordStrength } from '../hooks/usePasswordStrength'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

export function SignupPage() {
  const [username, setUsername] = useState('')
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const { errors, validate, clearError } = useFormErrors()
  const passwordStrength = usePasswordStrength(password)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    if (!validate(event.currentTarget, { password: passwordStrength.error })) {
      return
    }
    setIsSubmitting(true)
    try {
      const result = await signup({ username, firstName, lastName, email, password })
      setSuccessMessage(result.message)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
    } finally {
      setIsSubmitting(false)
    }
  }

  if (successMessage) {
    return (
      <AuthLayout>
        <Card>
          <CardHeader>
            <span className="flex size-10 items-center justify-center rounded-full bg-success/12 text-success">
              <MailCheck className="size-5" />
            </span>
            <CardTitle className="text-xl">Check your email</CardTitle>
            <CardDescription>{successMessage}</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-3">
            <p className="text-sm text-muted-foreground">
              Nothing after a minute or two? Check your spam folder, or send it again.
            </p>
            <ResendVerificationButton email={email} />
            <Button asChild variant="ghost" className="w-full">
              <Link to="/login">Back to log in</Link>
            </Button>
          </CardContent>
        </Card>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout>
      <Card>
        <CardHeader>
          <CardTitle className="text-xl">Create your account</CardTitle>
          <CardDescription>Start saving with a circle you trust.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
            <div className="grid grid-cols-2 gap-3">
              <div className="grid gap-1.5">
                <Label htmlFor="signup-first-name">First name</Label>
                <Input
                  id="signup-first-name"
                  name="firstName"
                  value={firstName}
                  onChange={(event) => setFirstName(event.target.value)}
                  onInput={() => clearError('firstName')}
                  aria-invalid={Boolean(errors.firstName)}
                  data-required-message="Enter your first name."
                  required
                />
                <FieldError message={errors.firstName} />
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor="signup-last-name">Last name</Label>
                <Input
                  id="signup-last-name"
                  name="lastName"
                  value={lastName}
                  onChange={(event) => setLastName(event.target.value)}
                  onInput={() => clearError('lastName')}
                  aria-invalid={Boolean(errors.lastName)}
                  data-required-message="Enter your last name."
                  required
                />
                <FieldError message={errors.lastName} />
              </div>
            </div>

            <div className="grid gap-1.5">
              <Label htmlFor="signup-username">Username</Label>
              <Input
                id="signup-username"
                name="username"
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                onInput={() => clearError('username')}
                aria-invalid={Boolean(errors.username)}
                data-required-message="Pick a username."
                required
              />
              <FieldError message={errors.username} />
            </div>

            <div className="grid gap-1.5">
              <Label htmlFor="signup-email">Email</Label>
              <Input
                id="signup-email"
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
              <Label htmlFor="signup-password">Password</Label>
              <PasswordInput
                id="signup-password"
                name="password"
                autoComplete="new-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                onInput={() => clearError('password')}
                aria-invalid={Boolean(errors.password)}
                aria-describedby="signup-password-strength"
                data-required-message="Choose a password."
                required
              />
              <PasswordStrength id="signup-password-strength" password={password} strength={passwordStrength} />
              <FieldError message={errors.password} />
            </div>

            <ErrorMessage message={error} />

            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Signing up…' : 'Sign up'}
            </Button>

            <p className="text-center text-sm text-muted-foreground">
              Already have an account?{' '}
              <Link to="/login" className="text-foreground underline underline-offset-4">
                Log in
              </Link>
            </p>
          </form>
        </CardContent>
      </Card>
    </AuthLayout>
  )
}
