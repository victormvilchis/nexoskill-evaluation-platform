import { useEffect, useMemo, useRef, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../features/authentication/context/AuthContext'
import { Breadcrumbs } from '../shared/components/Breadcrumbs'
import { Icon, type IconName } from '../shared/components/Icon'

interface NavItem {
  label: string
  to?: string
  icon: IconName
  permission?: string
  disabled?: boolean
  badge?: string
}

interface NavSection {
  id: string
  label: string
  icon: IconName
  items: NavItem[]
}

function initials(displayName?: string) {
  const values = (displayName ?? 'NS').trim().split(/\s+/).filter(Boolean)
  return values.slice(0, 2).map((value) => value[0]?.toUpperCase() ?? '').join('') || 'NS'
}

export function ApplicationLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const { user, logout } = useAuth()
  const [collapsed, setCollapsed] = useState(
    () => window.localStorage.getItem('nexoskill:sidebar-collapsed') === '1'
  )
  const [mobileOpen, setMobileOpen] = useState(false)
  const [accountOpen, setAccountOpen] = useState(false)
  const [openSections, setOpenSections] = useState<Set<string>>(
    () => new Set(['administration', 'content'])
  )
  const accountRef = useRef<HTMLDivElement>(null)

  const sections = useMemo<NavSection[]>(
    () => [
      {
        id: 'administration',
        label: 'Administración',
        icon: 'users',
        items: [
          { label: 'Usuarios', to: '/admin/users', icon: 'users', permission: 'USER_VIEW' }
        ]
      },
      {
        id: 'content',
        label: 'Contenido',
        icon: 'questions',
        items: [
          { label: 'Preguntas', to: '/admin/questions', icon: 'questions', permission: 'QUESTION_VIEW' },
          { label: 'Categorías', to: '/admin/question-categories', icon: 'categories', permission: 'QUESTION_CATEGORY_MANAGE' },
          { label: 'Colecciones', to: '/admin/question-collections', icon: 'collections', permission: 'COLLECTION_VIEW' }
        ]
      },
      {
        id: 'evaluation',
        label: 'Evaluaciones',
        icon: 'clipboard',
        items: [
          { label: 'Formularios', to: '/admin/forms', icon: 'clipboard', permission: 'FORM_VIEW' },
          { label: 'Resultados', icon: 'results', disabled: true, badge: 'Próximamente' }
        ]
      }
    ],
    []
  )

  useEffect(() => {
    setMobileOpen(false)
    setAccountOpen(false)
  }, [location.pathname])

  useEffect(() => {
    function handlePointerDown(event: MouseEvent) {
      if (accountRef.current && !accountRef.current.contains(event.target as Node)) {
        setAccountOpen(false)
      }
    }
    function handleEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setAccountOpen(false)
        setMobileOpen(false)
      }
    }
    document.addEventListener('mousedown', handlePointerDown)
    document.addEventListener('keydown', handleEscape)
    return () => {
      document.removeEventListener('mousedown', handlePointerDown)
      document.removeEventListener('keydown', handleEscape)
    }
  }, [])

  async function handleLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  function toggleCollapsed() {
    setCollapsed((current) => {
      const next = !current
      window.localStorage.setItem('nexoskill:sidebar-collapsed', next ? '1' : '0')
      return next
    })
  }

  function toggleSection(id: string) {
    if (collapsed) {
      setCollapsed(false)
      window.localStorage.setItem('nexoskill:sidebar-collapsed', '0')
    }
    setOpenSections((current) => {
      const next = new Set(current)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  function canShow(item: NavItem) {
    return !item.permission || user?.permissions.includes(item.permission)
  }

  return (
    <div className={`app-shell ${collapsed ? 'sidebar-collapsed' : ''}`}>
      {mobileOpen && (
        <button
          aria-label="Cerrar menú"
          className="sidebar-overlay"
          type="button"
          onClick={() => setMobileOpen(false)}
        />
      )}

      <aside className={`sidebar ${mobileOpen ? 'sidebar-mobile-open' : ''}`}>
        <div className="sidebar-header">
          <NavLink className="sidebar-brand" to="/dashboard" aria-label="Ir al inicio">
            <div className="brand-mark small" aria-hidden="true">NS</div>
            <div className="sidebar-brand-copy">
              <strong>NexoSkill</strong>
              <span>Evaluaciones</span>
            </div>
          </NavLink>
          <button
            aria-label={collapsed ? 'Expandir menú' : 'Contraer menú'}
            className="sidebar-collapse-button desktop-only"
            type="button"
            onClick={toggleCollapsed}
          >
            <Icon name={collapsed ? 'chevronRight' : 'menu'} size={18} />
          </button>
        </div>

        <nav className="sidebar-navigation" aria-label="Navegación principal">
          <NavLink
            className={({ isActive }) => `nav-item nav-item-root${isActive ? ' active' : ''}`}
            to="/dashboard"
            title="Inicio"
          >
            <Icon name="home" />
            <span>Inicio</span>
          </NavLink>

          {sections.map((section) => {
            const availableItems = section.items.filter(canShow)
            if (availableItems.length === 0) return null
            const expanded = openSections.has(section.id)
            return (
              <section className="nav-section" key={section.id}>
                <button
                  aria-expanded={expanded}
                  className="nav-section-trigger"
                  title={section.label}
                  type="button"
                  onClick={() => toggleSection(section.id)}
                >
                  <Icon name={section.icon} />
                  <span>{section.label}</span>
                  <Icon className="nav-section-chevron" name={expanded ? 'chevronDown' : 'chevronRight'} size={15} />
                </button>
                {expanded && (
                  <div className="nav-submenu">
                    {availableItems.map((item) => item.to ? (
                      <NavLink
                        className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}
                        key={item.label}
                        to={item.to}
                        title={item.label}
                      >
                        <Icon name={item.icon} size={17} />
                        <span>{item.label}</span>
                      </NavLink>
                    ) : (
                      <button className="nav-item nav-item-disabled" disabled key={item.label} title={item.label} type="button">
                        <Icon name={item.icon} size={17} />
                        <span>{item.label}</span>
                        {item.badge && <small>{item.badge}</small>}
                      </button>
                    ))}
                  </div>
                )}
              </section>
            )
          })}
        </nav>

        <div className="sidebar-footer">
          <span className="sidebar-version">NexoSkill Platform</span>
        </div>
      </aside>

      <div className="app-content">
        <header className="topbar">
          <div className="topbar-left">
            <button
              aria-label="Abrir menú"
              className="icon-button mobile-menu-button"
              type="button"
              onClick={() => setMobileOpen(true)}
            >
              <Icon name="menu" />
            </button>
            <Breadcrumbs />
          </div>

          <div className="account-menu" ref={accountRef}>
            <button
              aria-expanded={accountOpen}
              className="account-trigger"
              type="button"
              onClick={() => setAccountOpen((current) => !current)}
            >
              <span className="avatar">{initials(user?.displayName)}</span>
              <span className="account-trigger-copy">
                <strong>{user?.displayName}</strong>
                <small>{user?.roles.includes('ADMINISTRATOR') ? 'Administrador' : 'Usuario'}</small>
              </span>
              <Icon name="chevronDown" size={15} />
            </button>

            {accountOpen && (
              <div className="account-dropdown">
                <div className="account-dropdown-header">
                  <span className="avatar avatar-large">{initials(user?.displayName)}</span>
                  <div>
                    <strong>{user?.displayName}</strong>
                    <small>{user?.email}</small>
                  </div>
                </div>
                {user?.permissions.includes('PROFILE_VIEW') && (
                  <NavLink to="/profile"><Icon name="profile" size={17} />Perfil</NavLink>
                )}
                <NavLink to="/change-password"><Icon name="lock" size={17} />Cambiar contraseña</NavLink>
                <div className="account-dropdown-separator" />
                <button type="button" onClick={() => void handleLogout()}>
                  <Icon name="logout" size={17} />Cerrar sesión
                </button>
              </div>
            )}
          </div>
        </header>
        <Outlet />
      </div>
    </div>
  )
}
