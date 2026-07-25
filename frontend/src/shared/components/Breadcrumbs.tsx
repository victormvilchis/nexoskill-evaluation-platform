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

  if (pathname.startsWith('/admin/users')) {
    const base: Crumb[] = [{ label: 'Administración' }, { label: 'Usuarios', to: '/admin/users' }]
    if (pathname.endsWith('/new')) base.push({ label: 'Nuevo usuario' })
    else if (pathname !== '/admin/users') base.push({ label: 'Detalle' })
    return base
  }

  if (pathname === '/admin/question-categories') {
    return [
      { label: 'Contenido' },
      { label: 'Banco de preguntas', to: '/admin/questions' },
      { label: 'Categorías' }
    ]
  }

  if (pathname.startsWith('/admin/questions')) {
    const base: Crumb[] = [
      { label: 'Contenido' },
      { label: 'Banco de preguntas', to: '/admin/questions' }
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
