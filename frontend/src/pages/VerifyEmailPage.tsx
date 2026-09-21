import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { ApiError } from '../api/client'
import { verifyEmail } from '../api/auth'
import { AuthLayout } from '../components/AuthLayout'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

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
    <AuthLayout>
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>Email verification</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {status === 'pending' && <p className="text-sm text-muted-foreground">Verifying…</p>}
          {status !== 'pending' && <p className="text-sm text-muted-foreground">{message}</p>}
          {status === 'success' && (
            <Link to="/login" className="text-sm text-foreground underline underline-offset-4">
              Log in
            </Link>
          )}
        </CardContent>
      </Card>
    </AuthLayout>
  )
}
