import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../features/authentication/context/AuthContext'

export function ApplicationLayout() {
  const navigate = useNavigate()
  const { user, logout } = useAuth()

  async function handleLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <div className="brand-mark small" aria-hidden="true">NS</div>
          <div>
            <strong>NexoSkill</strong>
            <span>Evaluaciones</span>
          </div>
        </div>

        <nav aria-label="Navegación principal">
          <NavLink to="/dashboard">Inicio</NavLink>
          <button type="button" disabled>Evaluaciones</button>
          <button type="button" disabled>Resultados</button>
          {user?.permissions.includes('USER_VIEW') && (
            <NavLink to="/admin/users">Usuarios</NavLink>
          )}
          {user?.permissions.includes('PROFILE_VIEW') && (
            <NavLink to="/profile">Mi perfil</NavLink>
          )}
        </nav>

        <div className="sidebar-status">
          <span className="status-dot" aria-hidden="true" />
          Sesión protegida
        </div>
      </aside>

      <div className="app-content">
        <header className="topbar">
          <div>
            <span className="muted-label">Sesión activa</span>
            <strong>{user?.displayName}</strong>
          </div>
          <button
            className="secondary-button"
            type="button"
            onClick={() => void handleLogout()}
          >
            Cerrar sesión
          </button>
        </header>
        <Outlet />
      </div>
    </div>
  )
}
