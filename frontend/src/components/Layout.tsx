import { useState } from 'react'
import { Landmark, LogOut, Menu, Moon, Sun, UserRound, Users, Wallet } from 'lucide-react'
import { NavLink, Outlet, useLocation } from 'react-router'
import { useAuth } from '../auth/useAuth'
import { useTheme } from '../theme/useTheme'
import { DemoBanner } from './DemoBanner'
import { Logo } from './Logo'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Sheet, SheetContent, SheetTitle, SheetTrigger } from '@/components/ui/sheet'
import { cn } from 'cn'

const NAV_ITEMS = [
  { to: '/', label: 'Dashboard', icon: Wallet, end: true },
  { to: '/account', label: 'Account', icon: Landmark, end: false },
  { to: '/circles', label: 'Circles', icon: Users, end: false },
]

function SidebarNav({ onNavigate }: { onNavigate?: () => void }) {
  return (
    <nav className="flex flex-col gap-0.5">
      {NAV_ITEMS.map(({ to, label, icon: Icon, end }) => (
        <NavLink
          key={to}
          to={to}
          end={end}
          onClick={onNavigate}
          className={({ isActive }) =>
            cn(
              'flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-sm font-medium transition-colors',
              isActive
                ? 'bg-sidebar-accent text-sidebar-accent-foreground'
                : 'text-muted-foreground hover:bg-sidebar-accent/60 hover:text-sidebar-accent-foreground',
            )
          }
        >
          <Icon className="size-4 shrink-0" />
          {label}
        </NavLink>
      ))}
    </nav>
  )
}

function UserMenu() {
  const { user, logout } = useAuth()
  const { theme, toggleTheme } = useTheme()
  const initials = `${user?.firstName?.[0] ?? ''}${user?.lastName?.[0] ?? ''}`.toUpperCase()

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button className="flex w-full items-center gap-2.5 rounded-lg px-2 py-2 text-left transition-colors hover:bg-sidebar-accent/60 focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none">
          <Avatar className="size-8">
            <AvatarFallback className="bg-primary/12 text-xs font-semibold text-primary">{initials}</AvatarFallback>
          </Avatar>
          <span className="min-w-0 flex-1">
            <span className="block truncate text-sm font-medium">{user?.firstName} {user?.lastName}</span>
            <span className="block truncate text-xs text-muted-foreground">@{user?.username}</span>
          </span>
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" side="top" className="w-56">
        <DropdownMenuItem asChild>
          <NavLink to="/profile">
            <UserRound />
            Profile
          </NavLink>
        </DropdownMenuItem>
        <DropdownMenuItem onSelect={(event) => { event.preventDefault(); toggleTheme() }}>
          {theme === 'dark' ? <Sun /> : <Moon />}
          {theme === 'dark' ? 'Light theme' : 'Dark theme'}
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={() => logout()}>
          <LogOut />
          Log out
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

export function Layout() {
  const [mobileNavOpen, setMobileNavOpen] = useState(false)
  const location = useLocation()

  return (
    <div className="min-h-screen bg-background text-foreground">
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-60 flex-col justify-between border-r border-sidebar-border bg-sidebar p-3 md:flex">
        <div className="flex flex-col gap-6">
          <NavLink to="/" className="px-2 py-1.5 text-sidebar-foreground">
            <Logo />
          </NavLink>
          <SidebarNav />
        </div>
        <UserMenu />
      </aside>

      <header className="sticky top-0 z-20 flex items-center justify-between gap-3 border-b border-border bg-background/85 px-4 py-2.5 backdrop-blur-md md:hidden">
        <Sheet open={mobileNavOpen} onOpenChange={setMobileNavOpen}>
          <SheetTrigger asChild>
            <Button variant="ghost" size="icon" aria-label="Open navigation">
              <Menu />
            </Button>
          </SheetTrigger>
          <SheetContent side="left" className="w-64 bg-sidebar p-3">
            <SheetTitle className="sr-only">Navigation</SheetTitle>
            <div className="flex h-full flex-col justify-between gap-6">
              <div className="flex flex-col gap-6">
                <span className="px-2 py-1.5 text-sidebar-foreground">
                  <Logo />
                </span>
                <SidebarNav onNavigate={() => setMobileNavOpen(false)} />
              </div>
              <UserMenu />
            </div>
          </SheetContent>
        </Sheet>
        <Logo />
        <span className="w-8" />
      </header>

      <main key={location.pathname} className="animate-in fade-in duration-300 md:pl-60">
        <div className="mx-auto w-full max-w-4xl px-4 py-6 sm:px-6 sm:py-10">
          <DemoBanner />
          <Outlet />
        </div>
      </main>
    </div>
  )
}
