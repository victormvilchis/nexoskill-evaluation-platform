import { useEffect, useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useAuth } from '../features/authentication/context/AuthContext'
import {
  activateUser,
  deactivateUser,
  deleteUser,
  getUser,
  getUserSessions,
  getUserStatusHistory,
  resetUserPassword,
  restoreUser,
  revokeUserSessions,
  suspendUser
} from '../features/users/api/userApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { BackButton } from '../shared/components/BackButton'
import { ConfirmDialog } from '../shared/components/ConfirmDialog'
import { Icon } from '../shared/components/Icon'
import { LoadingScreen } from '../shared/components/LoadingScreen'
import { useToast } from '../shared/components/ToastProvider'
import type {
  AdminUser,
  InternalUserSession,
  InternalUserStatusHistory,
  UserStatus
} from '../shared/types/users'

type AdministrativeAction = 'ACTIVATE' | 'DEACTIVATE' | 'SUSPEND' | 'DELETE' | 'RESTORE' | 'REVOKE' | 'RESET_PASSWORD'

const statusLabels: Record<UserStatus, string> = {
  ACTIVE: 'Activo',
  INACTIVE: 'Inactivo',
  SUSPENDED: 'Suspendido',
  DELETED: 'Eliminado'
}

const roleLabels: Record<string, string> = {
  ADMINISTRATOR: 'Administrador',
  MANAGER: 'Gestor',
  SUPERVISOR: 'Supervisor'
}

const actionCopy: Record<AdministrativeAction, { title: string; description: string; confirm: string; danger?: boolean }> = {
  ACTIVATE: {
    title: 'Activar usuario',
    description: 'El usuario podrá iniciar sesión cuando su organización y vigencia también estén activas. La acción quedará auditada.',
    confirm: 'Activar usuario'
  },
  DEACTIVATE: {
    title: 'Inactivar usuario',
    description: 'El usuario perderá acceso inmediatamente, se cerrarán todas sus sesiones y conservará sus datos, historial y relaciones. Podrá reactivarse posteriormente.',
    confirm: 'Inactivar usuario',
    danger: true
  },
  SUSPEND: {
    title: 'Suspender usuario',
    description: 'El acceso se bloqueará inmediatamente por una causa administrativa o de seguridad. Todas las sesiones serán revocadas y el motivo quedará en auditoría.',
    confirm: 'Suspender usuario',
    danger: true
  },
  DELETE: {
    title: 'Eliminar usuario lógicamente',
    description: 'El usuario perderá acceso, dejará de aparecer en listados operativos y conservará su historial, auditoría y contenido. Para reutilizarlo deberá restaurarse y volverá como inactivo.',
    confirm: 'Eliminar usuario',
    danger: true
  },
  RESTORE: {
    title: 'Restaurar usuario',
    description: 'El usuario volverá como inactivo. No podrá iniciar sesión hasta que un Administrador lo active. Se validará el correo y la restauración quedará auditada.',
    confirm: 'Restaurar como inactivo'
  },
  REVOKE: {
    title: 'Revocar sesiones',
    description: 'Todas las sesiones activas del usuario se cerrarán. El estado de la cuenta no cambiará.',
    confirm: 'Revocar sesiones',
    danger: true
  },
  RESET_PASSWORD: {
    title: 'Generar contraseña temporal',
    description: 'Se invalidarán todas las sesiones y la contraseña actual. La nueva contraseña temporal se mostrará una sola vez y el usuario deberá cambiarla al iniciar sesión.',
    confirm: 'Generar contraseña',
    danger: true
  }
}

