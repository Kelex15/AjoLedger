import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { ApiError } from '../api/client'
import { signup } from '../api/auth'
import { ErrorMessage } from '../components/ErrorMessage'

export function SignupPage() {
  const [username, setUsername] = useState('')
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
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
      <div>
        <h1>Check your email</h1>
        <p>{successMessage}</p>
        <Link to="/login">Back to log in</Link>
      </div>
    )
  }

  return (
    <form onSubmit={handleSubmit}>
      <h1>Sign up</h1>

      <label>
        Username
        <input value={username} onChange={(event) => setUsername(event.target.value)} required />
      </label>

      <label>
        First name
        <input value={firstName} onChange={(event) => setFirstName(event.target.value)} required />
      </label>

      <label>
        Last name
        <input value={lastName} onChange={(event) => setLastName(event.target.value)} required />
      </label>

      <label>
        Email
        <input type="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
      </label>

      <label>
        Password
        <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} required />
      </label>

      <ErrorMessage message={error} />

      <button type="submit" disabled={isSubmitting}>
        {isSubmitting ? 'Signing up…' : 'Sign up'}
      </button>

      <p>
        Already have an account? <Link to="/login">Log in</Link>
      </p>
    </form>
  )
}
