import { useEffect, useRef, useState } from 'react'
import { CircleCheck, CircleX, Loader2 } from 'lucide-react'
import { Link, useSearchParams } from 'react-router'
import { ApiError } from '../api/client'
import { verifyEmail } from '../api/auth'
import { AuthLayout } from '../components/AuthLayout'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { cn } from 'cn'

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
      <Card>
        <CardHeader>
          <span
            className={cn(
              'flex size-10 items-center justify-center rounded-full',
              status === 'success' && 'bg-success/12 text-success',
              status === 'error' && 'bg-destructive/12 text-destructive',
              status === 'pending' && 'bg-muted text-muted-foreground',
            )}
          >
            {status === 'pending' && <Loader2 className="size-5 animate-spin" />}
            {status === 'success' && <CircleCheck className="size-5" />}
            {status === 'error' && <CircleX className="size-5" />}
          </span>
          <CardTitle className="text-xl">
            {status === 'pending' ? 'Verifying your email' : status === 'success' ? 'Email verified' : "That didn't work"}
          </CardTitle>
          <CardDescription>{status === 'pending' ? 'One moment…' : message}</CardDescription>
        </CardHeader>
        {status !== 'pending' && (
          <CardContent>
            <Button asChild className="w-full" variant={status === 'success' ? 'default' : 'outline'}>
              <Link to="/login">{status === 'success' ? 'Log in' : 'Back to log in'}</Link>
            </Button>
          </CardContent>
        )}
      </Card>
    </AuthLayout>
  )
}