function formatDate(value: string | null, fallback = 'Sin registro') {
  if (!value) return fallback
  return new Intl.DateTimeFormat('es-MX', {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(new Date(value))
}

function sessionStatusLabel(value: string) {
  return value === 'ACTIVE' ? 'Activa' : value === 'REVOKED' ? 'Revocada' : 'Vencida'
}

export function AdminUserManagementPage() {
  const { publicId } = useParams()
  const { user: currentUser } = useAuth()
  const toast = useToast()
  const [user, setUser] = useState<AdminUser | null>(null)
  const [history, setHistory] = useState<InternalUserStatusHistory[]>([])
  const [sessions, setSessions] = useState<InternalUserSession[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState<AdministrativeAction | null>(null)
  const [reason, setReason] = useState('')
  const [deleteConfirmation, setDeleteConfirmation] = useState('')
  const [busy, setBusy] = useState(false)
  const [temporaryPassword, setTemporaryPassword] = useState<string | null>(null)

  const selfManagement = currentUser?.publicId === user?.publicId
  const activeSessionCount = useMemo(
    () => sessions.filter((session) => session.status === 'ACTIVE').length,
    [sessions]
  )

  async function load() {
    if (!publicId) return
    setLoading(true)
    setError(null)
    try {
      const [userResponse, historyResponse, sessionResponse] = await Promise.all([
        getUser(publicId),
        getUserStatusHistory(publicId),
        getUserSessions(publicId)
      ])
      setUser(userResponse)
      setHistory(historyResponse)
      setSessions(sessionResponse)
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError
        ? requestError.message
        : 'No fue posible cargar la administración del usuario.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void load() }, [publicId])

  function requestAction(action: AdministrativeAction) {
    setReason('')
    setDeleteConfirmation('')
    setPending(action)
  }

  async function executeAction() {
    if (!pending || !user) return
    if (pending === 'SUSPEND' && !reason.trim()) {
      toast.error('Motivo obligatorio', 'Indica el motivo de la suspensión.')
      return
    }
    if (pending === 'DELETE' && !reason.trim()) {
      toast.error('Motivo obligatorio', 'Indica el motivo de la eliminación.')
      return
    }
    if (pending === 'DELETE' && deleteConfirmation.trim().toLowerCase() !== user.email.toLowerCase()) {
      toast.error('Confirmación incorrecta', 'Escribe el correo completo del usuario para confirmar.')
      return
    }

    setBusy(true)
    try {
      if (pending === 'RESET_PASSWORD') {
        const response = await resetUserPassword(user.publicId)
        setUser(response.user)
        setTemporaryPassword(response.temporaryPassword)
        toast.success('Contraseña temporal generada', 'Cópiala ahora; no podrá consultarse nuevamente.')
      } else if (pending === 'REVOKE') {
        const response = await revokeUserSessions(user.publicId)
        toast.success('Sesiones revocadas', `${response.revokedSessions} sesión(es) fueron cerradas.`)
      } else {
        const updated = pending === 'ACTIVATE'
          ? await activateUser(user.publicId, { reason: reason.trim() || undefined })
          : pending === 'DEACTIVATE'
            ? await deactivateUser(user.publicId, { reason: reason.trim() || undefined })
            : pending === 'SUSPEND'
              ? await suspendUser(user.publicId, { reason: reason.trim() })
              : pending === 'DELETE'
                ? await deleteUser(user.publicId, { reason: reason.trim() })
                : await restoreUser(user.publicId, { reason: reason.trim() || undefined })
        setUser(updated)
        toast.success('Acción completada', `El usuario quedó como ${statusLabels[updated.status].toLowerCase()}.`)
      }
      setPending(null)
      setReason('')
      setDeleteConfirmation('')
      const [nextHistory, nextSessions] = await Promise.all([
        getUserStatusHistory(user.publicId),
        getUserSessions(user.publicId)
      ])
      setHistory(nextHistory)
      setSessions(nextSessions)
    } catch (requestError) {
      toast.error('No fue posible completar la acción', requestError instanceof ApiRequestError ? requestError.message : undefined)
    } finally {
      setBusy(false)
    }
  }

  async function copyTemporaryPassword() {
    if (!temporaryPassword) return
    await navigator.clipboard.writeText(temporaryPassword)
    toast.success('Contraseña copiada', 'Compártela mediante un canal seguro.')
  }

  if (loading) return <LoadingScreen />
  if (!user) {
    return <main className="content-page"><BackButton fallback="/admin/users" /><div className="error-message">{error ?? 'El usuario no está disponible.'}</div></main>
  }

  const pendingCopy = pending ? actionCopy[pending] : null

  return (
    <main className="content-page resource-page internal-user-management-page">
      <BackButton fallback="/admin/users" />
      <header className="ns-page-header">
        <div>
          <p className="eyebrow">Administración · Usuarios</p>
          <h1>Administrar usuario</h1>
          <p className="muted">Estados, sesiones y credenciales temporales se gestionan en esta vista protegida.</p>
        </div>
      </header>

      {error && <div className="error-message" role="alert">{error}</div>}
      {selfManagement && (
        <div className="warning-message" role="alert">
          No puedes cambiar tu propio estado, rol u organización. Sí puedes consultar tu información y sesiones.
        </div>
      )}

      <section className="user-overview-grid">
        <article className="summary-card"><span>Usuario</span><strong>{user.displayName}</strong><small>{user.email}</small></article>
        <article className="summary-card"><span>Rol</span><strong>{user.roles.map((role) => roleLabels[role] ?? role).join(', ')}</strong></article>
        <article className="summary-card"><span>Organización</span><strong>{user.organizationName ?? 'Global'}</strong></article>
        <article className="summary-card"><span>Estado actual</span><strong>{statusLabels[user.status]}</strong><small>{user.statusReason || 'Sin motivo registrado'}</small></article>
      </section>

      {temporaryPassword && (
        <section className="temporary-credential-panel" aria-live="polite">
          <div>
            <p className="eyebrow">Mostrar una sola vez</p>
            <h2>Contraseña temporal</h2>
            <p>Compártela de forma segura. No se guarda en texto plano ni podrá consultarse otra vez.</p>
          </div>
          <code>{temporaryPassword}</code>
          <div className="inline-actions">
            <button className="secondary-button" type="button" onClick={() => void copyTemporaryPassword()}><Icon name="copy" size={16} /> Copiar contraseña</button>
            <button className="secondary-button" type="button" onClick={() => setTemporaryPassword(null)}>Ocultar definitivamente</button>
          </div>
        </section>
      )}

      <section className="management-grid">
        <article className="management-section">
          <div>
            <h2>Estado y acceso</h2>
            <p className="field-help">Cada transición revoca sesiones cuando corresponde y conserva la trazabilidad.</p>
          </div>
          <div className="internal-user-action-list form-wide">
            {user.status !== 'ACTIVE' && user.status !== 'DELETED' && (
              <button className="primary-button" type="button" disabled={selfManagement} onClick={() => requestAction('ACTIVATE')}>Activar</button>
            )}
            {(user.status === 'ACTIVE' || user.status === 'SUSPENDED') && (
              <button className="secondary-button" type="button" disabled={selfManagement} onClick={() => requestAction('DEACTIVATE')}>Inactivar</button>
            )}
            {user.status !== 'SUSPENDED' && user.status !== 'DELETED' && (
              <button className="danger-button" type="button" disabled={selfManagement} onClick={() => requestAction('SUSPEND')}>Suspender</button>
            )}
            {user.status !== 'DELETED' && (
              <button className="danger-button" type="button" disabled={selfManagement} onClick={() => requestAction('DELETE')}>Eliminar</button>
            )}
            {user.status === 'DELETED' && (
              <button className="primary-button" type="button" disabled={selfManagement} onClick={() => requestAction('RESTORE')}>Restaurar como inactivo</button>
            )}
          </div>
        </article>

        <article className="management-section">
          <div>
            <h2>Sesiones y contraseña</h2>
            <p className="field-help">Sesiones activas: {activeSessionCount}. Último acceso: {formatDate(user.lastLoginAt)}.</p>
          </div>
          <div className="internal-user-action-list form-wide">
            <button className="secondary-button" type="button" disabled={activeSessionCount === 0} onClick={() => requestAction('REVOKE')}>Revocar sesiones</button>
            <button className="danger-button" type="button" disabled={selfManagement || user.status === 'DELETED'} onClick={() => requestAction('RESET_PASSWORD')}>Generar contraseña temporal</button>
          </div>
        </article>
      </section>

      <section className="detail-card internal-user-admin-metadata">
        <div><span>Último cambio</span><strong>{formatDate(user.statusChangedAt)}</strong></div>
        <div><span>Ejecutado por</span><strong>{user.statusChangedBy ?? 'Sistema'}</strong></div>
        <div><span>Inicio de vigencia</span><strong>{formatDate(user.startsAt)}</strong></div>
        <div><span>Vencimiento</span><strong>{formatDate(user.expiresAt, 'Sin vencimiento')}</strong></div>
      </section>

      <section className="ns-data-panel internal-user-history-panel">
        <header className="internal-panel-heading"><div><h2>Historial de estados</h2><p className="muted">Registro cronológico de transiciones administrativas.</p></div></header>
        <div className="ns-data-table-wrap">
          <table className="ns-data-table">
            <thead><tr><th>Fecha</th><th>Estado anterior</th><th>Estado nuevo</th><th>Ejecutado por</th><th>Motivo</th></tr></thead>
            <tbody>
              {history.length === 0 && <tr><td colSpan={5} className="ns-table-empty">Sin cambios registrados.</td></tr>}
              {history.map((item, index) => (
                <tr key={`${item.occurredAt}-${index}`}>
                  <td>{formatDate(item.occurredAt)}</td>
                  <td>{item.previousStatus ? statusLabels[item.previousStatus] : 'Creación'}</td>
                  <td>{statusLabels[item.newStatus]}</td>
                  <td>{item.actorDisplayName}</td>
                  <td>{item.reason || 'Sin motivo'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="ns-data-panel internal-user-sessions-panel">
        <header className="internal-panel-heading"><div><h2>Sesiones</h2><p className="muted">Sesiones normales y restringidas para cambio obligatorio de contraseña.</p></div></header>
        <div className="ns-data-table-wrap">
          <table className="ns-data-table">
            <thead><tr><th>Estado</th><th>Tipo</th><th>Inicio</th><th>Última actividad</th><th>Vencimiento</th><th>Dirección IP</th></tr></thead>
            <tbody>
              {sessions.length === 0 && <tr><td colSpan={6} className="ns-table-empty">Sin sesiones registradas.</td></tr>}
              {sessions.map((session) => (
                <tr key={session.publicId}>
                  <td><span className={`status-badge status-${session.status.toLowerCase()}`}>{sessionStatusLabel(session.status)}</span></td>
                  <td>{session.scope === 'PASSWORD_CHANGE' ? 'Cambio de contraseña' : 'Completa'}</td>
                  <td>{formatDate(session.createdAt)}</td>
                  <td>{formatDate(session.lastActivityAt)}</td>
                  <td>{formatDate(session.expiresAt)}</td>
                  <td>{session.ipAddress ?? 'No disponible'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <ConfirmDialog
        open={Boolean(pending)}
        title={pendingCopy?.title ?? ''}
        description={pendingCopy?.description ?? ''}
        confirmLabel={pendingCopy?.confirm ?? 'Confirmar'}
        tone={pendingCopy?.danger ? 'danger' : 'primary'}
        busy={busy}
        onCancel={() => setPending(null)}
        onConfirm={() => void executeAction()}
      >
        {(pending === 'SUSPEND' || pending === 'DELETE' || pending === 'DEACTIVATE' || pending === 'ACTIVATE' || pending === 'RESTORE') && (
          <label className="form-field">
            <span>Motivo {pending === 'SUSPEND' || pending === 'DELETE' ? '(obligatorio)' : '(opcional)'}</span>
            <textarea rows={3} value={reason} onChange={(event) => setReason(event.target.value)} placeholder="Describe el motivo administrativo" />
          </label>
        )}
        {pending === 'DELETE' && (
          <label className="form-field">
            <span>Escribe {user.email} para confirmar</span>
            <input value={deleteConfirmation} onChange={(event) => setDeleteConfirmation(event.target.value)} />
          </label>
        )}
      </ConfirmDialog>
    </main>
  )
}
