import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useAuth } from '../features/authentication/context/AuthContext'
import {
  activateUser,
  deleteUser,
  restoreUser,
  searchUsers,
  suspendUser
} from '../features/users/api/userApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { ConfirmDialog } from '../shared/components/ConfirmDialog'
import { FilterToolbar } from '../shared/components/FilterToolbar'
import { Icon } from '../shared/components/Icon'
import {
  ResourceSearchField,
  ResourceSelectField
} from '../shared/components/ResourceFilters'
import {
  TableActionButton,
  TableActionLink,
  TableActions
} from '../shared/components/TableActions'
import { useToast } from '../shared/components/ToastProvider'
import { useDebouncedValue } from '../shared/hooks/useDebouncedValue'
import type {
  AdminUser,
  AdminUserPage,
  UserAccessStatus,
  UserStatus
} from '../shared/types/users'

const USER_STATUSES: Array<{ value: UserStatus | ''; label: string }> = [
  { value: '', label: 'Todos los estados' },
  { value: 'ACTIVE', label: 'Activo' },
  { value: 'PENDING', label: 'Pendiente' },
  { value: 'SUSPENDED', label: 'Suspendido' },
  { value: 'LOCKED', label: 'Bloqueado' },
  { value: 'DISABLED', label: 'Deshabilitado' },
  { value: 'DELETED', label: 'Eliminado' }
]

const statusLabels: Record<UserStatus | UserAccessStatus, string> = {
  ACTIVE: 'Activo',
  PENDING: 'Pendiente',
  SUSPENDED: 'Suspendido',
  LOCKED: 'Bloqueado',
  DISABLED: 'Deshabilitado',
  DELETED: 'Eliminado',
  EXPIRED: 'Vencido',
  CANCELED: 'Cancelado'
}

type UserAction = 'ACTIVATE' | 'SUSPEND' | 'DELETE' | 'RESTORE'
interface PendingUserAction {
  action: UserAction
  user: AdminUser
}

function effectiveAccessStatus(
  status: UserAccessStatus,
  startsAt: string,
  expiresAt: string | null,
  now: number
): UserAccessStatus {
  if (status !== 'ACTIVE') return status
  if (new Date(startsAt).getTime() > now) return 'PENDING'
  if (expiresAt && new Date(expiresAt).getTime() <= now) return 'EXPIRED'
  return 'ACTIVE'
}

