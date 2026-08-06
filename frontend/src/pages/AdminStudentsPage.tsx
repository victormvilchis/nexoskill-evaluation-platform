import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useAuth } from '../features/authentication/context/AuthContext'
import { getStudentFilterOptions, searchStudents } from '../features/students/api/studentApi'
import { permanentlyDeletePerson } from '../features/talent-bank/api/talentBankApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { ConfirmDialog } from '../shared/components/ConfirmDialog'
import { FilterToolbar } from '../shared/components/FilterToolbar'
import { Icon } from '../shared/components/Icon'
import { SortIndicator } from '../shared/components/SortIndicator'
import { ResourceSearchField, ResourceSelectField } from '../shared/components/ResourceFilters'
import { TableActionButton, TableActionLink, TableActions } from '../shared/components/TableActions'
import { TablePagination } from '../shared/components/TablePagination'
import { useToast } from '../shared/components/ToastProvider'
import { parsePage, parsePageSize, type PageSize } from '../shared/types/pagination'
import { useDebouncedValue } from '../shared/hooks/useDebouncedValue'
import type { StudentEffectiveStatus, StudentFilterOptions, StudentPage } from '../shared/types/students'
import { formatPersonName } from '../shared/utils/personNames'
const statuses: Array<{ value: StudentEffectiveStatus | 'ALL'; label: string }> = [
  { value: 'ACTIVE', label: 'Activo' },
  { value: 'ALL', label: 'Todos los estados' },
  { value: 'INACTIVE', label: 'Dado de baja' }
]
const statusLabels: Record<StudentEffectiveStatus, string> = {
  ACTIVE: 'Activo', INACTIVE: 'Dado de baja', EXPIRED: 'Dado de baja', DELETED: 'Eliminado'
}
const validStatuses = new Set<StudentEffectiveStatus>(['ACTIVE', 'INACTIVE'])
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

