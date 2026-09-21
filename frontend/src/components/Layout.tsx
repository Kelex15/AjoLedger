import { Moon, Sun } from 'lucide-react'
import { NavLink, Outlet } from 'react-router'
import { useAuth } from '../auth/useAuth'
import { useTheme } from '../theme/useTheme'
import { Button } from '@/components/ui/button'
import { cn } from 'cn'

const navLinkClassName = ({ isActive }: { isActive: boolean }) =>
  cn(
    'rounded-lg px-2.5 py-1.5 text-sm font-medium transition-colors hover:bg-muted hover:text-foreground',
    isActive ? 'bg-muted text-foreground' : 'text-muted-foreground',
  )

export function Layout() {
  const { logout } = useAuth()
  const { theme, toggleTheme } = useTheme()

  return (
    <div className="min-h-screen bg-background text-foreground">
      <nav className="flex items-center justify-between gap-4 border-b border-border px-4 py-3">
        <div className="flex items-center gap-1">
          <NavLink to="/" end className={navLinkClassName}>
            Dashboard
          </NavLink>
          <NavLink to="/circles" className={navLinkClassName}>
            Circles
          </NavLink>
          <NavLink to="/profile" className={navLinkClassName}>
            Profile
          </NavLink>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            size="icon"
            aria-label={theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'}
            onClick={toggleTheme}
          >
            {theme === 'dark' ? <Sun /> : <Moon />}
          </Button>
          <Button variant="outline" size="sm" onClick={() => logout()}>
            Log out
          </Button>
        </div>
      </nav>
      <main className="p-4">
        <Outlet />
      </main>
    </div>
  )
}
