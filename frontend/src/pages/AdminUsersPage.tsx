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
import { TablePagination } from '../shared/components/TablePagination'
import { parsePage, parsePageSize, type PageSize } from '../shared/types/pagination'
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
  const [reloadKey, setReloadKey] = useState(0)
  const debouncedQuery = useDebouncedValue(query, 300)
  const page = parsePage(searchParams.get('page'))
  const size = parsePageSize(searchParams.get('size'))

  useEffect(() => {
    const currentQuery = searchParams.get('query') ?? ''
    const currentStatus = validStatus(searchParams.get('status'))
    if (currentQuery === debouncedQuery.trim() && currentStatus === status) return

    const next = new URLSearchParams(searchParams)
    next.delete('page')
    if (debouncedQuery.trim()) next.set('query', debouncedQuery.trim())
    else next.delete('query')
    if (status === 'ACTIVE') next.delete('status')
    else next.set('status', status)
    setSearchParams(next, { replace: true })
  }, [debouncedQuery, searchParams, setSearchParams, status])
  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(null)
    searchUsers({
      query: searchParams.get('query') ?? '',
      status: validStatus(searchParams.get('status')),
      page,
      size,
      sort: searchParams.get('sort') ?? 'createdAt',
      direction: searchParams.get('direction') === 'ASC' ? 'ASC' : 'DESC',
      signal: controller.signal
    })
      .then((response) => {
        setData(response)
        if (response.totalPages > 0 && page >= response.totalPages) goToPage(response.totalPages - 1)
      })
      .catch((requestError) => {
        if (controller.signal.aborted) return
        setError(requestError instanceof ApiRequestError
          ? requestError.message
          : 'No fue posible consultar los usuarios internos.')
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [page, reloadKey, searchParams, size])

  function clearFilters() {
    setQuery('')
    setStatus('ACTIVE')
  }

  function goToPage(nextPage: number) {
    const next = new URLSearchParams(searchParams)
    if (nextPage > 0) next.set('page', String(nextPage))
    else next.delete('page')
    setSearchParams(next)
  }

  function changePageSize(nextSize: PageSize) {
    const next = new URLSearchParams(searchParams)
    next.delete('page')
    if (nextSize === 10) next.delete('size')
    else next.set('size', String(nextSize))
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

      {error && <section className="inline-error-panel" role="alert"><div className="inline-error-icon"><Icon name="error" size={20} /></div><div><strong>No fue posible cargar los usuarios</strong><p>{error}</p></div><button className="secondary-button" type="button" onClick={() => setReloadKey((value) => value + 1)}>Reintentar</button></section>}

      <section className="ns-data-panel" aria-busy={loading}>
        <div className="ns-data-table-wrap">
          <table className="ns-data-table">
            <thead>
              <tr>
                <th>Usuario</th>
                <th>Rol</th>
                <th>Organización</th>
                <th>Estado</th>
                <th className="ns-actions-column">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {loading && (
                <tr><td colSpan={5} className="ns-table-empty">Cargando usuarios…</td></tr>
              )}
              {!loading && data?.content.length === 0 && (
                <tr><td colSpan={5} className="ns-table-empty">No se encontraron usuarios.</td></tr>
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

        <TablePagination
          currentPage={page}
          pageSize={data?.size ?? size}
          totalElements={data?.totalElements ?? 0}
          totalPages={data?.totalPages ?? 0}
          isLoading={loading}
          onPageChange={goToPage}
          onPageSizeChange={changePageSize}
        />
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
