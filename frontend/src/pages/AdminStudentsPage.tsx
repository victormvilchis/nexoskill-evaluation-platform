import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useAuth } from '../features/authentication/context/AuthContext'
import { searchOrganizations } from '../features/organizations/api/organizationApi'
import { searchStudents } from '../features/students/api/studentApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { FilterToolbar } from '../shared/components/FilterToolbar'
import { Icon } from '../shared/components/Icon'
import { ResourceSearchField, ResourceSelectField } from '../shared/components/ResourceFilters'
import { TableActionLink, TableActions } from '../shared/components/TableActions'
import { TablePagination } from '../shared/components/TablePagination'
import { parsePage, parsePageSize, type PageSize } from '../shared/types/pagination'
import { useDebouncedValue } from '../shared/hooks/useDebouncedValue'
import type { OrganizationSummary } from '../features/organizations/types/organizations'
import type { StudentEffectiveStatus, StudentPage } from '../shared/types/students'

const statuses: Array<{ value: StudentEffectiveStatus | 'ALL'; label: string }> = [
  { value: 'ACTIVE', label: 'Activo' },
  { value: 'ALL', label: 'Todos los estados' },
  { value: 'INACTIVE', label: 'Desactivado' },
  { value: 'EXPIRED', label: 'Vencido' }
]
const statusLabels: Record<StudentEffectiveStatus, string> = {
  ACTIVE: 'Activo', INACTIVE: 'Desactivado', EXPIRED: 'Vencido', DELETED: 'Eliminado'
}
const validStatuses = new Set<StudentEffectiveStatus>(['ACTIVE', 'INACTIVE', 'EXPIRED'])
function statusFromQuery(value: string | null): StudentEffectiveStatus | 'ALL' {
  if (value === 'ALL') return 'ALL'
  return value && validStatuses.has(value as StudentEffectiveStatus) ? value as StudentEffectiveStatus : 'ACTIVE'
}
function formatDate(value: string | null) {
  if (!value) return 'Sin vencimiento'
  return new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium' }).format(new Date(value))
}

