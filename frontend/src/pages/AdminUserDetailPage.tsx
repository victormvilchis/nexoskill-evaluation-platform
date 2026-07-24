import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  activateUser,
  getRoles,
  getUser,
  resetUserPassword,
  suspendUser,
  updateUserAccess,
  updateUserProfile,
  updateUserRole
} from '../features/users/api/userApi'
import { useAuth } from '../features/authentication/context/AuthContext'
import { ApiRequestError } from '../shared/api/apiClient'
import type { AdminUser, RoleOption } from '../shared/types/users'

function toLocalInputValue(value: string) {
  const date = new Date(value)
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 16)
}

function formatDate(value: string | null) {
  if (!value) return 'Sin vencimiento'
  return new Intl.DateTimeFormat('es-MX', {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(new Date(value))
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof ApiRequestError ? error.message : fallback
}

export function AdminUserDetailPage() {
  const { publicId = '' } = useParams()
  const { user: currentUser } = useAuth()
  const [user, setUser] = useState<AdminUser | null>(null)
  const [roles, setRoles] = useState<RoleOption[]>([])
  const [loading, setLoading] = useState(true)
  const [busyAction, setBusyAction] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const [email, setEmail] = useState('')
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [startsAt, setStartsAt] = useState('')
  const [expiresAt, setExpiresAt] = useState('')
  const [withoutExpiration, setWithoutExpiration] = useState(false)
  const [roleCode, setRoleCode] = useState('')
  const [temporaryPassword, setTemporaryPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')

  const permissions = useMemo(
    () => new Set(currentUser?.permissions ?? []),
    [currentUser]
  )
  const isCurrentUser = currentUser?.publicId === publicId

  function synchronize(response: AdminUser) {
    setUser(response)
    setEmail(response.email)
    setFirstName(response.firstName)
    setLastName(response.lastName)
    setDisplayName(response.displayName)
    setStartsAt(toLocalInputValue(response.startsAt))
    setWithoutExpiration(response.expiresAt === null)
    setExpiresAt(
      response.expiresAt
        ? toLocalInputValue(response.expiresAt)
        : ''
    )
    setRoleCode(response.roles[0] ?? '')
  }

  useEffect(() => {
    let active = true
    Promise.all([
      getUser(publicId),
      permissions.has('USER_ROLE_ASSIGN') ? getRoles() : Promise.resolve([])
    ])
      .then(([response, roleOptions]) => {
        if (!active) return
        synchronize(response)
        setRoles(roleOptions)
      })
      .catch((requestError) => {
        if (active) {
          setError(errorMessage(
            requestError,
            'No fue posible consultar el usuario.'
          ))
        }
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [publicId, permissions])

  async function runAction(
    key: string,
    action: () => Promise<AdminUser>,
    successMessage: string
  ) {
    setBusyAction(key)
    setError(null)
    setMessage(null)
    try {
      const response = await action()
      synchronize(response)
      setMessage(successMessage)
    } catch (requestError) {
      setError(errorMessage(requestError, 'No fue posible completar la operación.'))
    } finally {
      setBusyAction(null)
    }
  }

  async function handleProfileSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    await runAction(
      'profile',
      () => updateUserProfile(publicId, {
        email,
        firstName,
        lastName,
        displayName: displayName || undefined
      }),
      'Los datos generales fueron actualizados.'
    )
  }

  async function handleAccessSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const confirmation = window.confirm(
      '¿Confirmas la modificación de la vigencia del usuario?'
    )
    if (!confirmation) return

    await runAction(
      'access',
      () => updateUserAccess(publicId, {
        startsAt: new Date(startsAt).toISOString(),
        expiresAt: withoutExpiration ? null : new Date(expiresAt).toISOString()
      }),
      'La vigencia fue actualizada.'
    )
  }

  async function handleRoleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const confirmation = window.confirm(
      `¿Confirmas asignar el rol ${roleCode} a este usuario?`
    )
    if (!confirmation) return

    await runAction(
      'role',
      () => updateUserRole(publicId, { roleCode }),
      'El rol fue actualizado.'
    )
  }

  async function handleSuspend() {
    if (isCurrentUser) return
    const confirmation = window.confirm(
      '¿Confirmas suspender al usuario? Sus sesiones activas se cerrarán inmediatamente.'
    )
    if (!confirmation) return
    await runAction(
      'suspend',
      () => suspendUser(publicId),
      'El usuario fue suspendido y sus sesiones fueron invalidadas.'
    )
  }

  async function handleActivate() {
    const confirmation = window.confirm('¿Confirmas activar al usuario?')
    if (!confirmation) return
    await runAction(
      'activate',
      () => activateUser(publicId),
      'El usuario fue activado.'
    )
  }

  async function handlePasswordReset(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (temporaryPassword !== confirmPassword) {
      setError('La confirmación de contraseña no coincide.')
      return
    }
    const confirmation = window.confirm(
      '¿Confirmas restablecer la contraseña? Todas las sesiones del usuario se cerrarán.'
    )
    if (!confirmation) return

    await runAction(
      'password',
      () => resetUserPassword(publicId, { temporaryPassword }),
      'La contraseña temporal fue restablecida y las sesiones fueron invalidadas.'
    )
    setTemporaryPassword('')
    setConfirmPassword('')
  }

  if (loading) {
    return <main className="content-page"><p>Cargando usuario…</p></main>
  }

  if (!user) {
    return (
      <main className="content-page">
        <div className="error-message">
          {error ?? 'El usuario solicitado no está disponible.'}
        </div>
        <Link className="secondary-button button-link" to="/admin/users">
          Volver a usuarios
        </Link>
      </main>
    )
  }

  return (
    <main className="content-page">
      <div className="page-heading">
        <div>
          <p className="eyebrow">Administración de usuarios</p>
          <h1>{user.displayName}</h1>
          <p className="muted">{user.email}</p>
        </div>
        <Link className="secondary-button button-link" to="/admin/users">
          Volver
        </Link>
      </div>

      {message && <div className="success-message" role="status">{message}</div>}
      {error && <div className="error-message" role="alert">{error}</div>}

      <section className="user-overview-grid">
        <article className="summary-card">
          <span>Estado de cuenta</span>
          <strong>{user.status}</strong>
        </article>
        <article className="summary-card">
          <span>Estado de acceso</span>
          <strong>{user.accessStatus}</strong>
        </article>
        <article className="summary-card">
          <span>Rol</span>
          <strong>{user.roles.join(', ')}</strong>
        </article>
        <article className="summary-card">
          <span>Vencimiento</span>
          <strong>{formatDate(user.expiresAt)}</strong>
        </article>
      </section>

      {permissions.has('USER_STATUS_CHANGE') && (
        <section className="management-section">
          <div>
            <h2>Estado de la cuenta</h2>
            <p className="muted">
              Suspender invalida todas las sesiones activas de inmediato.
            </p>
          </div>
          <div className="inline-actions">
            {user.status === 'SUSPENDED' ? (
              <button
                className="primary-button"
                type="button"
                disabled={busyAction !== null}
                onClick={() => void handleActivate()}
              >
                {busyAction === 'activate' ? 'Activando…' : 'Activar usuario'}
              </button>
            ) : (
              <button
                className="danger-button"
                type="button"
                disabled={busyAction !== null || isCurrentUser}
                title={isCurrentUser ? 'No puedes suspender tu propia cuenta.' : undefined}
                onClick={() => void handleSuspend()}
              >
                {busyAction === 'suspend' ? 'Suspendiendo…' : 'Suspender usuario'}
              </button>
            )}
          </div>
          {isCurrentUser && (
            <small className="field-help">
              La plataforma impide que un administrador suspenda su propia cuenta.
            </small>
          )}
        </section>
      )}

      <div className="management-grid">
        {permissions.has('USER_UPDATE') && (
          <form className="management-section" onSubmit={(event) => void handleProfileSubmit(event)}>
            <div>
              <h2>Datos generales</h2>
              <p className="muted">Actualiza nombre, correo y nombre visible.</p>
            </div>
            <div className="form-field">
              <label htmlFor="detail-firstName">Nombre</label>
              <input id="detail-firstName" value={firstName} required onChange={(event) => setFirstName(event.target.value)} />
            </div>
            <div className="form-field">
              <label htmlFor="detail-lastName">Apellidos</label>
              <input id="detail-lastName" value={lastName} required onChange={(event) => setLastName(event.target.value)} />
            </div>
            <div className="form-field form-wide">
              <label htmlFor="detail-displayName">Nombre visible</label>
              <input id="detail-displayName" value={displayName} onChange={(event) => setDisplayName(event.target.value)} />
            </div>
            <div className="form-field form-wide">
              <label htmlFor="detail-email">Correo</label>
              <input id="detail-email" type="email" value={email} required onChange={(event) => setEmail(event.target.value)} />
            </div>
            <div className="form-actions form-wide">
              <button className="primary-button" type="submit" disabled={busyAction !== null}>
                {busyAction === 'profile' ? 'Guardando…' : 'Guardar datos'}
              </button>
            </div>
          </form>
        )}

        {permissions.has('USER_ACCESS_MANAGE') && (
          <form className="management-section" onSubmit={(event) => void handleAccessSubmit(event)}>
            <div>
              <h2>Vigencia</h2>
              <p className="muted">
                Una fecha vencida cerrará la sesión del usuario automáticamente.
              </p>
            </div>
            <div className="form-field">
              <label htmlFor="detail-startsAt">Inicio</label>
              <div className="date-input-wrapper">
                <input id="detail-startsAt" type="datetime-local" value={startsAt} required onChange={(event) => setStartsAt(event.target.value)} />
              </div>
            </div>
            <div className="form-field">
              <label htmlFor="detail-expiresAt">Vencimiento</label>
              <div className="date-input-wrapper">
                <input id="detail-expiresAt" type="datetime-local" value={expiresAt} disabled={withoutExpiration} required={!withoutExpiration} onChange={(event) => setExpiresAt(event.target.value)} />
              </div>
            </div>
            <label className="checkbox-row form-wide">
              <input type="checkbox" checked={withoutExpiration} onChange={(event) => setWithoutExpiration(event.target.checked)} />
              Acceso sin fecha de vencimiento
            </label>
            <div className="form-actions form-wide">
              <button className="primary-button" type="submit" disabled={busyAction !== null}>
                {busyAction === 'access' ? 'Guardando…' : 'Guardar vigencia'}
              </button>
            </div>
          </form>
        )}

        {permissions.has('USER_ROLE_ASSIGN') && (
          <form className="management-section" onSubmit={(event) => void handleRoleSubmit(event)}>
            <div>
              <h2>Rol</h2>
              <p className="muted">Modifica los permisos funcionales del usuario.</p>
            </div>
            <div className="form-field form-wide">
              <label htmlFor="detail-role">Rol asignado</label>
              <select id="detail-role" value={roleCode} onChange={(event) => setRoleCode(event.target.value)}>
                {roles.map((role) => (
                  <option key={role.code} value={role.code}>{role.name}</option>
                ))}
              </select>
            </div>
            <div className="form-actions form-wide">
              <button className="primary-button" type="submit" disabled={busyAction !== null}>
                {busyAction === 'role' ? 'Guardando…' : 'Guardar rol'}
              </button>
            </div>
          </form>
        )}

        {permissions.has('USER_PASSWORD_RESET') && (
          <form className="management-section" onSubmit={(event) => void handlePasswordReset(event)}>
            <div>
              <h2>Restablecer contraseña</h2>
              <p className="muted">
                Define una contraseña temporal. No se mostrará ni se almacenará en texto plano.
              </p>
            </div>
            <div className="form-field form-wide">
              <label htmlFor="temporaryPassword">Contraseña temporal</label>
              <input id="temporaryPassword" type="password" minLength={10} value={temporaryPassword} required onChange={(event) => setTemporaryPassword(event.target.value)} />
            </div>
            <div className="form-field form-wide">
              <label htmlFor="confirmPassword">Confirmar contraseña</label>
              <input id="confirmPassword" type="password" minLength={10} value={confirmPassword} required onChange={(event) => setConfirmPassword(event.target.value)} />
            </div>
            <small className="field-help form-wide">
              Mínimo 10 caracteres, mayúscula, minúscula, número y símbolo.
            </small>
            <div className="form-actions form-wide">
              <button className="danger-button" type="submit" disabled={busyAction !== null}>
                {busyAction === 'password' ? 'Restableciendo…' : 'Restablecer contraseña'}
              </button>
            </div>
          </form>
        )}
      </div>
    </main>
  )
}
