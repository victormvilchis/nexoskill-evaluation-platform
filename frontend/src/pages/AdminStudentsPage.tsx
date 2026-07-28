import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useAuth } from '../features/authentication/context/AuthContext'
import { searchOrganizations } from '../features/organizations/api/organizationApi'
import {
  activateStudent, archiveStudent, deactivateStudent, restoreStudent, searchStudents, suspendStudent
} from '../features/students/api/studentApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { ConfirmDialog } from '../shared/components/ConfirmDialog'
import { FilterToolbar } from '../shared/components/FilterToolbar'
import { Icon } from '../shared/components/Icon'
import { ResourceSearchField, ResourceSelectField } from '../shared/components/ResourceFilters'
import { TableActionButton, TableActionLink, TableActions } from '../shared/components/TableActions'
import { TablePagination } from '../shared/components/TablePagination'
import { parsePage, parsePageSize, type PageSize } from '../shared/types/pagination'
import { useToast } from '../shared/components/ToastProvider'
import { useDebouncedValue } from '../shared/hooks/useDebouncedValue'
import type { OrganizationSummary } from '../features/organizations/types/organizations'
import type { StudentEffectiveStatus, StudentPage, StudentSummary } from '../shared/types/students'

const statuses: Array<{ value: StudentEffectiveStatus | 'ALL'; label: string }> = [
  { value: 'ACTIVE', label: 'Activo' }, { value: 'ALL', label: 'Todos los estados' },
  { value: 'PENDING', label: 'Pendiente' }, { value: 'EXPIRED', label: 'Vencido' },
  { value: 'INACTIVE', label: 'Inactivo' }, { value: 'SUSPENDED', label: 'Suspendido' },
  { value: 'ARCHIVED', label: 'Archivado' }, { value: 'DELETED', label: 'Eliminado' }
]
const statusLabels: Record<StudentEffectiveStatus, string> = {
  ACTIVE: 'Activo', INACTIVE: 'Inactivo', SUSPENDED: 'Suspendido', ARCHIVED: 'Archivado',
  DELETED: 'Eliminado', PENDING: 'Pendiente', EXPIRED: 'Vencido'
}
const validStatuses = new Set<StudentEffectiveStatus>(['ACTIVE', 'PENDING', 'EXPIRED', 'INACTIVE', 'SUSPENDED', 'ARCHIVED', 'DELETED'])
function statusFromQuery(value: string | null): StudentEffectiveStatus | 'ALL' {
  if (value === 'ALL') return 'ALL'
  return value && validStatuses.has(value as StudentEffectiveStatus) ? value as StudentEffectiveStatus : 'ACTIVE'
}
type Action = 'ACTIVATE' | 'DEACTIVATE' | 'SUSPEND' | 'ARCHIVE' | 'RESTORE'
interface PendingAction { action: Action; student: StudentSummary }
function formatDate(value: string | null) {
  if (!value) return 'Sin vencimiento'
  return new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium' }).format(new Date(value))
}

