import { useEffect, useState } from 'react'
import { getWelcomeDashboard } from '../features/dashboard/api/dashboardApi'
import { ApiRequestError } from '../shared/api/apiClient'
import type { WelcomeDashboard } from '../shared/types/dashboard'
import { useAuth } from '../features/authentication/context/AuthContext'

export function DashboardPage() {
  const { user } = useAuth()
  const [dashboard, setDashboard] = useState<WelcomeDashboard | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true

    getWelcomeDashboard()
      .then((response) => {
        if (active) setDashboard(response)
      })
      .catch((requestError) => {
        if (!active) return
        setError(
          requestError instanceof ApiRequestError
            ? requestError.message
            : 'No fue posible cargar el panel.'
        )
      })

    return () => {
      active = false
    }
  }, [])

  return (
    <main className="dashboard-page">
      <section className="welcome-section">
        <div>
          <span className="role-badge">
            {dashboard?.panelType === 'ADMIN'
              ? 'Panel administrativo'
              : 'Panel de usuario'}
          </span>
          <h1>{dashboard?.title ?? `Bienvenido, ${user?.displayName}`}</h1>
          <p>
            {dashboard?.message ?? 'Preparando tu panel de bienvenida…'}
          </p>
        </div>
        <div className="session-card">
          <span>Estado de cuenta</span>
          <strong>Activa</strong>
          <small>{user?.email}</small>
        </div>
      </section>

      {error && (
        <div className="error-message dashboard-error" role="alert">
          {error}
        </div>
      )}

      <section aria-labelledby="modules-title">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Estructura preparada</p>
            <h2 id="modules-title">Módulos siguientes</h2>
          </div>
          <span className="phase-label">Próxima fase</span>
        </div>

        <div className="module-grid">
          {(dashboard?.modules ?? []).map((module) => (
            <article className="module-card" key={module.code}>
              <div className="module-icon" aria-hidden="true">
                {module.name.charAt(0)}
              </div>
              <h3>{module.name}</h3>
              <p>{module.description}</p>
              <span className="coming-soon">No disponible todavía</span>
            </article>
          ))}
        </div>
      </section>

      <section className="technical-summary">
        <div>
          <span>Autenticación</span>
          <strong>Cookie HttpOnly</strong>
        </div>
        <div>
          <span>Rol actual</span>
          <strong>{user?.roles.join(', ')}</strong>
        </div>
        <div>
          <span>Backend</span>
          <strong>Spring Boot + Oracle</strong>
        </div>
      </section>
    </main>
  )
}
