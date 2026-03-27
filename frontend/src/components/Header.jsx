import { NavLink } from 'react-router-dom'
import { useAuth0 } from '@auth0/auth0-react'

export default function Header() {
  const { user, logout } = useAuth0()

  const initials = user?.name
    ? user.name.split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2)
    : '?'

  return (
    <header className="header">
      <div className="header-inner">
        <NavLink to="/schedules" className="header-logo">
          🎙 Papercast
        </NavLink>
        <nav className="header-nav">
          <NavLink to="/schedules" className={({ isActive }) => isActive ? 'active' : ''}>
            Schedules
          </NavLink>
        </nav>
        <div className="header-user">
          <div className="header-avatar" title={user?.email}>
            {user?.picture
              ? <img src={user.picture} alt={user.name} />
              : initials
            }
          </div>
          <span style={{ display: 'none' /* hidden on small screens */ }}>{user?.name}</span>
          <button
            onClick={() => logout({ logoutParams: { returnTo: window.location.origin + '/login' } })}
            style={{ fontSize: '0.8125rem', height: '1.875rem' }}
          >
            Log out
          </button>
        </div>
      </div>
    </header>
  )
}