function formatDate(value: string | null) {
  if (!value) return 'Sin vencimiento'
  return new Intl.DateTimeFormat('es-MX', {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(new Date(value))
}

export function AdminUsersPage() {
  const toast = useToast()
  const { user: currentUser } = useAuth()
  const permissions = useMemo(() => new Set(currentUser?.permissions ?? []), [currentUser])
  const [searchParams, setSearchParams] = useSearchParams()
  const [query, setQuery] = useState(searchParams.get('query') ?? '')
  const [status, setStatus] = useState<UserStatus | ''>(
    (searchParams.get('status') as UserStatus | null) ?? ''
  )
  const [data, setData] = useState<AdminUserPage | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [now, setNow] = useState(() => Date.now())
  const [pendingAction, setPendingAction] = useState<PendingUserAction | null>(null)
  const [busyId, setBusyId] = useState<string>()

  const page = Math.max(Number(searchParams.get('page') ?? 0), 0)
  const debouncedQuery = useDebouncedValue(query, 300)

  useEffect(() => {
    if (searchParams.get('created') !== '1') return
    toast.success('Usuario creado', 'La cuenta quedó disponible con la vigencia configurada.')
    const next = new URLSearchParams(searchParams)
    next.delete('created')
    setSearchParams(next, { replace: true })
  }, [searchParams, setSearchParams, toast])

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [])

  useEffect(() => {
    let active = true
    setLoading(true)
    setError(null)

    searchUsers({
      query: searchParams.get('query') ?? '',
      status: (searchParams.get('status') as UserStatus | null) ?? '',
      page
    })
      .then((response) => {
        if (active) setData(response)
      })
      .catch((requestError) => {
        if (!active) return
        setError(
          requestError instanceof ApiRequestError
            ? requestError.message
            : 'No fue posible consultar los usuarios.'
        )
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [page, searchParams])

  useEffect(() => {
    const currentQuery = searchParams.get('query') ?? ''
    const currentStatus = searchParams.get('status') ?? ''
    if (currentQuery === debouncedQuery.trim() && currentStatus === status) return
    const next = new URLSearchParams()
    if (debouncedQuery.trim()) next.set('query', debouncedQuery.trim())
    if (status) next.set('status', status)
    setSearchParams(next, { replace: true })
  }, [debouncedQuery, status, searchParams, setSearchParams])

  function clearFilters() {
    setQuery('')
    setStatus('')
  }

  function goToPage(nextPage: number) {
    const next = new URLSearchParams(searchParams)
    next.delete('created')
    next.set('page', String(nextPage))
    setSearchParams(next)
  }

  async function executePendingAction() {
    if (!pendingAction) return
    const { action, user } = pendingAction
    setBusyId(user.publicId)
    try {
      const updated = action === 'ACTIVATE'
        ? await activateUser(user.publicId)
        : action === 'SUSPEND'
          ? await suspendUser(user.publicId)
          : action === 'DELETE'
            ? await deleteUser(user.publicId, 'Eliminación administrativa')
            : await restoreUser(user.publicId)
      setData((current) => current
        ? { ...current, content: current.content.map((item) => item.publicId === updated.publicId ? updated : item) }
        : current)
      toast.success(
        action === 'ACTIVATE' ? 'Usuario activado'
          : action === 'SUSPEND' ? 'Usuario suspendido'
            : action === 'DELETE' ? 'Usuario eliminado'
              : 'Usuario restaurado como suspendido'
      )
      setPendingAction(null)
    } catch (requestError) {
      toast.error(
        'No fue posible completar la operación',
        requestError instanceof ApiRequestError ? requestError.message : undefined
      )
    } finally {
      setBusyId(undefined)
    }
  }

  const dialogTitle = pendingAction?.action === 'DELETE'
    ? 'Eliminar usuario'
    : pendingAction?.action === 'RESTORE'
      ? 'Restaurar usuario'
      : pendingAction?.action === 'SUSPEND'
        ? 'Suspender usuario'
        : 'Activar usuario'
  const dialogDescription = pendingAction?.action === 'DELETE'
    ? 'La cuenta perderá el acceso y dejará de aparecer en la lista normal. El registro permanecerá almacenado.'
    : pendingAction?.action === 'RESTORE'
      ? 'La cuenta volverá como suspendida y deberá activarse explícitamente.'
      : pendingAction?.action === 'SUSPEND'
        ? 'Las sesiones activas se cerrarán inmediatamente.'
        : 'La cuenta volverá a estar disponible si su vigencia también está activa.'

  return (
    <main className="content-page resource-page ns-list-page">
      <header className="ns-page-header">
        <div>
          <p className="eyebrow">Administración</p>
          <h1>Usuarios</h1>
          <p className="muted">Consulta usuarios y controla su acceso a la plataforma.</p>
        </div>
        {permissions.has('USER_CREATE') && (
          <Link className="primary-button button-link" to="/admin/users/new">
            <Icon name="plus" size={16} /> Crear usuario
          </Link>
        )}
      </header>

      <FilterToolbar
        resultLabel={`${data?.totalElements ?? 0} ${data?.totalElements === 1 ? 'usuario' : 'usuarios'}`}
        hasActiveFilters={Boolean(query || status)}
        onClear={clearFilters}
      >
        <ResourceSearchField
          value={query}
          onChange={setQuery}
          placeholder="Buscar por nombre o correo"
        />
        <ResourceSelectField label="Estado" value={status} onChange={(value) => setStatus(value as UserStatus | '')}>
          {USER_STATUSES.map((option) => (
            <option key={option.value || 'ALL'} value={option.value}>{option.label}</option>
          ))}
        </ResourceSelectField>
      </FilterToolbar>

      {error && <div className="error-message">{error}</div>}

      <section className="ns-data-panel" aria-busy={loading}>
        <div className="ns-data-table-wrap">
          <table className="ns-data-table">
            <thead>
              <tr>
                <th>Usuario</th>
                <th>Rol</th>
                <th>Cuenta</th>
                <th>Acceso</th>
                <th>Vencimiento</th>
                <th>Último acceso</th>
                <th className="ns-actions-column">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {loading && <tr><td colSpan={7} className="ns-table-empty">Cargando usuarios…</td></tr>}
              {!loading && data?.content.length === 0 && (
                <tr><td colSpan={7} className="ns-table-empty">No se encontraron usuarios.</td></tr>
              )}
              {!loading && data?.content.map((user) => {
                const accessStatus = effectiveAccessStatus(user.accessStatus, user.startsAt, user.expiresAt, now)
                const isCurrentUser = currentUser?.publicId === user.publicId
                const canChangeStatus = permissions.has('USER_STATUS_CHANGE') && !isCurrentUser
                return (
                  <tr className={user.status === 'DELETED' ? 'ns-row-muted' : ''} key={user.publicId}>
                    <td className="ns-primary-cell"><strong>{user.displayName}</strong><small>{user.email}</small></td>
                    <td>{user.roles.join(', ')}</td>
                    <td><span className={`status-badge status-${user.status.toLowerCase()}`}>{statusLabels[user.status]}</span></td>
                    <td><span className={`status-badge status-${accessStatus.toLowerCase()}`}>{statusLabels[accessStatus]}</span></td>
                    <td>{formatDate(user.expiresAt)}</td>
                    <td>{user.lastLoginAt ? formatDate(user.lastLoginAt) : 'Sin acceso'}</td>
                    <td>
                      <TableActions>
                        <TableActionLink to={`/admin/users/${user.publicId}`} label="Ver" icon="eye" />
                        {permissions.has('USER_UPDATE') && user.status !== 'DELETED' && (
                          <TableActionLink to={`/admin/users/${user.publicId}`} label="Editar" icon="edit" tone="primary" />
                        )}
                        {canChangeStatus && user.status === 'ACTIVE' && (
                          <TableActionButton
                            disabled={busyId === user.publicId}
                            label="Suspender"
                            icon="archive"
                            onClick={() => setPendingAction({ action: 'SUSPEND', user })}
                          />
                        )}
                        {canChangeStatus && user.status !== 'ACTIVE' && user.status !== 'DELETED' && (
                          <TableActionButton
                            disabled={busyId === user.publicId}
                            label="Activar"
                            icon="restore"
                            onClick={() => setPendingAction({ action: 'ACTIVATE', user })}
                          />
                        )}
                        {canChangeStatus && user.status !== 'DELETED' && (
                          <TableActionButton
                            disabled={busyId === user.publicId}
                            label="Eliminar"
                            icon="trash"
                            tone="danger"
                            onClick={() => setPendingAction({ action: 'DELETE', user })}
                          />
                        )}
                        {canChangeStatus && user.status === 'DELETED' && (
                          <TableActionButton
                            disabled={busyId === user.publicId}
                            label="Restaurar"
                            icon="restore"
                            tone="primary"
                            onClick={() => setPendingAction({ action: 'RESTORE', user })}
                          />
                        )}
                      </TableActions>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>

        <div className="pagination-controls">
          <button className="secondary-button" type="button" disabled={!data || data.page <= 0} onClick={() => goToPage(page - 1)}>Anterior</button>
          <span>Página {(data?.page ?? 0) + 1} de {Math.max(data?.totalPages ?? 1, 1)}</span>
          <button className="secondary-button" type="button" disabled={!data || data.page + 1 >= data.totalPages} onClick={() => goToPage(page + 1)}>Siguiente</button>
        </div>
      </section>

      <ConfirmDialog
        open={pendingAction !== null}
        title={dialogTitle}
        description={dialogDescription}
        confirmLabel={pendingAction?.action === 'DELETE' ? 'Eliminar' : pendingAction?.action === 'RESTORE' ? 'Restaurar' : pendingAction?.action === 'SUSPEND' ? 'Suspender' : 'Activar'}
        tone={pendingAction?.action === 'DELETE' || pendingAction?.action === 'SUSPEND' ? 'danger' : 'primary'}
        busy={pendingAction !== null && busyId === pendingAction.user.publicId}
        onCancel={() => { if (!busyId) setPendingAction(null) }}
        onConfirm={() => void executePendingAction()}
      />
    </main>
  )
}
