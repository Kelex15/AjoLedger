import { useEffect, useState, type ReactNode } from 'react'
import { getStoredItem, setStoredItem, storageKeys } from '../lib/storage'
import { ThemeContext, type Theme } from './context'

function getInitialTheme(): Theme {
  return getStoredItem(storageKeys.theme) === 'light' ? 'light' : 'dark'
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setTheme] = useState<Theme>(getInitialTheme)

  useEffect(() => {
    document.documentElement.classList.toggle('dark', theme === 'dark')
    setStoredItem(storageKeys.theme, theme)
  }, [theme])

  function toggleTheme() {
    setTheme((current) => (current === 'dark' ? 'light' : 'dark'))
  }

  return <ThemeContext.Provider value={{ theme, toggleTheme }}>{children}</ThemeContext.Provider>
}
