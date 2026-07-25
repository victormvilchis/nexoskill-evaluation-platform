import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getWelcomeDashboard } from '../features/dashboard/api/dashboardApi'
import { useAuth } from '../features/authentication/context/AuthContext'
import { ApiRequestError } from '../shared/api/apiClient'
import { Icon, type IconName } from '../shared/components/Icon'
import { useToast } from '../shared/components/ToastProvider'
import type { WelcomeDashboard } from '../shared/types/dashboard'

interface QuickAction {
  title: string
  description: string
  to: string
  icon: IconName
  permission: string
}

const quickActions: QuickAction[] = [
  {
    title: 'Usuarios',
    description: 'Administra accesos, vigencias y roles.',
    to: '/admin/users',
    icon: 'users',
    permission: 'USER_VIEW'
  },
  {
    title: 'Banco de preguntas',
    description: 'Consulta y crea contenido para evaluaciones.',
    to: '/admin/questions',
    icon: 'questions',
    permission: 'QUESTION_VIEW'
  },
  {
    title: 'Categorías',
    description: 'Organiza el contenido por área de conocimiento.',
    to: '/admin/question-categories',
    icon: 'categories',
    permission: 'QUESTION_CATEGORY_MANAGE'
  },
  {
    title: 'Mi perfil',
    description: 'Actualiza tus datos y seguridad.',
    to: '/profile',
    icon: 'profile',
    permission: 'PROFILE_VIEW'
  }
]

function formatExpiration(value: string | null | undefined) {
  if (!value) return 'Sin vencimiento'
  return new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium' }).format(new Date(value))
}

export function DashboardPage() {
  const { user } = useAuth()
  const toast = useToast()
  const [dashboard, setDashboard] = useState<WelcomeDashboard | null>(null)

  useEffect(() => {
    let active = true
    getWelcomeDashboard()
      .then((response) => {
        if (active) setDashboard(response)
      })
      .catch((requestError) => {
        if (!active) return
        toast.error(
          'No fue posible cargar el panel',
          requestError instanceof ApiRequestError
            ? requestError.message
            : 'Intenta nuevamente en unos momentos.'
        )
      })
    return () => { active = false }
  }, [toast])

  const actions = quickActions.filter((action) =>
    user?.permissions.includes(action.permission)
  )

  return (
    <main className="content-page dashboard-page">
      <section className="dashboard-welcome">
        <div>
          <span className="dashboard-kicker">
            {dashboard?.panelType === 'ADMIN' ? 'Panel administrativo' : 'Panel personal'}
          </span>
          <h1>{dashboard?.title ?? `Hola, ${user?.firstName ?? user?.displayName}`}</h1>
          <p>{dashboard?.message ?? 'Todo está listo para continuar.'}</p>
        </div>
        <div className="dashboard-account-summary">
          <span className="status-dot" aria-hidden="true" />
          <div>
            <small>Cuenta activa</small>
            <strong>Vigencia: {formatExpiration(user?.accessExpiresAt)}</strong>
          </div>
        </div>
      </section>

      <section className="dashboard-section" aria-labelledby="quick-actions-title">
        <div className="section-heading compact-section-heading">
          <div>
            <h2 id="quick-actions-title">Accesos rápidos</h2>
            <p className="muted">Continúa con las tareas más frecuentes.</p>
          </div>
        </div>
        <div className="quick-action-grid">
          {actions.map((action) => (
            <Link className="quick-action-card" key={action.to} to={action.to}>
              <span className="quick-action-icon"><Icon name={action.icon} size={20} /></span>
              <div>
                <strong>{action.title}</strong>
                <p>{action.description}</p>
              </div>
              <Icon className="quick-action-arrow" name="chevronRight" size={17} />
            </Link>
          ))}
        </div>
      </section>
    </main>
  )
}
