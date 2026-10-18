import { apiFetch } from './client'

export interface UserProfile {
  id: string
  username: string
  firstName: string
  lastName: string
  email: string
}

export function fetchCurrentUser() {
  return apiFetch<UserProfile>('/api/user/me')
}

export function login(email: string, password: string) {
  return apiFetch<{ message: string }>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
}

/** Matches the backend's reserved demo username. */
export const DEMO_USERNAME = 'demo'

export function loginAsDemo() {
  return apiFetch<{ message: string }>('/api/auth/demo', { method: 'POST' })
}

export function loginWithGoogle(idToken: string) {
  return apiFetch<{ message: string }>('/api/auth/google', {
    method: 'POST',
    body: JSON.stringify({ idToken }),
  })
}

export interface SignupFields {
  username: string
  firstName: string
  lastName: string
  email: string
  password: string
}

export function signup(fields: SignupFields) {
  return apiFetch<{ message: string }>('/api/auth/signup', {
    method: 'POST',
    body: JSON.stringify(fields),
  })
}

export function verifyEmail(token: string) {
  return apiFetch<{ message: string }>('/api/auth/verify-email', {
    method: 'POST',
    body: JSON.stringify({ token }),
  })
}

export function resendVerification(email: string) {
  return apiFetch<{ message: string }>('/api/auth/resend-verification', {
    method: 'POST',
    body: JSON.stringify({ email }),
  })
}

export function logout() {
  return apiFetch<void>('/api/auth/logout', { method: 'POST' })
}
