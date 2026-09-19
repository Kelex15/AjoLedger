import { NavLink, Outlet } from 'react-router'
import { useAuth } from '../auth/AuthContext'

export function Layout() {
  const { logout } = useAuth()

  return (
    <div>
      <nav>
        <NavLink to="/" end>
          Dashboard
        </NavLink>{' '}
        <NavLink to="/profile">Profile</NavLink>{' '}
        <NavLink to="/circles">Circles</NavLink>{' '}
        <button onClick={() => logout()}>Log out</button>
      </nav>
      <hr />
      <Outlet />
    </div>
  )
}
