import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { changePassword } from '../features/authentication/api/authApi'
import { useAuth } from '../features/authentication/context/AuthContext'
import { ApiRequestError } from '../shared/api/apiClient'
import { useToast } from '../shared/components/ToastProvider'

export function ChangePasswordPage() {
  const navigate = useNavigate()
  const { user, logout, refresh } = useAuth()
  const toast = useToast()
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)

    if (newPassword !== confirmPassword) {
      setError('La confirmación de la contraseña no coincide.')
      return
    }

    setSubmitting(true)
    try {
      await changePassword({
        currentPassword,
        newPassword,
        confirmPassword
      })
      await refresh()
      toast.success('Contraseña actualizada', 'Inicia sesión nuevamente con tu contraseña definitiva.')
      navigate('/login?reason=password-changed', { replace: true })
    } catch (requestError) {
      setError(
        requestError instanceof ApiRequestError
          ? requestError.message
          : 'No fue posible cambiar la contraseña.'
      )
    } finally {
      setSubmitting(false)
    }
  }

  async function handleLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <main className="password-page">
      <section className="password-card" aria-labelledby="password-title">
        <div className="brand-mark password-mark" aria-hidden="true">NS</div>
        <p className="eyebrow">Seguridad de la cuenta</p>
        <h1 id="password-title">
          {user?.passwordChangeRequired
            ? 'Crea una nueva contraseña'
            : 'Cambia tu contraseña'}
        </h1>
        <p className="muted">
          {user?.passwordChangeRequired
            ? 'Debes reemplazar la contraseña temporal antes de continuar.'
            : 'Confirma tu contraseña actual y define una nueva.'}
        </p>

        <div className="password-policy" aria-label="Política de contraseña">
          <strong>La nueva contraseña debe incluir:</strong>
          <span>10 a 128 caracteres</span>
          <span>Mayúscula, minúscula, número y símbolo</span>
          <span>No contener el nombre de tu correo</span>
          <span>No coincidir con tus últimas contraseñas</span>
        </div>

        <form className="login-form" onSubmit={(event) => void handleSubmit(event)}>
          <div className="form-field">
            <label htmlFor="current-password">Contraseña actual</label>
            <input
              id="current-password"
              type="password"
              autoComplete="current-password"
              value={currentPassword}
              onChange={(event) => setCurrentPassword(event.target.value)}
              required
              maxLength={128}
              disabled={submitting}
            />
          </div>

          <div className="form-field">
            <label htmlFor="new-password">Nueva contraseña</label>
            <input
              id="new-password"
              type="password"
              autoComplete="new-password"
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
              required
              minLength={10}
              maxLength={128}
              disabled={submitting}
            />
          </div>

          <div className="form-field">
            <label htmlFor="confirm-password">Confirmar contraseña</label>
            <input
              id="confirm-password"
              type="password"
              autoComplete="new-password"
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
              required
              minLength={10}
              maxLength={128}
              disabled={submitting}
            />
          </div>

          {error && <div className="error-message" role="alert">{error}</div>}

          <button className="primary-button" type="submit" disabled={submitting}>
            {submitting ? 'Actualizando…' : 'Guardar nueva contraseña'}
          </button>
          <button
            className="secondary-button"
            type="button"
            disabled={submitting}
            onClick={() => void handleLogout()}
          >
            Cerrar sesión
          </button>
        </form>
      </section>
    </main>
  )
}