export function AdminStudentsPage() {
  const { user } = useAuth()
  const toast = useToast()
  const permissions = useMemo(() => new Set(user?.permissions ?? []), [user])
  const administrator = Boolean(user?.roles.includes('ADMINISTRATOR'))
  const [organizations, setOrganizations] = useState<OrganizationSummary[]>([])
  const [searchParams, setSearchParams] = useSearchParams()
  const [query, setQuery] = useState(searchParams.get('query') ?? '')
  const [status, setStatus] = useState<StudentEffectiveStatus | 'ALL'>(statusFromQuery(searchParams.get('status')))
  const [data, setData] = useState<StudentPage | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState<PendingAction | null>(null)
  const [busy, setBusy] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)
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
      includeDeleted: statusFromQuery(searchParams.get('status')) === 'DELETED',
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
  }, [administrator, organization, page, reloadKey, searchParams, size])

  async function executeAction() {
    if (!pending) return
    setBusy(true)
    try {
      const updated = pending.action === 'ACTIVATE' ? await activateStudent(pending.student.publicId)
        : pending.action === 'DEACTIVATE' ? await deactivateStudent(pending.student.publicId)
          : pending.action === 'SUSPEND' ? await suspendStudent(pending.student.publicId)
            : pending.action === 'ARCHIVE' ? await archiveStudent(pending.student.publicId)
              : await restoreStudent(pending.student.publicId)
      setReloadKey((value) => value + 1)
      toast.success('Estado actualizado', `${updated.displayName} quedó como ${statusLabels[updated.effectiveStatus].toLowerCase()}.`)
      setPending(null)
    } catch (requestError) {
      toast.error('No fue posible actualizar al estudiante', requestError instanceof ApiRequestError ? requestError.message : undefined)
    } finally { setBusy(false) }
  }

  function updateParam(name: string, value: string) {
    const next = new URLSearchParams(searchParams)
    next.delete('page')
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

  return (
    <main className="content-page resource-page ns-list-page student-page student-global-page">
      <header className="ns-page-header"><div><p className="eyebrow">Administración</p><h1>Estudiantes</h1><p className="muted">La organización es un filtro administrativo; no modifica el contexto de sesión.</p></div>{permissions.has('STUDENT_CREATE') && <Link className="primary-button button-link" to="/admin/students/new"><Icon name="plus" size={16} /> Crear estudiante</Link>}</header>
      <FilterToolbar hasActiveFilters={Boolean(query || status !== 'ACTIVE' || organization)} onClear={clearFilters}>
        <ResourceSearchField value={query} onChange={setQuery} placeholder="Buscar por nombre, correo o código" />
        {administrator && <ResourceSelectField label="Organización" value={organization} onChange={(value) => updateParam('organization', value)}><option value="">Todas las organizaciones</option>{organizations.map((item) => <option key={item.publicId} value={item.publicId}>{item.name} · {item.code}</option>)}</ResourceSelectField>}
        <ResourceSelectField label="Estado" value={status} onChange={(value) => setStatus(value as StudentEffectiveStatus | 'ALL')}>{statuses.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</ResourceSelectField>
      </FilterToolbar>
      {error && <div className="error-message" role="alert">{error}</div>}
      <section className="ns-data-panel" aria-busy={loading}>
        <div className="ns-data-table-wrap"><table className="ns-data-table"><thead><tr><th>Estudiante</th>{administrator && <th>Organización</th>}<th>Perfil</th><th>Perfil tecnológico</th><th>Tecnología</th><th>Certificaciones</th><th>Estado</th><th>Vencimiento</th><th className="ns-actions-column">Acciones</th></tr></thead><tbody>
          {loading && <tr><td colSpan={administrator ? 9 : 8} className="ns-table-empty">Cargando estudiantes…</td></tr>}
          {!loading && !error && data?.content.length === 0 && <tr><td colSpan={administrator ? 9 : 8} className="ns-table-empty">No se encontraron estudiantes con los filtros seleccionados.</td></tr>}
          {!loading && data?.content.map((student) => <tr key={student.publicId} className={student.status === 'DELETED' ? 'ns-row-muted' : ''}>
            <td className="ns-primary-cell"><strong>{student.displayName}</strong><small>{student.email}</small></td>
            {administrator && <td>{student.organization ? <><strong>{student.organization.name}</strong><small>{student.organization.code}</small></> : '—'}</td>}
            <td>{student.professionalProfile?.name ?? '—'}</td><td>{student.technologicalProfile?.name ?? '—'}</td><td>{student.technology?.name ?? '—'}</td>
            <td>{student.certificationsEnabled ? <span className="status-badge status-active">Aplica</span> : <span className="muted">No aplica</span>}</td>
            <td><span className={`status-badge status-${student.effectiveStatus.toLowerCase()}`}>{statusLabels[student.effectiveStatus]}</span></td><td>{formatDate(student.expiresAt)}</td>
            <td className="ns-actions-column"><TableActions><TableActionLink icon="eye" label="Ver detalle" to={`/admin/students/${student.publicId}`} />{student.certificationsEnabled && permissions.has('STUDENT_CERTIFICATION_MANAGE') && <TableActionLink icon="clipboard" label="Administrar certificaciones" to={`/admin/students/${student.publicId}/certifications`} tone="primary" />}{permissions.has('STUDENT_UPDATE') && !['ARCHIVED', 'DELETED'].includes(student.status) && <TableActionLink icon="edit" label="Editar" to={`/admin/students/${student.publicId}/edit`} />}{permissions.has('STUDENT_STATUS_CHANGE') && student.status !== 'ACTIVE' && student.status !== 'DELETED' && <TableActionButton icon="check" label="Activar" onClick={() => setPending({ action: 'ACTIVATE', student })} />}{permissions.has('STUDENT_STATUS_CHANGE') && student.status === 'ACTIVE' && <TableActionButton icon="warning" label="Desactivar" onClick={() => setPending({ action: 'DEACTIVATE', student })} />}{permissions.has('STUDENT_STATUS_CHANGE') && !['SUSPENDED', 'DELETED', 'ARCHIVED'].includes(student.status) && <TableActionButton icon="warning" label="Suspender" onClick={() => setPending({ action: 'SUSPEND', student })} />}{permissions.has('STUDENT_STATUS_CHANGE') && !['ARCHIVED', 'DELETED'].includes(student.status) && <TableActionButton icon="archive" label="Archivar" onClick={() => setPending({ action: 'ARCHIVE', student })} />}{permissions.has('STUDENT_STATUS_CHANGE') && ['ARCHIVED', 'DELETED'].includes(student.status) && <TableActionButton icon="restore" label="Restaurar" onClick={() => setPending({ action: 'RESTORE', student })} />}</TableActions></td>
          </tr>)}
        </tbody></table></div>
        <TablePagination currentPage={page} pageSize={data?.size ?? size} totalElements={data?.totalElements ?? 0} totalPages={data?.totalPages ?? 0} isLoading={loading} onPageChange={goToPage} onPageSizeChange={changePageSize} />
      </section>
      <ConfirmDialog open={Boolean(pending)} title="Actualizar estado del estudiante" description="La pérdida de acceso revocará las sesiones activas y conservará el historial." confirmLabel="Confirmar" busy={busy} onCancel={() => setPending(null)} onConfirm={() => void executeAction()} />
    </main>
  )
}
