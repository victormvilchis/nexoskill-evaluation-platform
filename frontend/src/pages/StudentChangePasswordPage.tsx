import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { changeStudentPassword } from '../features/students/api/studentApi'
import { useStudentAuth } from '../features/students/context/StudentAuthContext'
import { ApiRequestError } from '../shared/api/apiClient'
import { BrandLogo } from '../shared/components/BrandLogo'

export function StudentChangePasswordPage() {
  const navigate = useNavigate()
  const { refresh } = useStudentAuth()
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await changeStudentPassword(currentPassword, newPassword, confirmPassword)
      await refresh()
      navigate('/student-login?passwordChanged=1', { replace: true })
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError ? requestError.message : 'No fue posible cambiar la contraseña.')
    } finally { setSubmitting(false) }
  }

  return (
    <main className="login-page student-login-page">
      <section className="login-brand"><div className="login-brand-logo-panel"><BrandLogo className="login-brand-logo" /></div><p className="eyebrow">Seguridad</p><h1>Crea una contraseña personal</h1><p className="login-description">El cambio revocará la sesión actual en Valtieris Talent Platform y tendrás que iniciar sesión nuevamente.</p></section>
      <section className="login-card"><h2>Cambiar contraseña</h2><form className="login-form" onSubmit={submit}>
        <div className="form-field"><label htmlFor="currentPassword">Contraseña temporal</label><input id="currentPassword" type="password" value={currentPassword} required maxLength={128} onChange={(e) => setCurrentPassword(e.target.value)} /></div>
        <div className="form-field"><label htmlFor="newPassword">Nueva contraseña</label><input id="newPassword" type="password" value={newPassword} required minLength={10} maxLength={128} onChange={(e) => setNewPassword(e.target.value)} /></div>
        <div className="form-field"><label htmlFor="confirmPassword">Confirmar contraseña</label><input id="confirmPassword" type="password" value={confirmPassword} required minLength={10} maxLength={128} onChange={(e) => setConfirmPassword(e.target.value)} /></div>
        {error && <div className="error-message">{error}</div>}
        <button className="primary-button" disabled={submitting}>{submitting ? 'Guardando…' : 'Cambiar contraseña'}</button>
      </form></section>
    </main>
  )
}
