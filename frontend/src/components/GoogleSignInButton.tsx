import { useEffect, useRef } from 'react'
import { env } from '../config/env'

interface GoogleCredentialResponse {
  credential: string
}

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (config: {
            client_id: string
            callback: (response: GoogleCredentialResponse) => void
          }) => void
          renderButton: (parent: HTMLElement, options: { theme?: string; size?: string }) => void
        }
      }
    }
  }
}

const SCRIPT_SRC = 'https://accounts.google.com/gsi/client'

function loadGoogleScript(): Promise<void> {
  if (window.google?.accounts?.id) {
    return Promise.resolve()
  }
  const existing = document.querySelector<HTMLScriptElement>(`script[src="${SCRIPT_SRC}"]`)
  if (existing) {
    return new Promise((resolve) => existing.addEventListener('load', () => resolve()))
  }
  return new Promise((resolve) => {
    const script = document.createElement('script')
    script.src = SCRIPT_SRC
    script.async = true
    script.onload = () => resolve()
    document.head.appendChild(script)
  })
}

export function GoogleSignInButton({ onCredential }: { onCredential: (idToken: string) => void }) {
  const buttonRef = useRef<HTMLDivElement>(null)
  // Kept fresh across renders so the effect below can stay mount-only, re-running
  // initialize/renderButton on every render would otherwise re-mount the widget.
  const onCredentialRef = useRef(onCredential)
  useEffect(() => {
    onCredentialRef.current = onCredential
  })

  useEffect(() => {
    let cancelled = false
    loadGoogleScript().then(() => {
      if (cancelled || !buttonRef.current || !window.google) {
        return
      }
      window.google.accounts.id.initialize({
        client_id: env.googleClientId,
        callback: (response) => onCredentialRef.current(response.credential),
      })
      window.google.accounts.id.renderButton(buttonRef.current, { theme: 'outline', size: 'large' })
    })
    return () => {
      cancelled = true
    }
  }, [])

  return <div ref={buttonRef} />
}
