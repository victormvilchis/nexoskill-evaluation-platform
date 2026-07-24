import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { searchUsers } from '../features/users/api/userApi'
import { ApiRequestError } from '../shared/api/apiClient'
import type {
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
  { value: 'DISABLED', label: 'Deshabilitado' }
]

const statusLabels: Record<UserStatus | UserAccessStatus, string> = {
  ACTIVE: 'Activo',
  PENDING: 'Pendiente',
  SUSPENDED: 'Suspendido',
  LOCKED: 'Bloqueado',
  DISABLED: 'Deshabilitado',
  EXPIRED: 'Vencido',
  CANCELED: 'Cancelado'
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
  const [searchParams, setSearchParams] = useSearchParams()
  const [query, setQuery] = useState(searchParams.get('query') ?? '')
  const [status, setStatus] = useState<UserStatus | ''>(
    (searchParams.get('status') as UserStatus | null) ?? ''
  )
  const [data, setData] = useState<AdminUserPage | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [now, setNow] = useState(() => Date.now())

  const page = Math.max(Number(searchParams.get('page') ?? 0), 0)
  const created = searchParams.get('created') === '1'

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

  function applyFilters() {
    const next = new URLSearchParams()
    if (query.trim()) next.set('query', query.trim())
    if (status) next.set('status', status)
    setSearchParams(next)
  }

  function goToPage(nextPage: number) {
    const next = new URLSearchParams(searchParams)
    next.delete('created')
    next.set('page', String(nextPage))
    setSearchParams(next)
  }

  return (
    <main className="content-page">
      <div className="page-heading">
        <div>
          <p className="eyebrow">Administración</p>
          <h1>Usuarios</h1>
          <p className="muted">
            Consulta usuarios y controla su acceso inicial a la plataforma.
          </p>
        </div>
        <Link className="primary-button button-link" to="/admin/users/new">
          Crear usuario
        </Link>
      </div>

      {created && (
        <div className="success-message" role="status">
          El usuario fue creado correctamente.
        </div>
      )}

      <section className="filter-panel" aria-label="Filtros de usuarios">
        <div className="form-field">
          <label htmlFor="user-query">Nombre o correo</label>
          <input
            id="user-query"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') applyFilters()
            }}
            placeholder="Buscar usuario"
          />
        </div>
        <div className="form-field">
          <label htmlFor="user-status">Estado</label>
          <select
            id="user-status"
            value={status}
            onChange={(event) => setStatus(event.target.value as UserStatus | '')}
          >
            {USER_STATUSES.map((option) => (
              <option key={option.value || 'ALL'} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
        <button className="secondary-button" type="button" onClick={applyFilters}>
          Aplicar filtros
        </button>
      </section>

      {error && <div className="error-message">{error}</div>}

      <section className="table-panel" aria-busy={loading}>
        <div className="table-summary">
          <strong>{data?.totalElements ?? 0} usuarios</strong>
          <span>Página {(data?.page ?? 0) + 1} de {Math.max(data?.totalPages ?? 1, 1)}</span>
        </div>

        <div className="responsive-table">
          <table>
            <thead>
              <tr>
                <th>Usuario</th>
                <th>Rol</th>
                <th>Cuenta</th>
                <th>Acceso</th>
                <th>Vencimiento</th>
                <th>Último acceso</th>
              </tr>
            </thead>
            <tbody>
              {loading && (
                <tr>
                  <td colSpan={6} className="empty-cell">Cargando usuarios…</td>
                </tr>
              )}
              {!loading && data?.content.length === 0 && (
                <tr>
                  <td colSpan={6} className="empty-cell">No se encontraron usuarios.</td>
                </tr>
              )}
              {!loading && data?.content.map((user) => {
                const accessStatus = effectiveAccessStatus(
                  user.accessStatus,
                  user.startsAt,
                  user.expiresAt,
                  now
                )
                return (
                <tr key={user.publicId}>
                  <td>
                    <strong>{user.displayName}</strong>
                    <small>{user.email}</small>
                  </td>
                  <td>{user.roles.join(', ')}</td>
                  <td>
                    <span className={`status-badge status-${user.status.toLowerCase()}`}>
                      {statusLabels[user.status]}
                    </span>
                  </td>
                  <td>
                    <span className={`status-badge status-${accessStatus.toLowerCase()}`}>
                      {statusLabels[accessStatus]}
                    </span>
                  </td>
                  <td>{formatDate(user.expiresAt)}</td>
                  <td>{user.lastLoginAt ? formatDate(user.lastLoginAt) : 'Sin acceso'}</td>
                </tr>
                )
              })}
            </tbody>
          </table>
        </div>

        <div className="pagination-controls">
          <button
            className="secondary-button"
            type="button"
            disabled={!data || data.page <= 0}
            onClick={() => goToPage(page - 1)}
          >
            Anterior
          </button>
          <button
            className="secondary-button"
            type="button"
            disabled={!data || data.page + 1 >= data.totalPages}
            onClick={() => goToPage(page + 1)}
          >
            Siguiente
          </button>
        </div>
      </section>
    </main>
  )
}
