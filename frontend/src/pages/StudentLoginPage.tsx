import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { useStudentAuth } from '../features/students/context/StudentAuthContext'
import { ApiRequestError } from '../shared/api/apiClient'
import { LoadingScreen } from '../shared/components/LoadingScreen'

export function StudentLoginPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { student, loading, login } = useStudentAuth()
  const [organizationCode, setOrganizationCode] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (loading) return <LoadingScreen />
  if (student) return <Navigate to={student.passwordChangeRequired ? '/student/change-password' : '/student'} replace />

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      const authenticated = await login(organizationCode.trim(), email.trim(), password)
      navigate(authenticated.passwordChangeRequired ? '/student/change-password' : '/student', { replace: true })
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError ? requestError.message : 'No fue posible iniciar sesión.')
    } finally { setSubmitting(false) }
  }

  return (
    <main className="login-page student-login-page">
      <section className="login-brand">
        <div className="brand-mark" aria-hidden="true">NS</div>
        <p className="eyebrow">Portal de estudiantes</p>
        <h1>Tu espacio de aprendizaje</h1>
        <p className="login-description">Acceso independiente y protegido para evaluaciones, colecciones y rutas asignadas.</p>
      </section>
      <section className="login-card" aria-labelledby="student-login-title">
        <p className="eyebrow">Estudiantes</p>
        <h2 id="student-login-title">Inicia sesión</h2>
        <p className="muted">Usa el código de tu organización y las credenciales proporcionadas.</p>
        {searchParams.get('passwordChanged') === '1' && (
          <div className="success-message" role="status">
            Contraseña actualizada. Inicia sesión nuevamente.
          </div>
        )}
        <form className="login-form" onSubmit={submit}>
          <div className="form-field"><label htmlFor="organizationCode">Código de organización</label><input id="organizationCode" value={organizationCode} maxLength={80} autoComplete="organization" required disabled={submitting} onChange={(e) => setOrganizationCode(e.target.value)} /></div>
          <div className="form-field"><label htmlFor="studentEmail">Correo electrónico</label><input id="studentEmail" type="email" value={email} maxLength={254} autoComplete="username" required disabled={submitting} onChange={(e) => setEmail(e.target.value)} /></div>
          <div className="form-field"><label htmlFor="studentPassword">Contraseña</label><input id="studentPassword" type="password" value={password} maxLength={128} autoComplete="current-password" required disabled={submitting} onChange={(e) => setPassword(e.target.value)} /></div>
          {error && <div className="error-message" role="alert">{error}</div>}
          <button className="primary-button" type="submit" disabled={submitting}>{submitting ? 'Ingresando…' : 'Iniciar sesión'}</button>
        </form>
        <Link className="student-internal-login-link" to="/login">Acceso para personal interno</Link>
      </section>
    </main>
  )
}
