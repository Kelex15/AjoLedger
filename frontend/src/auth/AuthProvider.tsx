import { type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import {
  fetchCurrentUser,
  login as loginRequest,
  loginWithGoogle as loginWithGoogleRequest,
  logout as logoutRequest,
} from '../api/auth'
import { queryKeys } from '../api/queryKeys'
import { AuthContext, type AuthContextValue } from './context'

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()

  const { data: user, isLoading } = useQuery({
    queryKey: queryKeys.currentUser,
    queryFn: async () => {
      try {
        return await fetchCurrentUser()
      } catch (error) {
        if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
          return null
        }
        throw error
      }
    },
    retry: false,
  })

  const loginMutation = useMutation({
    mutationFn: ({ email, password }: { email: string; password: string }) => loginRequest(email, password),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.currentUser }),
  })

  const loginWithGoogleMutation = useMutation({
    mutationFn: loginWithGoogleRequest,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.currentUser }),
  })

  const logoutMutation = useMutation({
    mutationFn: logoutRequest,
    onSuccess: () => queryClient.setQueryData(queryKeys.currentUser, null),
  })

  const value: AuthContextValue = {
    user,
    isLoading,
    login: async (email, password) => {
      await loginMutation.mutateAsync({ email, password })
    },
    loginWithGoogle: async (idToken) => {
      await loginWithGoogleMutation.mutateAsync(idToken)
    },
    logout: async () => {
      await logoutMutation.mutateAsync()
    },
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
