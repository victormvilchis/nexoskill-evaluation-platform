import { useEffect, useMemo, useState } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../features/authentication/context/AuthContext'
import { searchUsers } from '../features/users/api/userApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { ConfirmDialog } from '../shared/components/ConfirmDialog'
import { FilterToolbar } from '../shared/components/FilterToolbar'
import { Icon } from '../shared/components/Icon'
import { ResourceSearchField, ResourceSelectField } from '../shared/components/ResourceFilters'
import { TableActionLink, TableActions } from '../shared/components/TableActions'
import { useToast } from '../shared/components/ToastProvider'
import { useDebouncedValue } from '../shared/hooks/useDebouncedValue'
import type { AdminUserPage, UserStatus } from '../shared/types/users'

const statusOptions: Array<{ value: UserStatus | 'ALL'; label: string }> = [
  { value: 'ACTIVE', label: 'Activos' },
  { value: 'INACTIVE', label: 'Inactivos' },
  { value: 'SUSPENDED', label: 'Suspendidos' },
  { value: 'DELETED', label: 'Eliminados' },
  { value: 'ALL', label: 'Todos' }
]

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

function validStatus(value: string | null): UserStatus | 'ALL' {
  return statusOptions.some((option) => option.value === value)
    ? value as UserStatus | 'ALL'
    : 'ACTIVE'
}

interface UsersNavigationState {
  temporaryCredentials?: {
    email: string
    password: string
  }
}

