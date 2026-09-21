import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router'
import App from './App.tsx'
import { ApiError } from './api/client'
import { queryKeys } from './api/queryKeys'
import { AuthProvider } from './auth/AuthProvider'
import { ThemeProvider } from './theme/ThemeProvider'
import './index.css'

// A 401 that reaches here means apiFetch already tried a silent refresh-and-retry and it still
// failed - the refresh token itself is gone, not just the short-lived access token. Flipping
// currentUser to null (rather than each query/mutation handling this individually) is what makes
// ProtectedRoute redirect to /login, no matter which request on which page surfaced it.
function handleSessionExpired(error: unknown) {
  if (error instanceof ApiError && error.status === 401) {
    queryClient.setQueryData(queryKeys.currentUser, null)
  }
}

const queryClient = new QueryClient({
  queryCache: new QueryCache({ onError: handleSessionExpired }),
  mutationCache: new MutationCache({ onError: handleSessionExpired }),
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <AuthProvider>
            <App />
          </AuthProvider>
        </BrowserRouter>
      </QueryClientProvider>
    </ThemeProvider>
  </StrictMode>,
)
