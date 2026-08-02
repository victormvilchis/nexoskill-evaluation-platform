import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useAuth } from '../features/authentication/context/AuthContext'
import { searchOrganizations } from '../features/organizations/api/organizationApi'
import type {
  OrganizationPage,
  OrganizationStatus
} from '../features/organizations/types/organizations'
import { ApiRequestError } from '../shared/api/apiClient'
import { FilterToolbar } from '../shared/components/FilterToolbar'
import { Icon } from '../shared/components/Icon'
import {
  ResourceSearchField,
  ResourceSelectField
} from '../shared/components/ResourceFilters'
import { TableActionLink, TableActions } from '../shared/components/TableActions'
import { TablePagination } from '../shared/components/TablePagination'
import { parsePage, parsePageSize, type PageSize } from '../shared/types/pagination'
import { useToast } from '../shared/components/ToastProvider'
import { useDebouncedValue } from '../shared/hooks/useDebouncedValue'

const STATUS_OPTIONS: Array<{ value: OrganizationStatus | 'ALL'; label: string }> = [
  { value: 'ACTIVE', label: 'Activas' },
  { value: 'INACTIVE', label: 'Inactivas' },
  { value: 'DELETED', label: 'Eliminadas' },
  { value: 'ALL', label: 'Todas' }
]

const VALID_STATUSES = new Set<OrganizationStatus>([
  'ACTIVE', 'INACTIVE', 'SUSPENDED', 'EXPIRED', 'DELETED'
])

const STATUS_LABELS: Record<OrganizationStatus, string> = {
  ACTIVE: 'Activa',
  INACTIVE: 'Inactiva',
  SUSPENDED: 'Suspendida',
  EXPIRED: 'Vencida',
  DELETED: 'Eliminada'
}

const CONTENT_MODE_LABELS = {
  GLOBAL_CATALOG: 'Catálogo global',
  CLEAN: 'En limpio',
  CUSTOM: 'Personalizada'
} as const

function statusFromQuery(value: string | null): OrganizationStatus | 'ALL' {
  if (value === 'ALL') return 'ALL'
  return value && VALID_STATUSES.has(value as OrganizationStatus)
    ? value as OrganizationStatus
    : 'ACTIVE'
}


function formatDate(value?: string) {
  if (!value) return 'Sin vencimiento'
  const [yearText, monthText, dayText] = value.split('-')
  const year = Number(yearText)
  const month = Number(monthText)
  const day = Number(dayText)
  if (![year, month, day].every(Number.isFinite)) return value
  return new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium' })
    .format(new Date(year, month - 1, day))
}


