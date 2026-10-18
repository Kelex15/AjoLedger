import { useState } from 'react'
import { ApiError } from '../api/client'
import { resendVerification } from '../api/auth'
import { ErrorMessage } from './ErrorMessage'
import { Button } from '@/components/ui/button'

export function ResendVerificationButton({ email }: { email: string }) {
  const [status, setStatus] = useState<'idle' | 'sending' | 'sent'>('idle')
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function handleClick() {
    setError(null)
    setStatus('sending')
    try {
      const result = await resendVerification(email)
      setMessage(result.message)
      setStatus('sent')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
      setStatus('idle')
    }
  }

  return (
    <div className="flex flex-col gap-2">
      <Button type="button" variant="outline" onClick={handleClick} disabled={status !== 'idle'}>
        {status === 'sending' ? 'Sending…' : 'Resend verification email'}
      </Button>
      {message && (
        <p role="status" className="text-sm text-muted-foreground">
          {message}
        </p>
      )}
      <ErrorMessage message={error} />
    </div>
  )
}
