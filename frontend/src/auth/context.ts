import { createContext } from 'react'
import type { UserProfile } from '../api/auth'

export interface AuthContextValue {
  user: UserProfile | null | undefined
  isLoading: boolean
  isDemo: boolean
  login: (email: string, password: string) => Promise<void>
  loginWithGoogle: (idToken: string) => Promise<void>
  loginAsDemo: () => Promise<void>
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)
