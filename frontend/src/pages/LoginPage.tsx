import { Navigate } from 'react-router-dom'
import { LoginForm } from '../features/authentication/components/LoginForm'
import { useAuth } from '../features/authentication/context/AuthContext'
import { LoadingScreen } from '../shared/components/LoadingScreen'

export function LoginPage() {
  const { user, loading } = useAuth()

  if (loading) {
    return <LoadingScreen />
  }

  if (user) {
    return <Navigate to="/dashboard" replace />
  }

  return (
    <main className="login-page">
      <section className="login-brand">
        <div className="brand-mark" aria-hidden="true">NS</div>
        <p className="eyebrow">NexoSkill Consulting</p>
        <h1>Plataforma de evaluaciones</h1>
        <p className="login-description">
          Base segura para administrar usuarios y aplicar evaluaciones
          profesionales.
        </p>
      </section>

      <section className="login-card" aria-labelledby="login-title">
        <p className="eyebrow">Acceso</p>
        <h2 id="login-title">Inicia sesión</h2>
        <p className="muted">
          Ingresa con la cuenta configurada en el backend.
        </p>
        <LoginForm />
        <div className="demo-credentials">
          <strong>Ambiente local</strong>
          <span>Administrador: admin@nexoskill.local</span>
          <span>Usuario: usuario@nexoskill.local</span>
        </div>
      </section>
    </main>
  )
}
