import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../features/authentication/context/AuthContext'
import {
  getOwnProfile,
  updateOwnProfile,
  type OwnProfile
} from '../features/profile/api/profileApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { FormActions } from '../shared/components/FormActions'
import { useToast } from '../shared/components/ToastProvider'

export function ProfilePage() {
  const { refresh } = useAuth()
  const toast = useToast()
  const [profile, setProfile] = useState<OwnProfile | null>(null)
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function synchronize(value: OwnProfile) {
    setProfile(value)
    setFirstName(value.firstName)
    setLastName(value.lastName)
    setDisplayName(value.displayName)
  }

  useEffect(() => {
    let active = true
    getOwnProfile()
      .then((response) => {
        if (active) synchronize(response.profile)
      })
      .catch((requestError) => {
        if (active) {
          setError(
            requestError instanceof ApiRequestError
              ? requestError.message
              : 'No fue posible cargar el perfil.'
          )
        }
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)

    try {
      const response = await updateOwnProfile({
        firstName,
        lastName,
        displayName: displayName || undefined
      })
      synchronize(response.profile)
      await refresh()
      toast.success('Perfil actualizado', 'Tus cambios se guardaron correctamente.')
    } catch (requestError) {
      setError(
        requestError instanceof ApiRequestError
          ? requestError.message
          : 'No fue posible actualizar el perfil.'
      )
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return <main className="content-page"><p>Cargando perfil…</p></main>
  }

  return (
    <main className="content-page">
      <div className="page-heading">
        <div>
          <p className="eyebrow">Mi cuenta</p>
          <h1>Perfil y seguridad</h1>
          <p className="muted">Administra tus datos personales y contraseña.</p>
        </div>
      </div>

      {error && <div className="error-message" role="alert">{error}</div>}

      <div className="management-grid profile-grid">
        <form className="management-section" onSubmit={(event) => void handleSubmit(event)}>
          <div>
            <h2>Datos personales</h2>
            <p className="muted">El correo solo puede cambiarlo un administrador.</p>
          </div>

          <div className="form-field form-wide">
            <label htmlFor="profile-email">Correo electrónico</label>
            <input id="profile-email" value={profile?.email ?? ''} disabled />
          </div>

          <div className="form-field">
            <label htmlFor="profile-first-name">Nombre</label>
            <input
              id="profile-first-name"
              value={firstName}
              onChange={(event) => setFirstName(event.target.value)}
              required
              maxLength={100}
            />
          </div>

          <div className="form-field">
            <label htmlFor="profile-last-name">Apellidos</label>
            <input
              id="profile-last-name"
              value={lastName}
              onChange={(event) => setLastName(event.target.value)}
              required
              maxLength={150}
            />
          </div>

          <div className="form-field form-wide">
            <label htmlFor="profile-display-name">Nombre visible</label>
            <input
              id="profile-display-name"
              value={displayName}
              onChange={(event) => setDisplayName(event.target.value)}
              maxLength={250}
            />
          </div>

          <FormActions className="form-wide">
            <button
              className="secondary-button"
              type="button"
              disabled={submitting || !profile}
              onClick={() => profile && synchronize(profile)}
            >
              Cancelar
            </button>
            <button className="primary-button" type="submit" disabled={submitting}>
              {submitting ? 'Guardando…' : 'Guardar cambios'}
            </button>
          </FormActions>
        </form>

        <section className="management-section security-panel">
          <div>
            <h2>Contraseña</h2>
            <p className="muted">
              Cambiarla revocará las demás sesiones activas de tu cuenta.
            </p>
          </div>
          <Link className="primary-button button-link" to="/change-password">
            Cambiar contraseña
          </Link>
        </section>
      </div>
    </main>
  )
}
