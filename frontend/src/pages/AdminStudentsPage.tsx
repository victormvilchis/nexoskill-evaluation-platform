import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useAuth } from '../features/authentication/context/AuthContext'
import { searchOrganizations } from '../features/organizations/api/organizationApi'
import {
  activateStudent,
  archiveStudent,
  deactivateStudent,
  restoreStudent,
  searchStudents,
  suspendStudent
} from '../features/students/api/studentApi'
import { ApiRequestError, ORGANIZATION_CONTEXT_KEY } from '../shared/api/apiClient'
import { ConfirmDialog } from '../shared/components/ConfirmDialog'
import { FilterToolbar } from '../shared/components/FilterToolbar'
import { Icon } from '../shared/components/Icon'
import { ResourceSearchField, ResourceSelectField } from '../shared/components/ResourceFilters'
import { TableActionButton, TableActionLink, TableActions } from '../shared/components/TableActions'
import { useToast } from '../shared/components/ToastProvider'
import { useDebouncedValue } from '../shared/hooks/useDebouncedValue'
import type { OrganizationSummary } from '../features/organizations/types/organizations'
import type { StudentEffectiveStatus, StudentPage, StudentSummary } from '../shared/types/students'

const statuses: Array<{ value: StudentEffectiveStatus | ''; label: string }> = [
  { value: '', label: 'Todos los estados' },
  { value: 'ACTIVE', label: 'Activo' },
  { value: 'PENDING', label: 'Pendiente' },
  { value: 'EXPIRED', label: 'Vencido' },
  { value: 'INACTIVE', label: 'Inactivo' },
  { value: 'SUSPENDED', label: 'Suspendido' },
  { value: 'ARCHIVED', label: 'Archivado' },
  { value: 'DELETED', label: 'Eliminado' }
]

const statusLabels: Record<StudentEffectiveStatus, string> = {
  ACTIVE: 'Activo', INACTIVE: 'Inactivo', SUSPENDED: 'Suspendido', ARCHIVED: 'Archivado',
  DELETED: 'Eliminado', PENDING: 'Pendiente', EXPIRED: 'Vencido'
}

const validStatuses = new Set<StudentEffectiveStatus>([
  'ACTIVE', 'PENDING', 'EXPIRED', 'INACTIVE', 'SUSPENDED', 'ARCHIVED', 'DELETED'
])

function statusFromQuery(value: string | null): StudentEffectiveStatus | '' {
  return value && validStatuses.has(value as StudentEffectiveStatus)
    ? value as StudentEffectiveStatus
    : ''
}

type Action = 'ACTIVATE' | 'DEACTIVATE' | 'SUSPEND' | 'ARCHIVE' | 'RESTORE'
interface PendingAction { action: Action; student: StudentSummary }

