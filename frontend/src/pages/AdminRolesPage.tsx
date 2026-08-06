import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ApiRequestError } from '../shared/api/apiClient'
import { changeRoleStatus, cloneRole, deleteRole, listRoles } from '../features/roles/api/roleApi'
import { ConfirmDialog } from '../shared/components/ConfirmDialog'
import { FilterToolbar } from '../shared/components/FilterToolbar'
import { Icon } from '../shared/components/Icon'
import { ResourceSearchField, ResourceSelectField } from '../shared/components/ResourceFilters'
import { TableActionButton, TableActionLink, TableActions } from '../shared/components/TableActions'
import { TablePagination } from '../shared/components/TablePagination'
import { useToast } from '../shared/components/ToastProvider'
import { useDebouncedValue } from '../shared/hooks/useDebouncedValue'
import { normalizePagedResponse, parsePage, parsePageSize, type PagedResponse, type PageSize } from '../shared/types/pagination'
import type { RoleSummary } from '../shared/types/roles'

type RoleStatusFilter = 'ACTIVE' | 'INACTIVE' | 'ALL'
type PendingAction =
  | { type: 'clone'; role: RoleSummary }
  | { type: 'status'; role: RoleSummary; status: 'ACTIVE' | 'INACTIVE' }
  | { type: 'delete'; role: RoleSummary }

function validStatus(value: string | null): RoleStatusFilter {
  return value === 'INACTIVE' || value === 'ALL' ? value : 'ACTIVE'
}

