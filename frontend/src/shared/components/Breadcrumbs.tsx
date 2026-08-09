import { Link, useLocation } from 'react-router-dom'
import { Icon } from './Icon'

interface Crumb {
  label: string
  to?: string
}

const catalogLabels: Record<string, string> = {
  CATEGORIES: 'Categorías',
  TECHNOLOGIES: 'Tecnologías',
  PROFESSIONAL_PROFILES: 'Perfiles',
  TECHNOLOGICAL_PROFILES: 'Perfiles tecnológicos'
}

function resolveCrumbs(pathname: string): Crumb[] {
  if (pathname === '/dashboard') return [{ label: 'Dashboard' }]
  if (pathname === '/access-denied') return [{ label: 'Acceso no disponible' }]
  if (pathname === '/profile') return [{ label: 'Cuenta' }, { label: 'Perfil' }]
  if (pathname === '/change-password') return [{ label: 'Cuenta' }, { label: 'Contraseña' }]
  if (pathname.startsWith('/admin/roles')) {
    const segments = pathname.split('/').filter(Boolean)
    const base: Crumb[] = [{ label: 'Administración' }, { label: 'Roles', to: '/admin/roles' }]
    if (pathname === '/admin/roles/new') base.push({ label: 'Crear rol' })
    else if (pathname.endsWith('/edit')) base.push({ label: 'Editar rol' })
    else if (pathname !== '/admin/roles') {
      const roleCode = decodeURIComponent(segments[2] ?? '').toUpperCase()
      base.push({ label: roleCode === 'ADMINISTRATOR' ? 'Ver acceso' : 'Ver rol' })
    }
    return base
  }
  if (pathname.startsWith('/admin/talent-bank')) {
    const base: Crumb[] = [{ label: 'Administración' }, { label: 'Talent Bank', to: '/admin/talent-bank' }]
    if (pathname === '/admin/talent-bank/new') base.push({ label: 'Nuevo talento' })
    else if (pathname.endsWith('/new/academy')) base.push({ label: 'Academia' })
    else if (pathname.endsWith('/new/prospect')) base.push({ label: 'Prospecto' })
    else if (pathname.endsWith('/edit')) base.push({ label: 'Editar' })
    else if (pathname.endsWith('/convert')) base.push({ label: 'Convertir a colaborador' })
    else if (pathname !== '/admin/talent-bank') base.push({ label: 'Detalle' })
    return base
  }
  if (pathname.startsWith('/admin/collaborators')) {
    const base: Crumb[] = [{ label: 'Administración' }, { label: 'Colaboradores', to: '/admin/collaborators' }]
    if (pathname.endsWith('/new')) base.push({ label: 'Nuevo colaborador' })
    else if (pathname.endsWith('/edit')) base.push({ label: 'Editar' })
    else if (pathname.endsWith('/manage')) base.push({ label: 'Gestión' })
    else if (pathname.endsWith('/certifications')) base.push({ label: 'Certificaciones' })
    else if (pathname !== '/admin/collaborators') base.push({ label: 'Detalle' })
    return base
  }
  if (pathname.startsWith('/admin/organizations')) {
    const base: Crumb[] = [{ label: 'Administración' }, { label: 'Organizaciones', to: '/admin/organizations' }]
    if (pathname.endsWith('/new')) base.push({ label: 'Nueva organización' })
    else if (pathname.endsWith('/edit')) base.push({ label: 'Editar' })
    else if (pathname.endsWith('/manage')) base.push({ label: 'Gestión' })
    else if (pathname !== '/admin/organizations') base.push({ label: 'Detalle' })
    return base
  }
  if (pathname.startsWith('/admin/users')) {
    const base: Crumb[] = [{ label: 'Administración' }, { label: 'Usuarios', to: '/admin/users' }]
    if (pathname.endsWith('/new')) base.push({ label: 'Nuevo usuario' })
    else if (pathname.endsWith('/edit')) base.push({ label: 'Editar' })
    else if (pathname.endsWith('/manage')) base.push({ label: 'Gestión' })
    else if (pathname !== '/admin/users') base.push({ label: 'Detalle' })
    return base
  }
  if (pathname.startsWith('/admin/catalogs')) {
    const segments = pathname.split('/').filter(Boolean)
    const type = segments[2]?.toUpperCase()
    const base: Crumb[] = [
      { label: 'Administración' },
      { label: 'Catálogos', to: '/admin/catalogs' }
    ]
    if (type && catalogLabels[type]) {
      base.push({ label: catalogLabels[type], to: `/admin/catalogs/${type}` })
    }
    if (pathname.endsWith('/new')) base.push({ label: 'Crear' })
    else if (pathname.endsWith('/edit')) base.push({ label: 'Editar' })
    else if (pathname.endsWith('/manage')) base.push({ label: 'Gestión' })
    else if (segments.length > 3) base.push({ label: 'Ver' })
    return base
  }
  if (pathname.startsWith('/admin/collections')) {
    const base: Crumb[] = [
      { label: 'Banco de Preguntas' },
      { label: 'Colecciones', to: '/admin/collections' }
    ]
    if (pathname.endsWith('/new')) base.push({ label: 'Nueva colección' })
    else if (pathname !== '/admin/collections') base.push({ label: 'Configuración' })
    return base
  }
  if (pathname.startsWith('/admin/forms')) {
    const base: Crumb[] = [
      { label: 'Banco de Preguntas' },
      { label: 'Formularios', to: '/admin/forms' }
    ]
    if (pathname.endsWith('/new')) base.push({ label: 'Nuevo formulario' })
    else if (pathname.endsWith('/edit')) base.push({ label: 'Editar' })
    return base
  }
  if (pathname.startsWith('/admin/questions')) {
    const base: Crumb[] = [
      { label: 'Banco de Preguntas' },
      { label: 'Preguntas', to: '/admin/questions' }
    ]
    if (pathname.endsWith('/new')) base.push({ label: 'Nueva pregunta' })
    else if (pathname.endsWith('/edit')) base.push({ label: 'Editar' })
    else if (pathname !== '/admin/questions') base.push({ label: 'Detalle' })
    return base
  }

  return [{ label: 'Valtieris' }]
}

export function Breadcrumbs() {
  const location = useLocation()
  const crumbs = resolveCrumbs(location.pathname)
  return (
    <nav className="breadcrumbs" aria-label="Ruta de navegación">
      {crumbs.map((crumb, index) => (
        <span className="breadcrumb-item" key={`${crumb.label}-${index}`}>
          {index > 0 && <Icon name="chevronRight" size={13} />}
          {crumb.to && index < crumbs.length - 1 ? (
            <Link to={crumb.to}>{crumb.label}</Link>
          ) : (
            <span aria-current={index === crumbs.length - 1 ? 'page' : undefined}>
              {crumb.label}
            </span>
          )}
        </span>
      ))}
    </nav>
  )
}
