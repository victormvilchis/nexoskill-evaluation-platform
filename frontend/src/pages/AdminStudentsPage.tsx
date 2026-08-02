import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useAuth } from '../features/authentication/context/AuthContext'
import { searchOrganizations } from '../features/organizations/api/organizationApi'
import { getStudentCatalogs, searchStudents } from '../features/students/api/studentApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { FilterToolbar } from '../shared/components/FilterToolbar'
import { Icon } from '../shared/components/Icon'
import { SortIndicator } from '../shared/components/SortIndicator'
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
  if (!value) return 'N/A'
  return new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium' }).format(new Date(`${value}T12:00:00`))
}
function formatRole(profile?: string | null, technologicalProfile?: string | null) {
  return [profile, technologicalProfile].filter((value): value is string => Boolean(value?.trim())).join(' - ') || 'N/A'
}
export function AdminStudentsPage() {
  const { user } = useAuth()
  const permissions = useMemo(() => new Set(user?.permissions ?? []), [user])
  const certificationOperator = Boolean(user?.roles.some(
    (role) => role === 'ADMINISTRATOR' || role === 'MANAGER' || role === 'SUPERVISOR'
  ))
  const administrator = Boolean(user?.roles.includes('ADMINISTRATOR'))
  const [organizations, setOrganizations] = useState<OrganizationSummary[]>([])
  const [searchParams, setSearchParams] = useSearchParams()
  const [query, setQuery] = useState(searchParams.get('query') ?? '')
  const [status, setStatus] = useState<StudentEffectiveStatus | 'ALL'>(statusFromQuery(searchParams.get('status')))
  const [data, setData] = useState<StudentPage | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [tenantManualStudentCode, setTenantManualStudentCode] = useState(false)
  const [tenantCertificationsEnabled, setTenantCertificationsEnabled] = useState(false)
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
    if (administrator) return
    const controller = new AbortController()
    getStudentCatalogs(undefined, controller.signal)
      .then((response) => {
        setTenantManualStudentCode(response.organization.manualStudentCode)
        setTenantCertificationsEnabled(response.organization.appliesCertifications)
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setTenantManualStudentCode(false)
          setTenantCertificationsEnabled(false)
        }
      })
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
      page, size, sort: searchParams.get('sort') ?? 'displayName',
      direction: searchParams.get('direction') === 'DESC' ? 'DESC' : 'ASC', signal: controller.signal
    }).then((response) => {
      setData({ ...response, content: response.content ?? [] })
      if (response.totalPages > 0 && page >= response.totalPages) goToPage(response.totalPages - 1)
    }).catch((requestError) => {
      if (!controller.signal.aborted) setError(requestError instanceof ApiRequestError ? requestError.message : 'No fue posible consultar colaboradores.')
    }).finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [administrator, organization, page, searchParams, size])
  function updateParam(name: string, value: string) {
    const next = new URLSearchParams(searchParams); next.delete('page')
    if (value) next.set(name, value); else next.delete(name)
    setSearchParams(next)
  }
  function toggleSort(nextSort: 'displayName' | 'admissionDate' | 'expiresAt') {
    const currentSort = searchParams.get('sort')
    const currentDirection = searchParams.get('direction') === 'DESC' ? 'DESC' : 'ASC'
    const nextDirection = currentSort === nextSort && currentDirection === 'ASC' ? 'DESC' : 'ASC'
    const next = new URLSearchParams(searchParams); next.delete('page')
    next.set('sort', nextSort); next.set('direction', nextDirection)
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
  const activeSort = searchParams.get('sort')
  const sort = activeSort ?? 'displayName'
  const direction = searchParams.get('direction') === 'DESC' ? 'DESC' : 'ASC'
  const selectedOrganization = organizations.find((item) => item.publicId === organization)
  const showStudentCode = administrator ? Boolean(organization && selectedOrganization?.manualStudentCode) : tenantManualStudentCode
  const showCertificationColumns = administrator
    ? Boolean(organization && selectedOrganization?.appliesCertifications)
    : tenantCertificationsEnabled
  const columnCount = 5 + (showCertificationColumns ? 2 : 0) + (administrator ? 1 : 0) + (showStudentCode ? 1 : 0)
  useEffect(() => {
    if (showCertificationColumns || sort !== 'expiresAt') return
    const next = new URLSearchParams(searchParams)
    next.delete('page')
    next.set('sort', 'displayName')
    next.set('direction', 'ASC')
    setSearchParams(next, { replace: true })
  }, [searchParams, setSearchParams, showCertificationColumns, sort])
  const canImport = (administrator || certificationOperator) && permissions.has('STUDENT_CREATE') && permissions.has('STUDENT_UPDATE')
  const importTarget = administrator && organization
    ? `/admin/students/import?organization=${encodeURIComponent(organization)}`
    : '/admin/students/import'
  return (
    <main className="content-page resource-page ns-list-page student-page student-global-page">
      <header className="ns-page-header"><div><p className="eyebrow">Administración</p><h1>Colaboradores</h1><p className="muted">Las acciones respetan la organización propietaria y el ciclo de vida activo, desactivado o vencido.</p></div><div className="ns-page-header-actions ns-student-header-actions">{canImport && <Link className="button-link ns-excel-import-button" to={importTarget} aria-label="Cargar Excel de colaboradores"><span className="ns-excel-import-icon-wrap" aria-hidden="true"><svg className="ns-excel-import-icon" focusable="false" viewBox="0 0 24 24" fill="none"><path d="M5.5 3.5h9l4 4v13h-13v-17Z" stroke="currentColor" strokeWidth="1.7" strokeLinejoin="round"/><path d="M14.5 3.5v4h4M8.25 11l3 5m0-5-3 5M14 11v5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg></span><span>Cargar Excel</span></Link>}{permissions.has('STUDENT_CREATE') && <Link className="primary-button button-link" to="/admin/students/new"><Icon name="plus" size={16} /> Crear colaborador</Link>}</div></header>
      <FilterToolbar hasActiveFilters={Boolean(query || status !== 'ACTIVE' || organization)} onClear={clearFilters}>
        <ResourceSearchField value={query} onChange={setQuery} placeholder="Buscar por nombre, correo, código o usuario corporativo" />
        {administrator && <ResourceSelectField label="Organización" value={organization} onChange={(value) => updateParam('organization', value)}><option value="">Todas las organizaciones</option>{organizations.map((item) => <option key={item.publicId} value={item.publicId}>{item.name} · {item.code}</option>)}</ResourceSelectField>}
        <ResourceSelectField label="Estado" value={status} onChange={(value) => setStatus(value as StudentEffectiveStatus | 'ALL')}>{statuses.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</ResourceSelectField>
      </FilterToolbar>
      {error && <div className="error-message" role="alert">{error}</div>}
      <section className="ns-data-panel" aria-busy={loading}>
        <div className="ns-data-table-wrap">
          <table className="ns-data-table">
            <thead>
              <tr>
                <th aria-sort={activeSort === 'displayName' ? (direction === 'ASC' ? 'ascending' : 'descending') : 'none'}>
                  <button
                    className={`ns-sortable-column-button${activeSort === 'displayName' ? ' active' : ''}`}
                    type="button"
                    onClick={() => toggleSort('displayName')}
                  >
                    Colaborador
                    <SortIndicator active={activeSort === 'displayName'} direction={direction} />
                  </button>
                </th>
                {administrator && <th>Organización</th>}
                {showStudentCode && <th>Código a nivel organización</th>}
                {showCertificationColumns && <th>Rol</th>}
                <th>Usuario corporativo</th>
                <th aria-sort={activeSort === 'admissionDate' ? (direction === 'ASC' ? 'ascending' : 'descending') : 'none'}>
                  <button
                    className={`ns-sortable-column-button${activeSort === 'admissionDate' ? ' active' : ''}`}
                    type="button"
                    onClick={() => toggleSort('admissionDate')}
                  >
                    Fecha de alta
                    <SortIndicator active={activeSort === 'admissionDate'} direction={direction} />
                  </button>
                </th>
                <th>Estado</th>
                {showCertificationColumns && (
                  <th aria-sort={activeSort === 'expiresAt' ? (direction === 'ASC' ? 'ascending' : 'descending') : 'none'}>
                    <button
                      className={`ns-sortable-column-button${activeSort === 'expiresAt' ? ' active' : ''}`}
                      type="button"
                      onClick={() => toggleSort('expiresAt')}
                    >
                      Vencimiento
                      <SortIndicator active={activeSort === 'expiresAt'} direction={direction} />
                    </button>
                  </th>
                )}
                <th className="ns-actions-column">Acciones</th>
              </tr>
            </thead>
            <tbody>
          {loading && <tr><td colSpan={columnCount} className="ns-table-empty">Cargando colaboradores…</td></tr>}
          {!loading && !error && data?.content.length === 0 && <tr><td colSpan={columnCount} className="ns-table-empty">No se encontraron colaboradores con los filtros seleccionados.</td></tr>}
          {!loading && data?.content.map((student) => <tr key={student.publicId}>
            <td className="ns-primary-cell"><strong>{student.displayName}</strong><small>{student.email}</small></td>
            {administrator && <td>{student.organization ? <><strong>{student.organization.name}</strong><small>{student.organization.code}</small></> : '—'}</td>}
            {showStudentCode && <td>{student.studentCode || 'N/A'}</td>}
            {showCertificationColumns && <td>{formatRole(student.professionalProfile?.name, student.technologicalProfile?.name)}</td>}
            <td>{student.corporateUser || 'N/A'}</td>
            <td>{formatDate(student.admissionDate)}</td>
            <td><span className={`status-badge status-${student.effectiveStatus.toLowerCase()}`}>{statusLabels[student.effectiveStatus]}</span></td>
            {showCertificationColumns && <td>{formatDate(student.expiresAt)}</td>}
            <td className="ns-actions-column"><TableActions>
              <TableActionLink icon="eye" label="Ver" to={`/admin/students/${student.publicId}`} />
              {permissions.has('STUDENT_UPDATE') && <TableActionLink icon="edit" label="Editar" to={`/admin/students/${student.publicId}/edit`} />}
              {(permissions.has('STUDENT_STATUS_CHANGE') || permissions.has('STUDENT_SESSION_MANAGE') || permissions.has('STUDENT_DELETE')) && <TableActionLink icon="lock" label="Administrar" to={`/admin/students/${student.publicId}/manage`} tone="primary" />}
              {certificationOperator && student.certificationsEnabled && student.effectiveStatus === 'ACTIVE' && permissions.has('STUDENT_CERTIFICATION_MANAGE') && <TableActionLink icon="clipboard" label="Administrar certificaciones" to={`/admin/students/${student.publicId}/certifications`} />}
            </TableActions></td>
          </tr>)}
            </tbody>
          </table>
        </div>
        <TablePagination currentPage={page} pageSize={data?.size ?? size} totalElements={data?.totalElements ?? 0} totalPages={data?.totalPages ?? 0} isLoading={loading} onPageChange={goToPage} onPageSizeChange={changePageSize} />
      </section>
    </main>
  )
}