export function AdminOrganizationsPage() {
  const toast = useToast()
  const { user } = useAuth()
  const permissions = useMemo(() => new Set(user?.permissions ?? []), [user])
  const [searchParams, setSearchParams] = useSearchParams()
  const [query, setQuery] = useState(searchParams.get('query') ?? '')
  const [status, setStatus] = useState<OrganizationStatus | 'ALL'>(
    statusFromQuery(searchParams.get('status'))
  )
  const [data, setData] = useState<OrganizationPage | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string>()
  const [reloadKey, setReloadKey] = useState(0)
  const debouncedQuery = useDebouncedValue(query, 300)
  const page = parsePage(searchParams.get('page'))
  const size = parsePageSize(searchParams.get('size'))

  useEffect(() => {
    const success = searchParams.get('success')
    if (!success) return
    const messages: Record<string, [string, string]> = {
      created: ['Organización creada correctamente.', 'La organización ya aparece en el listado de activas.'],
      updated: ['Organización actualizada correctamente.', 'Los cambios quedaron guardados.'],
      activated: ['Organización activada.', 'El acceso operativo volvió a estar disponible.'],
      deactivated: ['Organización desactivada.', 'Los accesos quedaron bloqueados y la información se conservó.'],
      deleted: ['Organización eliminada lógicamente.', 'La información histórica permanece almacenada.'],
      restored: ['Organización restaurada como inactiva.', 'Actívala explícitamente cuando deba volver a operar.']
    }
    const message = messages[success]
    if (message) toast.success(message[0], message[1])
    const next = new URLSearchParams(searchParams)
    next.delete('success')
    setSearchParams(next, { replace: true })
  }, [searchParams, setSearchParams, toast])

  useEffect(() => {
    const currentQuery = searchParams.get('query') ?? ''
    const currentStatus = statusFromQuery(searchParams.get('status'))
    const normalizedQuery = debouncedQuery.trim()
    if (currentQuery === normalizedQuery && currentStatus === status) return

    const next = new URLSearchParams(searchParams)
    next.delete('page')
    if (normalizedQuery) next.set('query', normalizedQuery)
    else next.delete('query')
    if (status === 'ACTIVE') next.delete('status')
    else next.set('status', status)
    setSearchParams(next, { replace: true })
  }, [debouncedQuery, searchParams, setSearchParams, status])

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(undefined)
    searchOrganizations({
      query: searchParams.get('query') ?? '',
      status: statusFromQuery(searchParams.get('status')),
      page,
      size,
      sort: searchParams.get('sort') ?? 'createdAt',
      direction: searchParams.get('direction') === 'ASC' ? 'ASC' : 'DESC',
      signal: controller.signal
    })
      .then((response) => {
        setData(response)
        if (response.totalPages > 0 && page >= response.totalPages) {
          goToPage(response.totalPages - 1)
        }
      })
      .catch((requestError: unknown) => {
        if (controller.signal.aborted) return
        setError(requestError instanceof ApiRequestError
          ? requestError.message
          : 'No fue posible consultar las organizaciones.')
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [page, reloadKey, searchParams, size])

  const activeFilters = Boolean(query.trim()) || status !== 'ACTIVE'

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

  return (
    <main className="content-page resource-page ns-list-page org-page">
      <header className="ns-page-header">
        <div>
          <p className="eyebrow">Administración</p>
          <h1>Organizaciones</h1>
          <p className="muted">Administra organizaciones, vigencia, contenido y capacidad comercial sin eliminar su historial.</p>
        </div>
        {permissions.has('ORGANIZATION_CREATE') && (
          <Link className="primary-button button-link" to="/admin/organizations/new">
            <Icon name="plus" size={17} /> Nueva organización
          </Link>
        )}
      </header>

      <FilterToolbar
        hasActiveFilters={activeFilters}
        onClear={clearFilters}
      >
        <ResourceSearchField value={query} onChange={setQuery} placeholder="Buscar por nombre o código" />
        <ResourceSelectField label="Estado" value={status} onChange={(value) => setStatus(value as OrganizationStatus | 'ALL')}>
          {STATUS_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
        </ResourceSelectField>
      </FilterToolbar>

      {error && (
        <section className="inline-error-panel" role="alert">
          <div className="inline-error-icon"><Icon name="error" size={20} /></div>
          <div><strong>No fue posible cargar las organizaciones</strong><p>{error}</p></div>
          <button className="secondary-button" type="button" onClick={() => setReloadKey((value) => value + 1)}>Reintentar</button>
        </section>
      )}

      <section className="ns-data-panel org-data-panel" aria-busy={loading}>
        <div className="ns-data-table-wrap">
          <table className="ns-data-table org-data-table">
            <thead>
              <tr>
                <th>Organización</th>
                <th>Modalidad</th>
                <th>Colaboradores</th>
                <th>Vigencia</th>
                <th>Estado</th>
                <th className="ns-actions-column">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {loading && <tr><td colSpan={6} className="ns-table-empty">Cargando organizaciones…</td></tr>}
              {!loading && !error && data?.content.length === 0 && (
                <tr><td colSpan={6} className="ns-table-empty">
                  <strong>{activeFilters ? 'No encontramos coincidencias' : 'Aún no hay organizaciones activas'}</strong>
                  <span>{activeFilters ? 'Ajusta o limpia los filtros.' : 'Crea la primera organización comercial para comenzar.'}</span>
                </td></tr>
              )}
              {!loading && !error && data?.content.map((item) => (
                <tr className={item.status === 'DELETED' ? 'ns-row-muted' : ''} key={item.publicId}>
                  <td className="ns-primary-cell">
                    <strong>{item.name}</strong>
                    <small><code className="ns-code-label">{item.code}</code> · {item.organizationType === 'GLOBAL' ? 'Sistema global' : 'Comercial'}</small>
                  </td>
                  <td><span className={`org-mode-badge org-mode-${item.contentMode.toLowerCase().replace('_', '-')}`}>{CONTENT_MODE_LABELS[item.contentMode]}</span></td>
                  <td><div className="org-student-counts" aria-label={`Activos: ${item.activeStudentCount ?? 0}. Inactivos: ${item.inactiveStudentCount ?? 0}. Vencidos: ${item.expiredStudentCount ?? 0}.`}>
                    <span><small>Activos</small><strong>{item.activeStudentCount ?? 0}</strong></span>
                    <span><small>Inactivos</small><strong>{item.inactiveStudentCount ?? 0}</strong></span>
                    <span><small>Vencidos</small><strong>{item.expiredStudentCount ?? 0}</strong></span>
                  </div></td>
                  <td>{formatDate(item.expiresOn)}</td>
                  <td><span className={`status-badge status-${item.status.toLowerCase()}`}>{STATUS_LABELS[item.status]}</span></td>
                  <td>
                    <TableActions>
                      <TableActionLink to={`/admin/organizations/${item.publicId}`} label="Ver" icon="eye" />
                      {permissions.has('ORGANIZATION_UPDATE') && item.status !== 'DELETED' && item.organizationType !== 'GLOBAL' && (
                        <TableActionLink to={`/admin/organizations/${item.publicId}/edit`} label="Editar" icon="edit" tone="primary" />
                      )}
                      {permissions.has('ORGANIZATION_STATUS_CHANGE') && item.organizationType !== 'GLOBAL' && (
                        <TableActionLink to={`/admin/organizations/${item.publicId}/manage`} label="Administrar" icon="lock" />
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
    </main>
  )
}
