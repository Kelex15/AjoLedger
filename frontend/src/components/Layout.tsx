import { NavLink, Outlet } from 'react-router'
import { useAuth } from '../auth/useAuth'

export function Layout() {
  const { logout } = useAuth()

  return (
    <div>
      <nav>
        <NavLink to="/" end>
          Dashboard
        </NavLink>{' '}
        <NavLink to="/circles">Circles</NavLink>{' '}
        <NavLink to="/profile">Profile</NavLink>{' '}
        <button onClick={() => logout()}>Log out</button>
      </nav>
      <hr />
      <Outlet />
    </div>
  )
}
