import { Link, Navigate, useSearchParams } from 'react-router-dom'
import { LoginForm } from '../features/authentication/components/LoginForm'
import { useAuth } from '../features/authentication/context/AuthContext'
import { BrandLogo } from '../shared/components/BrandLogo'
import { LoadingScreen } from '../shared/components/LoadingScreen'

export function LoginPage() {
  const { user, loading } = useAuth()
  const [searchParams] = useSearchParams()
  const reason = searchParams.get('reason')

  if (loading) {
    return <LoadingScreen />
  }

  if (user) {
    return (
      <Navigate
        to={user.passwordChangeRequired ? '/change-password' : '/dashboard'}
        replace
      />
    )
  }

  return (
    <main className="login-page">
      <section className="login-brand">
        <div className="login-brand-logo-panel">
          <BrandLogo className="login-brand-logo" />
        </div>
        <p className="eyebrow">Valtieris Talent Platform</p>
        <h1>Evaluación y desarrollo de talento</h1>
        <p className="login-description">
          Administra colaboradores, certificaciones y evaluaciones profesionales
          desde una experiencia segura y centralizada.
        </p>
      </section>

      <section className="login-card" aria-labelledby="login-title">
        <p className="eyebrow">Acceso</p>
        <h2 id="login-title">Inicia sesión en Valtieris Talent Platform</h2>
        <p className="muted">
          Ingresa con la cuenta configurada en la plataforma.
        </p>
        {reason === 'password-changed' && (
          <div className="success-message" role="status">
            Tu contraseña fue actualizada. Inicia sesión nuevamente.
          </div>
        )}
        {reason === 'inactive' && (
          <div className="error-message" role="alert">
            Tu cuenta se encuentra inactiva. Contacta a un administrador.
          </div>
        )}
        {reason === 'suspended' && (
          <div className="error-message" role="alert">
            Tu acceso se encuentra suspendido. Contacta a un administrador.
          </div>
        )}
        {reason === 'organization-inactive' && (
          <div className="error-message" role="alert">
            La organización asociada a tu cuenta se encuentra inactiva.
          </div>
        )}
        {reason === 'organization-expired' && (
          <div className="error-message" role="alert">
            La organización asociada a tu cuenta ya no se encuentra vigente.
          </div>
        )}
        {reason === 'expired' && (
          <div className="error-message" role="alert">
            Tu acceso a la plataforma ha expirado. Solicita una nueva vigencia
            al administrador.
          </div>
        )}
        {reason === 'temporary-password-expired' && (
          <div className="error-message" role="alert">
            La contraseña temporal expiró. Solicita al administrador una nueva.
          </div>
        )}
        {reason === 'session' && (
          <div className="error-message" role="alert">
            Tu sesión dejó de estar disponible. Inicia sesión nuevamente.
          </div>
        )}
        <LoginForm />
        <Link className="student-internal-login-link" to="/student-login">Acceso para colaboradores</Link>
      </section>
    </main>
  )
}
