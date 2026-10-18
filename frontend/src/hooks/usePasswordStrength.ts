import { useEffect, useState } from 'react'
import type { ZXCVBNResult } from 'zxcvbn'

// Mirrors @StrongPassword on the backend, which scores with zxcvbn4j, a port of this same zxcvbn.
export const MIN_PASSWORD_LENGTH = 10
export const MIN_PASSWORD_SCORE = 3

type Zxcvbn = (password: string) => ZXCVBNResult

// zxcvbn ships its dictionaries inline (~400 KB gzipped), so it stays out of the main bundle.
let zxcvbnPromise: Promise<Zxcvbn> | null = null
function loadZxcvbn() {
  zxcvbnPromise ??= import('zxcvbn').then((module) => module.default)
  return zxcvbnPromise
}

export function usePasswordStrength(password: string) {
  const [zxcvbn, setZxcvbn] = useState<Zxcvbn | null>(null)

  useEffect(() => {
    // If the chunk fails to load the meter just stays hidden; the server still enforces strength.
    loadZxcvbn()
      .then((fn) => setZxcvbn(() => fn))
      .catch(() => undefined)
  }, [])

  const result = zxcvbn && password ? zxcvbn(password) : null
  const longEnough = password.length >= MIN_PASSWORD_LENGTH
  const hardToGuess = result !== null && result.score >= MIN_PASSWORD_SCORE

  let error: string | null = null
  if (password && !longEnough) {
    error = `Use at least ${MIN_PASSWORD_LENGTH} characters.`
  } else if (result && !hardToGuess) {
    error = 'Pick a password that’s harder to guess.'
  }

  return { result, longEnough, hardToGuess, error }
}

export type PasswordStrength = ReturnType<typeof usePasswordStrength>