function formatDate(value: string | null, fallback = 'Sin registro') {
  if (!value) return fallback
  return new Intl.DateTimeFormat('es-MX', {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(new Date(value))
}

export function AdminUsersPage() {
  const { user } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const toast = useToast()
  const navigationState = location.state as UsersNavigationState | null
  const temporaryCredentials = navigationState?.temporaryCredentials
  const permissions = useMemo(() => new Set(user?.permissions ?? []), [user])
  const [searchParams, setSearchParams] = useSearchParams()
  const [query, setQuery] = useState(searchParams.get('query') ?? '')
  const [status, setStatus] = useState<UserStatus | 'ALL'>(validStatus(searchParams.get('status')))
  const [data, setData] = useState<AdminUserPage | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const debouncedQuery = useDebouncedValue(query, 300)
  const parsedPage = Number.parseInt(searchParams.get('page') ?? '0', 10)
  const page = Number.isFinite(parsedPage) && parsedPage >= 0 ? parsedPage : 0

  useEffect(() => {
    const currentQuery = searchParams.get('query') ?? ''
    const currentStatus = validStatus(searchParams.get('status'))
    if (currentQuery === debouncedQuery.trim() && currentStatus === status) return

    const next = new URLSearchParams()
    if (debouncedQuery.trim()) next.set('query', debouncedQuery.trim())
    next.set('status', status)
    setSearchParams(next, { replace: true })
  }, [debouncedQuery, searchParams, setSearchParams, status])

  useEffect(() => {
    let active = true
    setLoading(true)
    setError(null)

    searchUsers({
      query: searchParams.get('query') ?? '',
      status: validStatus(searchParams.get('status')),
      page
    })
      .then((response) => {
        if (active) setData(response)
      })
      .catch((requestError) => {
        if (!active) return
        setError(requestError instanceof ApiRequestError
          ? requestError.message
          : 'No fue posible consultar los usuarios internos.')
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => { active = false }
  }, [page, searchParams])

  function clearFilters() {
    setQuery('')
    setStatus('ACTIVE')
  }

  function goToPage(nextPage: number) {
    const next = new URLSearchParams(searchParams)
    next.set('page', String(nextPage))
    setSearchParams(next)
  }

  async function copyCredential(value: string, label: string) {
    await navigator.clipboard.writeText(value)
    toast.success(`${label} copiado`, 'Compártelo mediante un canal seguro.')
  }

  function closeTemporaryCredentials() {
    navigate(`${location.pathname}${location.search}`, { replace: true, state: null })
  }

  return (
    <main className="content-page resource-page ns-list-page internal-users-page">
      <header className="ns-page-header">
        <div>
          <p className="eyebrow">Administración</p>
          <h1>Usuarios</h1>
          <p className="muted">Administra exclusivamente cuentas internas de Administrador, Gestor y Supervisor.</p>
        </div>
        {permissions.has('USER_CREATE') && (
          <Link className="primary-button button-link" to="/admin/users/new">
            <Icon name="plus" size={16} /> Crear usuario
          </Link>
        )}
      </header>

      <FilterToolbar
        resultLabel={`${data?.totalElements ?? 0} ${data?.totalElements === 1 ? 'usuario' : 'usuarios'}`}
        hasActiveFilters={Boolean(query || status !== 'ACTIVE')}
        onClear={clearFilters}
      >
        <ResourceSearchField
          value={query}
          onChange={setQuery}
          placeholder="Buscar por nombre o correo"
        />
        <ResourceSelectField
          label="Estado"
          value={status}
          onChange={(value) => setStatus(value as UserStatus | 'ALL')}
        >
          {statusOptions.map((option) => (
            <option key={option.value} value={option.value}>{option.label}</option>
          ))}
        </ResourceSelectField>
      </FilterToolbar>

      {error && <div className="error-message" role="alert">{error}</div>}

      <section className="ns-data-panel" aria-busy={loading}>
        <div className="ns-data-table-wrap">
          <table className="ns-data-table">
            <thead>
              <tr>
                <th>Usuario</th>
                <th>Rol</th>
                <th>Organización</th>
                <th>Estado</th>
                <th>Último acceso</th>
                <th>Creación</th>
                <th className="ns-actions-column">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {loading && (
                <tr><td colSpan={7} className="ns-table-empty">Cargando usuarios…</td></tr>
              )}
              {!loading && data?.content.length === 0 && (
                <tr><td colSpan={7} className="ns-table-empty">No se encontraron usuarios.</td></tr>
              )}
              {!loading && data?.content.map((item) => (
                <tr key={item.publicId} className={item.status === 'DELETED' ? 'ns-row-muted' : ''}>
                  <td className="ns-primary-cell">
                    <strong>{item.displayName}</strong>
                    <small>{item.email}</small>
                  </td>
                  <td>{item.roles.map((role) => roleLabels[role] ?? role).join(', ')}</td>
                  <td>{item.organizationName ?? 'Global'}</td>
                  <td>
                    <span className={`status-badge status-${item.status.toLowerCase()}`}>
                      {statusLabels[item.status]}
                    </span>
                  </td>
                  <td>{formatDate(item.lastLoginAt)}</td>
                  <td>{formatDate(item.createdAt)}</td>
                  <td className="ns-actions-column">
                    <TableActions>
                      <TableActionLink icon="eye" label="Ver" to={`/admin/users/${item.publicId}`} />
                      {permissions.has('USER_UPDATE') && item.status !== 'DELETED' && (
                        <TableActionLink icon="edit" label="Editar" to={`/admin/users/${item.publicId}/edit`} />
                      )}
                      {permissions.has('USER_STATUS_CHANGE') && (
                        <TableActionLink
                          icon="lock"
                          label="Administrar"
                          tone="primary"
                          to={`/admin/users/${item.publicId}/manage`}
                        />
                      )}
                    </TableActions>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {data && data.totalPages > 1 && (
          <div className="pagination-bar">
            <button className="secondary-button" disabled={page <= 0} onClick={() => goToPage(page - 1)}>
              Anterior
            </button>
            <span>Página {page + 1} de {data.totalPages}</span>
            <button className="secondary-button" disabled={page + 1 >= data.totalPages} onClick={() => goToPage(page + 1)}>
              Siguiente
            </button>
          </div>
        )}
      </section>

      <ConfirmDialog
        open={Boolean(temporaryCredentials)}
        title="Credenciales temporales"
        description="La contraseña se muestra una sola vez. Cópiala antes de cerrar esta ventana."
        confirmLabel="Copiar ambas"
        cancelLabel="Cerrar"
        onCancel={closeTemporaryCredentials}
        onConfirm={() => temporaryCredentials && void copyCredential(
          `Usuario: ${temporaryCredentials.email}\nContraseña temporal: ${temporaryCredentials.password}`,
          'Credenciales'
        )}
      >
        {temporaryCredentials && (
          <div className="temporary-credential-grid temporary-credential-dialog-grid">
            <div>
              <span>Usuario</span>
              <code>{temporaryCredentials.email}</code>
              <button className="secondary-button" type="button"
                onClick={() => void copyCredential(temporaryCredentials.email, 'Usuario')}>Copiar usuario</button>
            </div>
            <div>
              <span>Contraseña temporal</span>
              <code>{temporaryCredentials.password}</code>
              <button className="secondary-button" type="button"
                onClick={() => void copyCredential(temporaryCredentials.password, 'Contraseña')}>Copiar contraseña</button>
            </div>
          </div>
        )}
      </ConfirmDialog>
    </main>
  )
}