export function AdminRolesPage() {
  const toast = useToast()
  const [searchParams, setSearchParams] = useSearchParams()
  const [query, setQuery] = useState(searchParams.get('query') ?? '')
  const [status, setStatus] = useState<RoleStatusFilter>(validStatus(searchParams.get('status')))
  const [data, setData] = useState<PagedResponse<RoleSummary> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [reloadKey, setReloadKey] = useState(0)
  const [pending, setPending] = useState<PendingAction | null>(null)
  const [cloneName, setCloneName] = useState('')
  const [busy, setBusy] = useState(false)
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
    setError('')
    listRoles({
      query: searchParams.get('query') ?? '',
      status: validStatus(searchParams.get('status')),
      page,
      size,
      signal: controller.signal
    }).then((response) => {
      const normalized = normalizePagedResponse(response)
      setData(normalized)
      if (normalized.totalPages > 0 && page >= normalized.totalPages) goToPage(normalized.totalPages - 1)
    }).catch((requestError) => {
      if (!controller.signal.aborted) {
        setError(requestError instanceof ApiRequestError
          ? requestError.message
          : 'No fue posible consultar los roles.')
      }
    }).finally(() => {
      if (!controller.signal.aborted) setLoading(false)
    })
    return () => controller.abort()
  }, [page, reloadKey, searchParams, size])

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

  function clearFilters() {
    setQuery('')
    setStatus('ACTIVE')
  }

  function openClone(role: RoleSummary) {
    setCloneName(`Copia de ${role.name}`)
    setPending({ type: 'clone', role })
  }

  async function confirmAction() {
    if (!pending || busy) return
    setBusy(true)
    try {
      if (pending.type === 'clone') {
        await cloneRole(pending.role.code, { name: cloneName.trim() })
        toast.success('Rol clonado', 'El rol y todos sus permisos se clonaron correctamente.')
      } else if (pending.type === 'status') {
        await changeRoleStatus(pending.role.code, pending.status)
        toast.success(
          pending.status === 'ACTIVE' ? 'Rol activado' : 'Rol inactivado',
          pending.status === 'ACTIVE'
            ? 'El rol fue activado correctamente.'
            : 'El rol fue inactivado correctamente.'
        )
      } else {
        await deleteRole(pending.role.code)
        toast.success('Rol eliminado', 'El rol y su configuración de permisos fueron eliminados definitivamente.')
      }
      setPending(null)
      setReloadKey((value) => value + 1)
    } catch (requestError) {
      toast.error(
        'No fue posible completar la acción.',
        requestError instanceof ApiRequestError ? requestError.message : undefined
      )
    } finally {
      setBusy(false)
    }
  }

  const dialog = pending && (() => {
    if (pending.type === 'clone') {
      return {
        title: 'Clonar rol',
        description: 'Se copiarán todos los permisos del rol. La copia será independiente.',
        confirmLabel: 'Clonar rol',
        tone: 'primary' as const
      }
    }
    if (pending.type === 'delete') {
      return pending.role.assignedUsers > 0
        ? {
            title: 'No es posible eliminar el rol',
            description: 'No es posible eliminar este rol porque tiene usuarios asignados. Reasigna primero a los usuarios a otro rol activo.',
            confirmLabel: 'Eliminar definitivamente',
            tone: 'danger' as const
          }
        : {
            title: 'Eliminar rol',
            description: 'Esta acción eliminará permanentemente el rol y toda su configuración de permisos. ¿Deseas continuar?',
            confirmLabel: 'Eliminar definitivamente',
            tone: 'danger' as const
          }
    }
    return {
      title: pending.status === 'ACTIVE' ? 'Activar rol' : 'Inactivar rol',
      description: pending.status === 'ACTIVE'
        ? 'El rol recuperará sus permisos y volverá a estar disponible para asignación.'
        : 'El rol dejará de estar disponible y sus usuarios no podrán utilizar sus permisos mientras permanezca inactivo.',
      confirmLabel: pending.status === 'ACTIVE' ? 'Activar' : 'Inactivar',
      tone: 'primary' as const
    }
  })()

  return (
    <main className="content-page resource-page ns-list-page role-management-page">
      <header className="role-list-header">
        <div>
          <span className="eyebrow">Administración</span>
          <h1>Roles</h1>
          <p>Configura módulos y acciones por rol. El Administrador conserva acceso completo y no es configurable.</p>
        </div>
        <Link className="primary-button button-link ns-create-button" to="/admin/roles/new">
          <Icon name="plus" size={15} /> Crear rol
        </Link>
      </header>

      <FilterToolbar hasActiveFilters={Boolean(query || status !== 'ACTIVE')} onClear={clearFilters}>
        <ResourceSearchField value={query} onChange={setQuery} placeholder="Buscar por nombre del rol" />
        <ResourceSelectField label="Estado" value={status} onChange={(value) => setStatus(value as RoleStatusFilter)}>
          <option value="ACTIVE">Activos</option>
          <option value="INACTIVE">Inactivos</option>
          <option value="ALL">Todos</option>
        </ResourceSelectField>
      </FilterToolbar>

      {error && (
        <section className="inline-error-panel" role="alert">
          <Icon name="error" />
          <div>
            <strong>No fue posible cargar los roles</strong>
            <p>{error}</p>
          </div>
          <button
            className="secondary-button compact-button"
            type="button"
            onClick={() => setReloadKey((value) => value + 1)}
          >
            Reintentar
          </button>
        </section>
      )}

      <section className="ns-data-panel role-list-panel" aria-busy={loading}>
        <div className="ns-data-table-wrap">
          <table className="ns-data-table role-table">
            <colgroup>
              <col />
              <col className="role-status-col" />
              <col className="role-actions-col" />
            </colgroup>
            <thead>
              <tr>
                <th>Rol</th>
                <th className="role-status-column">Estado</th>
                <th className="ns-actions-column role-actions-column">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {loading && (
                <tr><td colSpan={3} className="ns-table-empty">Cargando roles…</td></tr>
              )}
              {!loading && (data?.content.length ?? 0) === 0 && (
                <tr><td colSpan={3} className="ns-table-empty">No se encontraron roles.</td></tr>
              )}
              {!loading && data?.content.map((role) => (
                <tr key={role.code}>
                  <td className="ns-primary-cell">
                    <strong>{role.name}</strong>
                    <small>
                      {role.protectedRole
                        ? 'Rol protegido · Acceso completo'
                        : `${role.permissionCount} permisos · ${role.assignedUsers} usuarios`}
                    </small>
                  </td>
                  <td className="role-status-column">
                    <span className={`status-badge ${role.protectedRole
                      ? 'status-protected'
                      : role.status === 'ACTIVE' ? 'status-active' : 'status-inactive'}`}
                    >
                      {role.protectedRole ? 'Protegido' : role.status === 'ACTIVE' ? 'Activo' : 'Inactivo'}
                    </span>
                  </td>
                  <td className="ns-actions-column role-actions-column">
                    <TableActions>
                      {role.protectedRole ? (
                        <TableActionLink to={`/admin/roles/${role.code}`} icon="eye" label="Ver acceso" />
                      ) : (
                        <>
                          <TableActionLink to={`/admin/roles/${role.code}/edit`} icon="edit" label="Configurar permisos" />
                          <TableActionButton icon="copy" label="Clonar" onClick={() => openClone(role)} />
                          <TableActionButton
                            icon={role.status === 'ACTIVE' ? 'archive' : 'restore'}
                            label={role.status === 'ACTIVE' ? 'Inactivar' : 'Activar'}
                            onClick={() => setPending({
                              type: 'status',
                              role,
                              status: role.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
                            })}
                          />
                          <TableActionButton
                            icon="trash"
                            label="Eliminar"
                            tone="danger"
                            onClick={() => setPending({ type: 'delete', role })}
                          />
                        </>
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

      {dialog && (
        <ConfirmDialog
          open
          title={dialog.title}
          description={dialog.description}
          confirmLabel={dialog.confirmLabel}
          tone={dialog.tone}
          busy={busy}
          confirmDisabled={(pending?.type === 'clone' && cloneName.trim().length === 0)
            || (pending?.type === 'delete' && pending.role.assignedUsers > 0)}
          onCancel={() => !busy && setPending(null)}
          onConfirm={() => void confirmAction()}
        >
          {pending?.type === 'clone' && (
            <label className="field-group">
              <span>Nombre del nuevo rol</span>
              <input value={cloneName} maxLength={100} onChange={(event) => setCloneName(event.target.value)} />
            </label>
          )}
          {pending && (
            <div className="role-dialog-summary">
              <strong>{pending.role.name}</strong>
              {pending.type === 'delete' && pending.role.assignedUsers > 0 && (
                <p>Tiene {pending.role.assignedUsers} usuario(s) asignado(s); la eliminación será bloqueada hasta reasignarlos.</p>
              )}
            </div>
          )}
        </ConfirmDialog>
      )}
    </main>
  )
}