export function AdminStudentsPage() {
  const { user } = useAuth()
  const permissions = useMemo(() => new Set(user?.permissions ?? []), [user])
  const administrator = Boolean(user?.roles.includes('ADMINISTRATOR'))
  const [organizations, setOrganizations] = useState<OrganizationSummary[]>([])
  const [searchParams, setSearchParams] = useSearchParams()
  const [query, setQuery] = useState(searchParams.get('query') ?? '')
  const [status, setStatus] = useState<StudentEffectiveStatus | 'ALL'>(statusFromQuery(searchParams.get('status')))
  const [data, setData] = useState<StudentPage | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const page = parsePage(searchParams.get('page'))
  const size = parsePageSize(searchParams.get('size'))
  const organization = searchParams.get('organization') ?? ''
  const debouncedQuery = useDebouncedValue(query, 300)

  useEffect(() => {
    if (!administrator) return
    const controller = new AbortController()
    searchOrganizations({ status: 'ACTIVE', page: 0, size: 100, signal: controller.signal })
      .then((response) => setOrganizations(response.content.filter((item) => item.organizationType === 'CUSTOMER')))
      .catch(() => { if (!controller.signal.aborted) setOrganizations([]) })
    return () => controller.abort()
  }, [administrator])

  useEffect(() => {
    const currentQuery = searchParams.get('query') ?? ''
    const currentStatus = statusFromQuery(searchParams.get('status'))
    if (currentQuery === debouncedQuery.trim() && currentStatus === status) return
    const next = new URLSearchParams(searchParams)
    next.delete('page')
    if (debouncedQuery.trim()) next.set('query', debouncedQuery.trim()); else next.delete('query')
    if (status === 'ACTIVE') next.delete('status'); else next.set('status', status)
    setSearchParams(next, { replace: true })
  }, [debouncedQuery, searchParams, setSearchParams, status])

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(null)
    searchStudents({
      query: searchParams.get('query') ?? '', status: statusFromQuery(searchParams.get('status')),
      organizationPublicId: administrator ? organization || undefined : undefined,
      page, size, sort: searchParams.get('sort') ?? 'updatedAt',
      direction: searchParams.get('direction') === 'ASC' ? 'ASC' : 'DESC', signal: controller.signal
    }).then((response) => {
      setData({ ...response, content: response.content ?? [] })
      if (response.totalPages > 0 && page >= response.totalPages) goToPage(response.totalPages - 1)
    }).catch((requestError) => {
      if (!controller.signal.aborted) setError(requestError instanceof ApiRequestError ? requestError.message : 'No fue posible consultar estudiantes.')
    }).finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [administrator, organization, page, searchParams, size])

  function updateParam(name: string, value: string) {
    const next = new URLSearchParams(searchParams); next.delete('page')
    if (value) next.set(name, value); else next.delete(name)
    setSearchParams(next)
  }
  function clearFilters() { setQuery(''); setStatus('ACTIVE'); setSearchParams(new URLSearchParams()) }
  function goToPage(nextPage: number) {
    const next = new URLSearchParams(searchParams)
    if (nextPage > 0) next.set('page', String(nextPage)); else next.delete('page')
    setSearchParams(next)
  }
  function changePageSize(nextSize: PageSize) {
    const next = new URLSearchParams(searchParams); next.delete('page')
    if (nextSize === 10) next.delete('size'); else next.set('size', String(nextSize))
    setSearchParams(next)
  }

  const columnCount = administrator ? 6 : 5
  return (
    <main className="content-page resource-page ns-list-page student-page student-global-page">
      <header className="ns-page-header"><div><p className="eyebrow">Administración</p><h1>Estudiantes</h1><p className="muted">Las acciones respetan la organización propietaria y el ciclo de vida activo, desactivado o vencido.</p></div>{permissions.has('STUDENT_CREATE') && <Link className="primary-button button-link" to="/admin/students/new"><Icon name="plus" size={16} /> Crear estudiante</Link>}</header>
      <FilterToolbar hasActiveFilters={Boolean(query || status !== 'ACTIVE' || organization)} onClear={clearFilters}>
        <ResourceSearchField value={query} onChange={setQuery} placeholder="Buscar por nombre, correo o código" />
        {administrator && <ResourceSelectField label="Organización" value={organization} onChange={(value) => updateParam('organization', value)}><option value="">Todas las organizaciones</option>{organizations.map((item) => <option key={item.publicId} value={item.publicId}>{item.name} · {item.code}</option>)}</ResourceSelectField>}
        <ResourceSelectField label="Estado" value={status} onChange={(value) => setStatus(value as StudentEffectiveStatus | 'ALL')}>{statuses.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</ResourceSelectField>
      </FilterToolbar>
      {error && <div className="error-message" role="alert">{error}</div>}
      <section className="ns-data-panel" aria-busy={loading}>
        <div className="ns-data-table-wrap"><table className="ns-data-table"><thead><tr><th>Estudiante</th>{administrator && <th>Organización</th>}<th>Estado</th><th>Vencimiento</th><th>Certificaciones</th><th className="ns-actions-column">Acciones</th></tr></thead><tbody>
          {loading && <tr><td colSpan={columnCount} className="ns-table-empty">Cargando estudiantes…</td></tr>}
          {!loading && !error && data?.content.length === 0 && <tr><td colSpan={columnCount} className="ns-table-empty">No se encontraron estudiantes con los filtros seleccionados.</td></tr>}
          {!loading && data?.content.map((student) => <tr key={student.publicId}>
            <td className="ns-primary-cell"><strong>{student.displayName}</strong><small>{student.email}</small></td>
            {administrator && <td>{student.organization ? <><strong>{student.organization.name}</strong><small>{student.organization.code}</small></> : '—'}</td>}
            <td><span className={`status-badge status-${student.effectiveStatus.toLowerCase()}`}>{statusLabels[student.effectiveStatus]}</span></td>
            <td>{formatDate(student.expiresAt)}</td>
            <td>{student.certificationsEnabled ? <span className="status-badge status-active">Habilitadas</span> : <span className="muted">—</span>}</td>
            <td className="ns-actions-column"><TableActions>
              <TableActionLink icon="eye" label="Ver" to={`/admin/students/${student.publicId}`} />
              {permissions.has('STUDENT_UPDATE') && <TableActionLink icon="edit" label="Editar" to={`/admin/students/${student.publicId}/edit`} />}
              {(permissions.has('STUDENT_STATUS_CHANGE') || permissions.has('STUDENT_SESSION_MANAGE') || permissions.has('STUDENT_DELETE')) && <TableActionLink icon="lock" label="Administrar" to={`/admin/students/${student.publicId}/manage`} tone="primary" />}
              {student.certificationsEnabled && permissions.has('STUDENT_CERTIFICATION_MANAGE') && <TableActionLink icon="clipboard" label="Administrar certificaciones" to={`/admin/students/${student.publicId}/certifications`} />}
            </TableActions></td>
          </tr>)}
        </tbody></table></div>
        <TablePagination currentPage={page} pageSize={data?.size ?? size} totalElements={data?.totalElements ?? 0} totalPages={data?.totalPages ?? 0} isLoading={loading} onPageChange={goToPage} onPageSizeChange={changePageSize} />
      </section>


    </main>
  )
}
