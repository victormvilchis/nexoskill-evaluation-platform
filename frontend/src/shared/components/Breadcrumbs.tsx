import { Link, useLocation } from 'react-router-dom'
import { Icon } from './Icon'

interface Crumb {
  label: string
  to?: string
}

function resolveCrumbs(pathname: string): Crumb[] {
  if (pathname === '/dashboard') return [{ label: 'Inicio' }]
  if (pathname === '/profile') return [{ label: 'Cuenta' }, { label: 'Perfil' }]
  if (pathname === '/change-password') return [{ label: 'Cuenta' }, { label: 'Contraseña' }]

  if (pathname.startsWith('/admin/students')) {
    const base: Crumb[] = [{ label: 'Administración' }, { label: 'Estudiantes', to: '/admin/students' }]
    if (pathname.endsWith('/new')) base.push({ label: 'Nuevo estudiante' })
    else if (pathname.endsWith('/edit')) base.push({ label: 'Editar' })
    else if (pathname !== '/admin/students') base.push({ label: 'Detalle' })
    return base
  }

  if (pathname.startsWith('/admin/organizations')) {
    const base: Crumb[] = [{ label: 'Administración' }, { label: 'Organizaciones', to: '/admin/organizations' }]
    if (pathname.endsWith('/new')) base.push({ label: 'Nueva organización' })
    else if (pathname.endsWith('/edit')) base.push({ label: 'Editar' })
    else if (pathname.endsWith('/manage')) base.push({ label: 'Administrar' })
    else if (pathname !== '/admin/organizations') base.push({ label: 'Detalle' })
    return base
  }

  if (pathname.startsWith('/admin/users')) {
    const base: Crumb[] = [{ label: 'Administración' }, { label: 'Usuarios', to: '/admin/users' }]
    if (pathname.endsWith('/new')) base.push({ label: 'Nuevo usuario' })
    else if (pathname !== '/admin/users') base.push({ label: 'Detalle' })
    return base
  }


  if (pathname.startsWith('/admin/catalogs')) {
    const base: Crumb[] = [{ label: 'Administración' }, { label: 'Catálogos', to: '/admin/catalogs' }]
    if (pathname !== '/admin/catalogs') base.push({ label: 'Administrar' })
    return base
  }

  if (pathname.startsWith('/admin/collections')) {
    const base: Crumb[] = [
      { label: 'Evaluaciones' },
      { label: 'Colecciones', to: '/admin/collections' }
    ]
    if (pathname.endsWith('/new')) base.push({ label: 'Nueva colección' })
    else if (pathname !== '/admin/collections') base.push({ label: 'Configuración' })
    return base
  }

  if (pathname === '/admin/question-categories') {
    return [
      { label: 'Banco de Preguntas' },
      { label: 'Preguntas', to: '/admin/questions' },
      { label: 'Categorías' }
    ]
  }

  if (pathname === '/admin/question-technologies') {
    return [
      { label: 'Banco de Preguntas' },
      { label: 'Preguntas', to: '/admin/questions' },
      { label: 'Tecnologías' }
    ]
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

  return [{ label: 'NexoSkill' }]
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
