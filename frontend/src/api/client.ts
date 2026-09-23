import { env } from '../config/env'

export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`))
  return match ? decodeURIComponent(match[1]) : null
}

const REFRESH_EXEMPT_PATHS = new Set([
  '/api/auth/login',
  '/api/auth/signup',
  '/api/auth/google',
  '/api/auth/refresh',
  '/api/auth/logout',
  '/api/auth/verify-email',
])

function rawFetch(path: string, options: RequestInit): Promise<Response> {
  const method = (options.method ?? 'GET').toUpperCase()
  const headers = new Headers(options.headers)

  if (options.body) {
    headers.set('Content-Type', 'application/json')
  }

  // The access/refresh tokens ride along as httpOnly cookies automatically;
  // mutating requests also need the CSRF double-submit header.
  if (method !== 'GET' && method !== 'HEAD') {
    const csrfToken = readCookie('XSRF-TOKEN')
    if (csrfToken) {
      headers.set('X-XSRF-TOKEN', csrfToken)
    }
  }

  return fetch(`${env.apiBaseUrl}${path}`, { ...options, method, headers, credentials: 'include' })
}

// Shared across concurrent callers so several requests failing around the same moment (e.g. a
// page firing multiple queries at once when the access token expires) trigger exactly one
// refresh, not one per request.
let refreshPromise: Promise<boolean> | null = null

function refreshAccessToken(): Promise<boolean> {
  if (!refreshPromise) {
    // Goes through rawFetch, not a bare fetch, /api/auth/refresh isn't CSRF-exempt on the
    // backend, so it needs the same X-XSRF-TOKEN header every other mutating request gets.
    refreshPromise = rawFetch('/api/auth/refresh', { method: 'POST' })
      .then((response) => response.ok)
      .catch(() => false)
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

export async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  let response = await rawFetch(path, options)

  // The access token is short-lived (15 min) by design; a 401 here almost always just means it
  // expired mid-session, not that the user is actually logged out. Silently refresh and retry
  // once before giving up.
  if (response.status === 401 && !REFRESH_EXEMPT_PATHS.has(path)) {
    const refreshed = await refreshAccessToken()
    if (refreshed) {
      response = await rawFetch(path, options)
    }
  }

  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new ApiError(response.status, body?.message ?? response.statusText)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json()
}