function formatTechnology(technology?: string | null, expertise?: string | null) {
  const normalizedTechnology = technology?.trim()
  if (!normalizedTechnology) return 'N/A'

  const normalizedExpertise = expertise?.trim()
  const hasExpertise = Boolean(normalizedExpertise)
    && normalizedExpertise?.toLocaleLowerCase('es-MX') !== 'sin nivel'

  return hasExpertise ? `${normalizedTechnology} - ${normalizedExpertise}` : normalizedTechnology
}
export function AdminStudentsPage() {
  const toast = useToast()
  const { user } = useAuth()
  const permissions = useMemo(() => new Set(user?.permissions ?? []), [user])
  const administrator = Boolean(user?.roles.includes('ADMINISTRATOR'))
  const [searchParams, setSearchParams] = useSearchParams()
  const [query, setQuery] = useState(searchParams.get('query') ?? '')
  const [status, setStatus] = useState<StudentEffectiveStatus | 'ALL'>(statusFromQuery(searchParams.get('status')))
  const [filterOptions, setFilterOptions] = useState<StudentFilterOptions>({ roles: [], technologies: [] })
  const [data, setData] = useState<StudentPage | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [deleteCandidate, setDeleteCandidate] = useState<StudentPage['content'][number]>()
  const [deleting, setDeleting] = useState(false)
  const page = parsePage(searchParams.get('page'))
  const size = parsePageSize(searchParams.get('size'))
  const role = searchParams.get('role') ?? ''
  const technology = searchParams.get('technology') ?? ''
  const debouncedQuery = useDebouncedValue(query, 300)
  useEffect(() => {
    const controller = new AbortController()
    getStudentFilterOptions(controller.signal)
      .then(setFilterOptions)
      .catch(() => {
        if (!controller.signal.aborted) setFilterOptions({ roles: [], technologies: [] })
      })
    return () => controller.abort()
  }, [])

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
      profilePublicId: role || undefined,
      technologyPublicId: technology || undefined,
      page, size, sort: searchParams.get('sort') ?? 'displayName',
      direction: searchParams.get('direction') === 'DESC' ? 'DESC' : 'ASC', signal: controller.signal
    }).then((response) => {
      setData({ ...response, content: response.content ?? [] })
      if (response.totalPages > 0 && page >= response.totalPages) {
        const validPage = response.totalPages - 1
        const next = new URLSearchParams(searchParams)
        if (validPage > 0) next.set('page', String(validPage)); else next.delete('page')
        setSearchParams(next, { replace: true })
      }
    }).catch((requestError) => {
      if (!controller.signal.aborted) setError(requestError instanceof ApiRequestError ? requestError.message : 'No fue posible consultar colaboradores.')
    }).finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [page, role, searchParams, setSearchParams, size, technology])
  function updateParam(name: string, value: string) {
    const next = new URLSearchParams(searchParams); next.delete('page')
    if (value) next.set(name, value); else next.delete(name)
    setSearchParams(next)
  }
  function toggleSort(nextSort: 'displayName' | 'admissionDate') {
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
  const direction = searchParams.get('direction') === 'DESC' ? 'DESC' : 'ASC'
  const showCertificationColumns = data?.content.some((student) => student.certificationsEnabled) ?? true
  const columnCount = showCertificationColumns ? 6 : 5
  const canImport = permissions.has('STUDENT_IMPORT')
  const importTarget = '/admin/collaborators/import'
  async function confirmPermanentDeletion() {
    if (!deleteCandidate || deleting) return
    setDeleting(true)
    try {
      await permanentlyDeletePerson(deleteCandidate.publicId)
      setData((current) => current ? {
        ...current,
        content: current.content.filter((item) => item.publicId !== deleteCandidate.publicId),
        totalElements: Math.max(0, current.totalElements - 1)
      } : current)
      toast.success('El registro y toda su información asociada fueron eliminados definitivamente.')
      setDeleteCandidate(undefined)
    } catch (requestError) {
      toast.error('No fue posible eliminar el registro.', requestError instanceof ApiRequestError ? requestError.message : undefined)
    } finally {
      setDeleting(false)
    }
  }
  return (
    <main className="content-page resource-page ns-list-page student-page student-global-page">
      {(canImport || permissions.has('STUDENT_CREATE')) && <div className="ns-list-action-bar ns-student-header-actions" aria-label="Acciones de colaboradores">{canImport && <Link className="button-link ns-excel-import-button ns-create-button-secondary" to={importTarget} aria-label="Cargar Excel de colaboradores"><span className="ns-excel-import-icon-wrap" aria-hidden="true"><svg className="ns-excel-import-icon" focusable="false" viewBox="0 0 24 24" fill="none"><path d="M5.5 3.5h9l4 4v13h-13v-17Z" stroke="currentColor" strokeWidth="1.7" strokeLinejoin="round"/><path d="M14.5 3.5v4h4M8.25 11l3 5m0-5-3 5M14 11v5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg></span><span>Cargar Excel</span></Link>}{permissions.has('STUDENT_CREATE') && <Link className="primary-button button-link ns-create-button" to="/admin/collaborators/new"><Icon name="plus" size={15} /> Crear colaborador</Link>}</div>}
      <FilterToolbar hasActiveFilters={Boolean(query || role || technology || status !== 'ACTIVE')} onClear={clearFilters}>
        <ResourceSearchField value={query} onChange={setQuery} placeholder="Buscar por nombre, correo, código o usuario corporativo" />
        <ResourceSelectField label="Rol" value={role} onChange={(value) => updateParam('role', value)}>
          <option value="">Todos los roles</option>
          {filterOptions.roles.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
        </ResourceSelectField>
        <ResourceSelectField label="Tecnología" value={technology} onChange={(value) => updateParam('technology', value)}>
          <option value="">Todas las tecnologías</option>
          {filterOptions.technologies.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
        </ResourceSelectField>
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
                {showCertificationColumns && <th>Rol</th>}
                <th>Tecnología actual</th>
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
                <th className="ns-actions-column">Acciones</th>
              </tr>
            </thead>
            <tbody>
          {loading && <tr><td colSpan={columnCount} className="ns-table-empty">Cargando colaboradores…</td></tr>}
          {!loading && !error && data?.content.length === 0 && <tr><td colSpan={columnCount} className="ns-table-empty">No se encontraron colaboradores con los filtros seleccionados.</td></tr>}
          {!loading && data?.content.map((student) => <tr key={student.publicId}>
            <td className="ns-primary-cell">
              <span className="ns-person-name">{formatPersonName(student.displayName)}</span>
              <small>{student.email?.trim() || 'N/A'}</small>
            </td>
            {showCertificationColumns && <td>{formatRole(student.professionalProfile?.name, student.technologicalProfile?.name)}</td>}
            <td>{formatTechnology(student.currentTechnology, student.expertise)}</td>
            <td>{formatDate(student.admissionDate)}</td>
            <td><span className={`status-badge status-${student.effectiveStatus.toLowerCase()}`}>{statusLabels[student.effectiveStatus]}</span></td>
            <td className="ns-actions-column"><TableActions>
              <TableActionLink icon="eye" label="Ver" to={`/admin/collaborators/${student.publicId}`} />
              {permissions.has('STUDENT_UPDATE') && <TableActionLink icon="edit" label="Editar" to={`/admin/collaborators/${student.publicId}/edit`} />}
              {(permissions.has('STUDENT_STATUS_CHANGE') || permissions.has('STUDENT_SESSION_MANAGE') || permissions.has('STUDENT_DELETE')) && <TableActionLink icon="lock" label="Gestionar" to={`/admin/collaborators/${student.publicId}/manage`} tone="primary" />}
              {student.certificationsEnabled && permissions.has('STUDENT_VIEW') && <TableActionLink icon="clipboard" label="Certificaciones" to={`/admin/collaborators/${student.publicId}/certifications`} />}
              {permissions.has('STUDENT_DELETE') && <TableActionButton icon="trash" label="Eliminar definitivamente" tone="danger" onClick={() => setDeleteCandidate(student)} />}
            </TableActions></td>
          </tr>)}
            </tbody>
          </table>
        </div>
        <TablePagination currentPage={page} pageSize={data?.size ?? size} totalElements={data?.totalElements ?? 0} totalPages={data?.totalPages ?? 0} isLoading={loading} onPageChange={goToPage} onPageSizeChange={changePageSize} />
      </section>
      <ConfirmDialog open={Boolean(deleteCandidate)} title="Eliminar definitivamente"
        description="Esta acción eliminará de forma permanente el registro, su historial, sus documentos y toda la información asociada. La información no podrá continuar consultándose en la plataforma. ¿Deseas continuar?"
        confirmLabel="Eliminar definitivamente" tone="danger" busy={deleting}
        onCancel={() => setDeleteCandidate(undefined)} onConfirm={() => void confirmPermanentDeletion()}>
        {deleteCandidate && <div className="permanent-deletion-warning"><strong>{formatPersonName(deleteCandidate.displayName)}</strong><p>{deleteCandidate.email} · {deleteCandidate.studentCode}</p></div>}
      </ConfirmDialog>
    </main>
  )
}