function formatDate(value: string | null, emptyLabel = 'Sin vencimiento') {
  if (!value) return emptyLabel
  return new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

export function AdminStudentsPage() {
  const { user } = useAuth()
  const toast = useToast()
  const permissions = useMemo(() => new Set(user?.permissions ?? []), [user])
  const administrator = Boolean(user?.roles.includes('ADMINISTRATOR'))
  const [organizations, setOrganizations] = useState<OrganizationSummary[]>([])
  const [selectedOrganization, setSelectedOrganization] = useState(
    () => window.localStorage.getItem(ORGANIZATION_CONTEXT_KEY) ?? ''
  )
  const [searchParams, setSearchParams] = useSearchParams()
  const [query, setQuery] = useState(searchParams.get('query') ?? '')
  const [status, setStatus] = useState<StudentEffectiveStatus | ''>(statusFromQuery(searchParams.get('status')))
  const [includeDeleted, setIncludeDeleted] = useState(searchParams.get('deleted') === '1')
  const [data, setData] = useState<StudentPage | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState<PendingAction | null>(null)
  const [busy, setBusy] = useState(false)
  const parsedPage = Number.parseInt(searchParams.get('page') ?? '0', 10)
  const page = Number.isFinite(parsedPage) && parsedPage >= 0 ? parsedPage : 0
  const debouncedQuery = useDebouncedValue(query, 300)

  useEffect(() => {
    if (!administrator) return
    searchOrganizations({ status: 'ACTIVE', page: 0, size: 100 })
      .then((response) => {
        setOrganizations(response.content)
        if (selectedOrganization && !response.content.some((organization) => organization.publicId === selectedOrganization)) {
          setSelectedOrganization('')
        }
      })
      .catch(() => setOrganizations([]))
  }, [administrator, selectedOrganization])

  useEffect(() => {
    if (selectedOrganization) window.localStorage.setItem(ORGANIZATION_CONTEXT_KEY, selectedOrganization)
    else window.localStorage.removeItem(ORGANIZATION_CONTEXT_KEY)
  }, [selectedOrganization])

  useEffect(() => {
    const currentQuery = searchParams.get('query') ?? ''
    const currentStatus = searchParams.get('status') ?? ''
    const currentDeleted = searchParams.get('deleted') === '1'
    if (currentQuery === debouncedQuery.trim() && currentStatus === status && currentDeleted === includeDeleted) return
    const next = new URLSearchParams()
    if (debouncedQuery.trim()) next.set('query', debouncedQuery.trim())
    if (status) next.set('status', status)
    if (includeDeleted) next.set('deleted', '1')
    setSearchParams(next, { replace: true })
  }, [debouncedQuery, includeDeleted, searchParams, setSearchParams, status])

  useEffect(() => {
    if (administrator && !selectedOrganization) {
      setData(null)
      setLoading(false)
      setError(null)
      return
    }
    let active = true
    setLoading(true)
    setError(null)
    searchStudents({
      query: searchParams.get('query') ?? '',
      status: statusFromQuery(searchParams.get('status')),
      includeDeleted: searchParams.get('deleted') === '1',
      page
    }).then((response) => { if (active) setData(response) })
      .catch((requestError) => {
        if (active) setError(requestError instanceof ApiRequestError ? requestError.message : 'No fue posible consultar estudiantes.')
      }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [administrator, page, searchParams, selectedOrganization])

  async function executeAction() {
    if (!pending) return
    setBusy(true)
    try {
      const updated = pending.action === 'ACTIVATE' ? await activateStudent(pending.student.publicId)
        : pending.action === 'DEACTIVATE' ? await deactivateStudent(pending.student.publicId)
          : pending.action === 'SUSPEND' ? await suspendStudent(pending.student.publicId)
            : pending.action === 'ARCHIVE' ? await archiveStudent(pending.student.publicId)
              : await restoreStudent(pending.student.publicId)
      setData((current) => current ? {
        ...current,
        content: current.content.map((item) => item.publicId === updated.publicId ? updated : item)
      } : current)
      toast.success('Estado actualizado', `${updated.displayName} quedó como ${statusLabels[updated.effectiveStatus].toLowerCase()}.`)
      setPending(null)
    } catch (requestError) {
      toast.error('No fue posible actualizar al estudiante', requestError instanceof ApiRequestError ? requestError.message : undefined)
    } finally { setBusy(false) }
  }

  function clearFilters() {
    setQuery('')
    setStatus('')
    setIncludeDeleted(false)
  }

  function goToPage(nextPage: number) {
    const next = new URLSearchParams(searchParams)
    next.set('page', String(nextPage))
    setSearchParams(next)
  }

  return (
    <main className="content-page resource-page ns-list-page student-page">
      <header className="ns-page-header">
        <div>
          <p className="eyebrow">Administración</p>
          <h1>Estudiantes</h1>
          <p className="muted">Administra cuentas, vigencia, estados y sesiones sin mezclarlas con usuarios internos.</p>
        </div>
        {permissions.has('STUDENT_CREATE') && (!administrator || selectedOrganization) && (
          <Link className="primary-button button-link" to="/admin/students/new">
            <Icon name="plus" size={16} /> Crear estudiante
          </Link>
        )}
      </header>

      {administrator && (
        <section className="student-context-card">
          <div>
            <strong>Contexto organizacional</strong>
            <p className="muted">Selecciona la organización cuyos estudiantes deseas administrar.</p>
          </div>
          <select value={selectedOrganization} onChange={(event) => setSelectedOrganization(event.target.value)}>
            <option value="">Seleccionar organización</option>
            {organizations.map((organization) => (
              <option key={organization.publicId} value={organization.publicId}>{organization.name} · {organization.code}</option>
            ))}
          </select>
        </section>
      )}

      {administrator && !selectedOrganization ? (
        <section className="ns-data-panel student-context-empty">
          <Icon name="users" size={32} />
          <h2>Selecciona una organización</h2>
          <p>El contexto evita consultar o modificar estudiantes de otra organización por accidente.</p>
        </section>
      ) : (
        <>
          <FilterToolbar
            resultLabel={`${data?.totalElements ?? 0} ${data?.totalElements === 1 ? 'estudiante' : 'estudiantes'}`}
            hasActiveFilters={Boolean(query || status || includeDeleted)}
            onClear={clearFilters}
          >
            <ResourceSearchField value={query} onChange={setQuery} placeholder="Buscar por nombre, correo o código" />
            <ResourceSelectField label="Estado" value={status} onChange={(value) => {
                const nextStatus = value as StudentEffectiveStatus | ''
                setStatus(nextStatus)
                if (nextStatus === 'DELETED') setIncludeDeleted(true)
              }}>
              {statuses.map((option) => <option key={option.value || 'ALL'} value={option.value}>{option.label}</option>)}
            </ResourceSelectField>
            <label className="student-deleted-filter">
              <input
                type="checkbox"
                checked={includeDeleted}
                onChange={(event) => {
                  const checked = event.target.checked
                  setIncludeDeleted(checked)
                  if (!checked && status === 'DELETED') setStatus('')
                }}
              />
              Mostrar eliminados
            </label>
          </FilterToolbar>

          {error && <div className="error-message">{error}</div>}
          <section className="ns-data-panel" aria-busy={loading}>
            <div className="ns-data-table-wrap">
              <table className="ns-data-table">
                <thead><tr><th>Estudiante</th><th>Código</th><th>Estado</th><th>Vigencia</th><th>Último acceso</th><th className="ns-actions-column">Acciones</th></tr></thead>
                <tbody>
                  {loading && <tr><td colSpan={6} className="ns-table-empty">Cargando estudiantes…</td></tr>}
                  {!loading && data?.content.length === 0 && <tr><td colSpan={6} className="ns-table-empty">No se encontraron estudiantes.</td></tr>}
                  {!loading && data?.content.map((student) => (
                    <tr className={student.status === 'DELETED' ? 'ns-row-muted' : ''} key={student.publicId}>
                      <td className="ns-primary-cell"><strong>{student.displayName}</strong><small>{student.email}</small></td>
                      <td><code>{student.studentCode}</code></td>
                      <td><span className={`status-badge status-${student.effectiveStatus.toLowerCase()}`}>{statusLabels[student.effectiveStatus]}</span></td>
                      <td><small>{formatDate(student.validFrom)}</small><br /><small>{formatDate(student.expiresAt)}</small></td>
                      <td>{formatDate(student.lastLoginAt, 'Sin acceso')}</td>
                      <td className="ns-actions-column">
                        <TableActions>
                          <TableActionLink icon="eye" label="Ver detalle" to={`/admin/students/${student.publicId}`} />
                          {permissions.has('STUDENT_UPDATE') && !['ARCHIVED', 'DELETED'].includes(student.status) && (
                            <TableActionLink icon="edit" label="Editar" to={`/admin/students/${student.publicId}/edit`} />
                          )}
                          {permissions.has('STUDENT_STATUS_CHANGE') && student.status !== 'ACTIVE' && student.status !== 'DELETED' && (
                            <TableActionButton icon="check" label="Activar" onClick={() => setPending({ action: 'ACTIVATE', student })} />
                          )}
                          {permissions.has('STUDENT_STATUS_CHANGE') && student.status === 'ACTIVE' && (
                            <TableActionButton icon="warning" label="Desactivar" onClick={() => setPending({ action: 'DEACTIVATE', student })} />
                          )}
                          {permissions.has('STUDENT_STATUS_CHANGE') && !['SUSPENDED', 'DELETED', 'ARCHIVED'].includes(student.status) && (
                            <TableActionButton icon="warning" label="Suspender" onClick={() => setPending({ action: 'SUSPEND', student })} />
                          )}
                          {permissions.has('STUDENT_STATUS_CHANGE') && !['ARCHIVED', 'DELETED'].includes(student.status) && (
                            <TableActionButton icon="archive" label="Archivar" onClick={() => setPending({ action: 'ARCHIVE', student })} />
                          )}
                          {permissions.has('STUDENT_STATUS_CHANGE') && ['ARCHIVED', 'DELETED'].includes(student.status) && (
                            <TableActionButton icon="restore" label="Restaurar" onClick={() => setPending({ action: 'RESTORE', student })} />
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
                <button className="secondary-button" disabled={page <= 0} onClick={() => goToPage(page - 1)}>Anterior</button>
                <span>Página {page + 1} de {data.totalPages}</span>
                <button className="secondary-button" disabled={page + 1 >= data.totalPages} onClick={() => goToPage(page + 1)}>Siguiente</button>
              </div>
            )}
          </section>
        </>
      )}

      <ConfirmDialog
        open={Boolean(pending)}
        title={pending?.action === 'ACTIVATE' ? 'Activar estudiante' : pending?.action === 'DEACTIVATE' ? 'Desactivar estudiante' : pending?.action === 'SUSPEND' ? 'Suspender estudiante' : pending?.action === 'ARCHIVE' ? 'Archivar estudiante' : 'Restaurar estudiante'}
        description={pending?.action === 'ACTIVATE'
          ? 'El estudiante podrá autenticarse cuando su vigencia esté activa. La asignación de asiento se integrará en la Parte 3.'
          : pending?.action === 'RESTORE' ? 'El estudiante se restaurará como inactivo y conservará su historial.'
            : 'La pérdida de acceso será inmediata y cualquier sesión activa se revocará.'}
        confirmLabel="Confirmar"
        tone={pending?.action === 'SUSPEND' || pending?.action === 'ARCHIVE' ? 'danger' : 'primary'}
        busy={busy}
        onCancel={() => setPending(null)}
        onConfirm={() => void executeAction()}
      />
    </main>
  )
}
