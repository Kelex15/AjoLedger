import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { ApiError } from '../api/client'
import { verifyEmail } from '../api/auth'

export function VerifyEmailPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')

  const [status, setStatus] = useState<'pending' | 'success' | 'error'>(token ? 'pending' : 'error')
  const [message, setMessage] = useState(token ? '' : 'Missing verification token.')
  const calledForToken = useRef<string | null>(null)

  useEffect(() => {
    if (!token) {
      return
    }
    // The token is single-use server-side; StrictMode's double-invoke would
    // otherwise burn it on the first call and show the second call's failure.
    if (calledForToken.current === token) {
      return
    }
    calledForToken.current = token

    verifyEmail(token)
      .then((result) => {
        setStatus('success')
        setMessage(result.message)
      })
      .catch((err) => {
        setStatus('error')
        setMessage(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
      })
  }, [token])

  return (
    <div>
      <h1>Email verification</h1>
      {status === 'pending' && <p>Verifying…</p>}
      {status !== 'pending' && <p>{message}</p>}
      {status === 'success' && <Link to="/login">Log in</Link>}
    </div>
  )
}
