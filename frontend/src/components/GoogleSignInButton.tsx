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
          renderButton: (
            parent: HTMLElement,
            options: { theme?: string; size?: string; width?: number },
          ) => void
        }
      }
    }
  }
}

const SCRIPT_SRC = 'https://accounts.google.com/gsi/client'

// Google clamps the rendered button to this range and ignores anything outside it.
const GSI_MIN_WIDTH = 200
const GSI_MAX_WIDTH = 400

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
    let observer: ResizeObserver | undefined

    loadGoogleScript().then(() => {
      const container = buttonRef.current
      if (cancelled || !container || !window.google) {
        return
      }
      window.google.accounts.id.initialize({
        client_id: env.googleClientId,
        callback: (response) => onCredentialRef.current(response.credential),
      })

      // Without an explicit width Google picks its own, and the personalised
      // "Sign in as <name>" variant is wide enough to overflow the card.
      let rendered = 0
      const render = () => {
        const width = Math.round(Math.min(GSI_MAX_WIDTH, Math.max(GSI_MIN_WIDTH, container.clientWidth)))
        if (width === rendered) {
          return
        }
        rendered = width
        container.replaceChildren()
        window.google!.accounts.id.renderButton(container, { theme: 'outline', size: 'large', width })
      }

      render()
      observer = new ResizeObserver(render)
      observer.observe(container)
    })

    return () => {
      cancelled = true
      observer?.disconnect()
    }
  }, [])

  return <div ref={buttonRef} className="w-full" />
}
