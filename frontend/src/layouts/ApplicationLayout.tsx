import { useEffect, useMemo, useRef, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../features/authentication/context/AuthContext'
import { getAllOrganizations, getCurrentOrganizationBranding } from '../features/organizations/api/organizationApi'
import type { OrganizationBranding, OrganizationSummary } from '../features/organizations/types/organizations'
import { BrandLogo } from '../shared/components/BrandLogo'
import { Breadcrumbs } from '../shared/components/Breadcrumbs'
import { Icon, type IconName } from '../shared/components/Icon'
import { authorizedHome } from '../shared/utils/authorizedHome'
import { ORGANIZATION_CONTEXT_CHANGED_EVENT, ORGANIZATION_CONTEXT_KEY } from '../shared/api/apiClient'

interface NavItem {
  id: string
  label: string
  to?: string
  icon: IconName
  permission?: string
  roles?: string[]
  children?: NavItem[]
}

interface NavSection {
  id: string
  label: string
  icon: IconName
  items: NavItem[]
}

const navigationConfig: NavSection[] = [
  {
    id: 'administration',
    label: 'Administración',
    icon: 'users',
    items: [
      { id: 'organizations', label: 'Organizaciones', to: '/admin/organizations', icon: 'collections', permission: 'ORGANIZATION_VIEW', roles: ['ADMINISTRATOR'] },
      { id: 'users', label: 'Usuarios', to: '/admin/users', icon: 'users', permission: 'USER_VIEW', roles: ['ADMINISTRATOR'] },
      { id: 'roles', label: 'Roles', to: '/admin/roles', icon: 'lock', permission: 'ROLE_MANAGE', roles: ['ADMINISTRATOR'] },
      { id: 'students', label: 'Colaboradores', to: '/admin/collaborators', icon: 'profile', permission: 'STUDENT_VIEW' },
      { id: 'talent-bank', label: 'Talent Bank', to: '/admin/talent-bank', icon: 'users', permission: 'TALENT_VIEW' }
    ]
  },
  {
    id: 'catalogs',
    label: 'Catálogos',
    icon: 'categories',
    items: [
      { id: 'catalog-categories', label: 'Categorías', to: '/admin/catalogs/CATEGORIES', icon: 'categories', permission: 'CATALOG_VIEW' },
      { id: 'catalog-technologies', label: 'Tecnologías', to: '/admin/catalogs/TECHNOLOGIES', icon: 'code', permission: 'CATALOG_VIEW' },
      { id: 'catalog-professional-profiles', label: 'Perfiles', to: '/admin/catalogs/PROFESSIONAL_PROFILES', icon: 'profile', permission: 'CATALOG_VIEW' },
      { id: 'catalog-technological-profiles', label: 'Perfiles tecnológicos', to: '/admin/catalogs/TECHNOLOGICAL_PROFILES', icon: 'profile', permission: 'CATALOG_VIEW' }
    ]
  },
  {
    id: 'content',
    label: 'Banco de Preguntas',
    icon: 'questions',
    items: [
      { id: 'questions', label: 'Preguntas', to: '/admin/questions', icon: 'questions', permission: 'QUESTION_VIEW' },
      { id: 'forms', label: 'Formularios', to: '/admin/forms', icon: 'clipboard', permission: 'FORM_VIEW' },
      { id: 'collections', label: 'Colecciones', to: '/admin/collections', icon: 'collections', permission: 'COLLECTION_VIEW' }
    ]
  },
  {
    id: 'evaluation',
    label: 'Evaluaciones',
    icon: 'clipboard',
    items: []
  }
]

function initials(displayName?: string) {
  const values = (displayName ?? 'VT').trim().split(/\s+/).filter(Boolean)
  return values.slice(0, 2).map((value) => value[0]?.toUpperCase() ?? '').join('') || 'VT'
}

function routeMatches(pathname: string, to?: string) {
  if (!to) return false
  return pathname === to || pathname.startsWith(`${to}/`)
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
    () => new Set(['administration', 'catalogs', 'content'])
  )
  const [openGroups, setOpenGroups] = useState<Set<string>>(
    () => new Set(['catalogs'])
  )
  const [branding, setBranding] = useState<OrganizationBranding>({
    global: true, organizationPublicId: null, name: 'Valtieris', hasLogo: true, logoUrl: null
  })
  const [organizations, setOrganizations] = useState<OrganizationSummary[]>([])
  const [contextOrganization, setContextOrganization] = useState(
    () => window.localStorage.getItem(ORGANIZATION_CONTEXT_KEY) ?? ''
  )
  const accountRef = useRef<HTMLDivElement>(null)

  const administrator = user?.roles.includes('ADMINISTRATOR') ?? false
  const globalAdministrationRoute = administrator && (
    location.pathname.startsWith('/admin/organizations')
    || location.pathname.startsWith('/admin/users')
    || location.pathname.startsWith('/admin/roles')
  )
  const displayBranding: OrganizationBranding = globalAdministrationRoute
    ? { global: true, organizationPublicId: null, name: 'Valtieris', hasLogo: true, logoUrl: null }
    : branding
  const canDashboard = administrator || (user?.permissions.includes('DASHBOARD_VIEW') ?? false)
  const homeRoute = authorizedHome(user)
  const canShow = (item: NavItem) => {
    if (item.roles && !item.roles.some((role) => user?.roles.includes(role))) return false
    return administrator || !item.permission || user?.permissions.includes(item.permission)
  }

  const sections = useMemo(
    () => navigationConfig
      .map((section) => ({
        ...section,
        items: section.items
          .filter(canShow)
          .map((item) => ({
            ...item,
            children: item.children?.filter(canShow)
          }))
          .filter((item) => item.to || (item.children?.length ?? 0) > 0)
      }))
      .filter((section) => section.items.length > 0),
    [administrator, user?.permissions, user?.roles]
  )

  function itemIsActive(item: NavItem): boolean {
    return routeMatches(location.pathname, item.to)
      || Boolean(item.children?.some((child) => itemIsActive(child)))
  }

  useEffect(() => {
    setMobileOpen(false)
    setAccountOpen(false)
    setOpenSections((current) => {
      const next = new Set(current)
      sections.forEach((section) => {
        if (section.items.some((item) => itemIsActive(item))) next.add(section.id)
      })
      return next
    })
    setOpenGroups((current) => {
      const next = new Set(current)
      sections.flatMap((section) => section.items).forEach((item) => {
        if (item.children?.some((child) => itemIsActive(child))) next.add(item.id)
      })
      return next
    })
  }, [location.pathname, sections])

  useEffect(() => {
    if (!user) return
    let active = true
    const controller = new AbortController()

    const loadBranding = async () => {
      try {
        const value = await getCurrentOrganizationBranding(controller.signal)
        if (active) setBranding(value)
      } catch {
        if (active) {
          setBranding({ global: true, organizationPublicId: null, name: 'Valtieris', hasLogo: true, logoUrl: null })
        }
      }
    }

    void loadBranding()
    if (administrator) {
      void getAllOrganizations({ status: 'ACTIVE', sort: 'name', direction: 'ASC', signal: controller.signal })
        .then((values) => { if (active) setOrganizations(values.filter((item) => item.organizationType === 'CUSTOMER')) })
        .catch(() => { if (active) setOrganizations([]) })
    } else {
      setOrganizations([])
      setContextOrganization('')
    }

    const handleContextChanged = () => {
      setContextOrganization(window.localStorage.getItem(ORGANIZATION_CONTEXT_KEY) ?? '')
      void loadBranding()
    }
    window.addEventListener(ORGANIZATION_CONTEXT_CHANGED_EVENT, handleContextChanged)
    return () => {
      active = false
      controller.abort()
      window.removeEventListener(ORGANIZATION_CONTEXT_CHANGED_EVENT, handleContextChanged)
    }
  }, [administrator, user])

  useEffect(() => {
    if (!user) return
    document.title = displayBranding.name || 'Valtieris'
  }, [displayBranding.name, user])

  useEffect(() => () => { document.title = 'Valtieris' }, [])

  function changeOrganizationContext(publicId: string) {
    if (!administrator) return
    if (publicId) window.localStorage.setItem(ORGANIZATION_CONTEXT_KEY, publicId)
    else window.localStorage.removeItem(ORGANIZATION_CONTEXT_KEY)
    window.dispatchEvent(new Event(ORGANIZATION_CONTEXT_CHANGED_EVENT))
    window.location.reload()
  }

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

  function ensureExpandedSidebar() {
    if (!collapsed) return
    setCollapsed(false)
    window.localStorage.setItem('nexoskill:sidebar-collapsed', '0')
  }

  function toggleSection(id: string) {
    ensureExpandedSidebar()
    setOpenSections((current) => {
      const next = new Set(current)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  function toggleGroup(id: string) {
    ensureExpandedSidebar()
    setOpenGroups((current) => {
      const next = new Set(current)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
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
          <NavLink className={`sidebar-brand${displayBranding.global ? '' : ' organization-brand'}`} to={homeRoute} aria-label={`Ir al inicio de ${displayBranding.name}`}>
            {displayBranding.global ? (
              <>
                <BrandLogo variant="horizontal" className="sidebar-brand-logo sidebar-brand-logo-expanded" decorative />
                <BrandLogo variant="isotype" className="sidebar-brand-logo sidebar-brand-logo-collapsed" decorative />
              </>
            ) : displayBranding.hasLogo && displayBranding.logoUrl ? (
              <>
                <span className="organization-brand-expanded"><img alt="" aria-hidden="true" src={displayBranding.logoUrl} /><strong>{displayBranding.name}</strong></span>
                <img alt="" aria-hidden="true" className="organization-brand-collapsed" src={displayBranding.logoUrl} />
              </>
            ) : (
              <>
                <span className="organization-brand-expanded organization-brand-neutral"><b>{initials(displayBranding.name)}</b><strong>{displayBranding.name}</strong></span>
                <span className="organization-brand-collapsed organization-brand-neutral-mark">{initials(displayBranding.name)}</span>
              </>
            )}
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
          {canDashboard && (
            <NavLink
              className={({ isActive }) => `nav-item nav-item-root${isActive ? ' active' : ''}`}
              to="/dashboard"
              title="Dashboard"
            >
              <Icon name="results" />
              <span>Dashboard</span>
            </NavLink>
          )}
          {sections.map((section) => {
            const expanded = openSections.has(section.id)
            const active = section.items.some((item) => itemIsActive(item))
            return (
              <section className="nav-section" key={section.id}>
                <button
                  aria-expanded={expanded}
                  className={`nav-section-trigger${active ? ' active' : ''}`}
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
                    {section.items.map((item) => {
                      const itemActive = itemIsActive(item)
                      if (item.children?.length) {
                        const groupExpanded = openGroups.has(item.id)
                        return (
                          <div className="nav-group" key={item.id}>
                            <button
                              aria-expanded={groupExpanded}
                              className={`nav-item nav-item-parent${itemActive ? ' active' : ''}`}
                              title={item.label}
                              type="button"
                              onClick={() => toggleGroup(item.id)}
                            >
                              <Icon name={item.icon} size={17} />
                              <span>{item.label}</span>
                              <Icon className="nav-section-chevron" name={groupExpanded ? 'chevronDown' : 'chevronRight'} size={14} />
                            </button>
                            {groupExpanded && (
                              <div className="nav-submenu nav-submenu-third">
                                {item.children.map((child) => (
                                  <NavLink
                                    className={({ isActive }) => `nav-item nav-item-third${isActive || routeMatches(location.pathname, child.to) ? ' active' : ''}`}
                                    key={child.id}
                                    to={child.to!}
                                    title={child.label}
                                  >
                                    <Icon name={child.icon} size={15} />
                                    <span>{child.label}</span>
                                  </NavLink>
                                ))}
                              </div>
                            )}
                          </div>
                        )
                      }
                      return (
                        <NavLink
                          className={({ isActive }) => `nav-item${isActive || itemActive ? ' active' : ''}`}
                          key={item.id}
                          to={item.to!}
                          title={item.label}
                        >
                          <Icon name={item.icon} size={17} />
                          <span>{item.label}</span>
                        </NavLink>
                      )
                    })}
                  </div>
                )}
              </section>
            )
          })}
        </nav>
        <div className="sidebar-footer">
          <span className="sidebar-version">{displayBranding.global ? 'Valtieris Talent Platform' : displayBranding.name}</span>
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
          <div className="topbar-actions">
            {administrator && !globalAdministrationRoute && (
              <label className="organization-context-switcher">
                <span>Contexto</span>
                <select aria-label="Contexto de organización" value={contextOrganization} onChange={(event) => changeOrganizationContext(event.target.value)}>
                  <option value="">Global · Valtieris</option>
                  {organizations.map((organization) => (
                    <option key={organization.publicId} value={organization.publicId}>{organization.name}</option>
                  ))}
                </select>
              </label>
            )}
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
                {(administrator || user?.permissions.includes('PROFILE_VIEW')) && (
                  <NavLink to="/profile"><Icon name="profile" size={17} />Perfil</NavLink>
                )}
                {(administrator || user?.permissions.includes('PASSWORD_CHANGE')) && (
                  <NavLink to="/change-password" state={{ from: `${location.pathname}${location.search}` }}><Icon name="lock" size={17} />Cambiar contraseña</NavLink>
                )}
                <div className="account-dropdown-separator" />
                <button type="button" onClick={() => void handleLogout()}>
                  <Icon name="logout" size={17} />Cerrar sesión
                </button>
              </div>
            )}
          </div>
          </div>
        </header>
        <Outlet />
      </div>
    </div>
  )
}
