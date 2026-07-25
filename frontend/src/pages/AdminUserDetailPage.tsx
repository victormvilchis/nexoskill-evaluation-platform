import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  activateUser,
  deleteUser,
  getRoles,
  getUser,
  resetUserPassword,
  restoreUser,
  suspendUser,
  updateUserAccess,
  updateUserProfile,
  updateUserRole
} from '../features/users/api/userApi'
import { useAuth } from '../features/authentication/context/AuthContext'
import { ApiRequestError } from '../shared/api/apiClient'
import { ConfirmDialog } from '../shared/components/ConfirmDialog'
import { useToast } from '../shared/components/ToastProvider'
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

interface PendingConfirmation {
  key: string
  title: string
  description: string
  confirmLabel: string
  tone?: 'primary' | 'danger'
  action: () => Promise<void>
}

export function AdminUserDetailPage() {
  const { publicId = '' } = useParams()
  const { user: currentUser } = useAuth()
  const toast = useToast()
  const [user, setUser] = useState<AdminUser | null>(null)
  const [roles, setRoles] = useState<RoleOption[]>([])
  const [loading, setLoading] = useState(true)
  const [busyAction, setBusyAction] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [confirmation, setConfirmation] = useState<PendingConfirmation | null>(null)

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
    try {
      const response = await action()
      synchronize(response)
      toast.success(successMessage)
    } catch (requestError) {
      const message = errorMessage(requestError, 'No fue posible completar la operación.')
      setError(message)
      toast.error('Operación no completada', message)
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

  function requestConfirmation(value: PendingConfirmation) {
    setConfirmation(value)
  }

  async function executeConfirmation() {
    if (!confirmation) return
    await confirmation.action()
    setConfirmation(null)
  }

  function handleAccessSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    requestConfirmation({
      key: 'access',
      title: 'Actualizar vigencia',
      description: 'El nuevo periodo se aplicará inmediatamente. Si la vigencia ya terminó, la sesión del usuario será bloqueada en su siguiente validación.',
      confirmLabel: 'Guardar vigencia',
      action: () => runAction(
        'access',
        () => updateUserAccess(publicId, {
          startsAt: new Date(startsAt).toISOString(),
          expiresAt: withoutExpiration ? null : new Date(expiresAt).toISOString()
        }),
        'La vigencia fue actualizada.'
      )
    })
  }

  function handleRoleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    requestConfirmation({
      key: 'role',
      title: 'Cambiar rol',
      description: `Se asignará el rol ${roleCode}. Las sesiones activas del usuario se cerrarán para aplicar los permisos nuevos.`,
      confirmLabel: 'Cambiar rol',
      action: () => runAction(
        'role',
        () => updateUserRole(publicId, { roleCode }),
        'El rol fue actualizado.'
      )
    })
  }

  function handleSuspend() {
    if (isCurrentUser) return
    requestConfirmation({
      key: 'suspend',
      title: 'Suspender usuario',
      description: 'El usuario perderá el acceso y todas sus sesiones activas se cerrarán inmediatamente.',
      confirmLabel: 'Suspender usuario',
      tone: 'danger',
      action: () => runAction(
        'suspend',
        () => suspendUser(publicId),
        'El usuario fue suspendido y sus sesiones fueron invalidadas.'
      )
    })
  }

  function handleActivate() {
    requestConfirmation({
      key: 'activate',
      title: 'Activar usuario',
      description: 'La cuenta volverá a estar disponible siempre que su periodo de vigencia también esté activo.',
      confirmLabel: 'Activar usuario',
      action: () => runAction(
        'activate',
        () => activateUser(publicId),
        'El usuario fue activado.'
      )
    })
  }

  function handleDelete() {
    if (isCurrentUser) return
    requestConfirmation({
      key: 'delete',
      title: 'Eliminar usuario',
      description: 'La cuenta desaparecerá de la lista normal, perderá el acceso y sus sesiones se cerrarán. El registro se conservará.',
      confirmLabel: 'Eliminar usuario',
      tone: 'danger',
      action: () => runAction(
        'delete',
        () => deleteUser(publicId, 'Eliminación administrativa'),
        'El usuario fue eliminado lógicamente.'
      )
    })
  }

  function handleRestore() {
    requestConfirmation({
      key: 'restore',
      title: 'Restaurar usuario',
      description: 'El usuario volverá como suspendido. Deberás activarlo explícitamente antes de que pueda iniciar sesión.',
      confirmLabel: 'Restaurar usuario',
      action: () => runAction(
        'restore',
        () => restoreUser(publicId),
        'El usuario fue restaurado como suspendido.'
      )
    })
  }

  function handlePasswordReset(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (temporaryPassword !== confirmPassword) {
      const message = 'La confirmación de contraseña no coincide.'
      setError(message)
      toast.warning('Revisa la contraseña', message)
      return
    }
    requestConfirmation({
      key: 'password',
      title: 'Restablecer contraseña',
      description: 'Se establecerá una contraseña temporal y todas las sesiones activas del usuario se cerrarán.',
      confirmLabel: 'Restablecer contraseña',
      tone: 'danger',
      action: async () => {
        await runAction(
          'password',
          () => resetUserPassword(publicId, { temporaryPassword }),
          'La contraseña temporal fue restablecida y las sesiones fueron invalidadas.'
        )
        setTemporaryPassword('')
        setConfirmPassword('')
      }
    })
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
        <section className={`management-section ${user.status === 'DELETED' ? 'deleted-management-section' : ''}`}>
          <div>
            <h2>Estado de la cuenta</h2>
            <p className="muted">
              {user.status === 'DELETED'
                ? 'La cuenta está eliminada lógicamente y no puede acceder a la plataforma.'
                : 'Suspender es reversible. Eliminar oculta la cuenta y conserva el registro.'}
            </p>
          </div>
          <div className="inline-actions">
            {user.status === 'DELETED' ? (
              <button className="primary-button" type="button" disabled={busyAction !== null} onClick={handleRestore}>
                {busyAction === 'restore' ? 'Restaurando…' : 'Restaurar usuario'}
              </button>
            ) : (
              <>
                {user.status === 'SUSPENDED' ? (
                  <button className="primary-button" type="button" disabled={busyAction !== null} onClick={handleActivate}>
                    {busyAction === 'activate' ? 'Activando…' : 'Activar usuario'}
                  </button>
                ) : (
                  <button className="secondary-button" type="button" disabled={busyAction !== null || isCurrentUser}
                    title={isCurrentUser ? 'No puedes suspender tu propia cuenta.' : undefined} onClick={handleSuspend}>
                    {busyAction === 'suspend' ? 'Suspendiendo…' : 'Suspender usuario'}
                  </button>
                )}
                <button className="danger-button" type="button" disabled={busyAction !== null || isCurrentUser}
                  title={isCurrentUser ? 'No puedes eliminar tu propia cuenta.' : undefined} onClick={handleDelete}>
                  {busyAction === 'delete' ? 'Eliminando…' : 'Eliminar usuario'}
                </button>
              </>
            )}
          </div>
          {isCurrentUser && user.status !== 'DELETED' && (
            <small className="field-help">No puedes suspender ni eliminar tu propia cuenta.</small>
          )}
        </section>
      )}

      {user.status !== 'DELETED' && <div className="management-grid">
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
      </div>}

      <ConfirmDialog
        open={confirmation !== null}
        title={confirmation?.title ?? ''}
        description={confirmation?.description ?? ''}
        confirmLabel={confirmation?.confirmLabel ?? 'Confirmar'}
        tone={confirmation?.tone}
        busy={confirmation !== null && busyAction === confirmation.key}
        onCancel={() => {
          if (busyAction === null) setConfirmation(null)
        }}
        onConfirm={() => void executeConfirmation()}
      />
    </main>
  )
}
