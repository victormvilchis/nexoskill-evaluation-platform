import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import {
  changeCatalogItemStatus,
  createCatalogItem,
  deleteCatalogItem,
  getCatalogDependencies,
  getCatalogItem,
  getCatalogItems,
  getCatalogTypes,
  updateCatalogItem
} from '../features/catalogs/api/catalogApi'
import type {
  CatalogDependencies,
  CatalogItem,
  CatalogPayload,
  CatalogType,
  CatalogTypeSummary,
  ManagedCatalogStatus
} from '../features/catalogs/types/catalogs'
import { searchOrganizations } from '../features/organizations/api/organizationApi'
import type { OrganizationSummary } from '../features/organizations/types/organizations'
import { ApiRequestError } from '../shared/api/apiClient'
import { ConfirmDialog } from '../shared/components/ConfirmDialog'
import { FilterToolbar } from '../shared/components/FilterToolbar'
import { Icon } from '../shared/components/Icon'
import { ResourceSearchField, ResourceSelectField } from '../shared/components/ResourceFilters'
import { SelectField } from '../shared/components/SelectField'
import { TableActionButton, TableActions } from '../shared/components/TableActions'
import { TablePagination } from '../shared/components/TablePagination'
import { useClientPagination } from '../shared/hooks/useClientPagination'
import { parsePage, parsePageSize, type PageSize } from '../shared/types/pagination'
import { useToast } from '../shared/components/ToastProvider'
import { useDebouncedValue } from '../shared/hooks/useDebouncedValue'
import { useAuth } from '../features/authentication/context/AuthContext'

type DialogMode = 'create' | 'view' | 'edit'
type StatusFilter = ManagedCatalogStatus | 'ALL'

const catalogTypes = new Set<CatalogType>([
  'CATEGORIES',
  'TECHNOLOGIES',
  'PROFESSIONAL_PROFILES',
  'TECHNOLOGICAL_PROFILES'
])

function formatDate(value?: string) {
  if (!value) return 'Sin registro'
  return new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium', timeStyle: 'short' })
    .format(new Date(value))
}

function getDialogMode(pathname: string, id?: string): DialogMode | undefined {
  if (pathname.endsWith('/new')) return 'create'
  if (pathname.endsWith('/edit')) return 'edit'
  if (id) return 'view'
  return undefined
}

export function CatalogItemsPage() {
  const params = useParams<{ type: string; id?: string }>()
  const location = useLocation()
  const navigate = useNavigate()
  const toast = useToast()
  const { user } = useAuth()
  const permissions = useMemo(() => new Set(user?.permissions ?? []), [user?.permissions])
  const canManage = permissions.has('CATALOG_MANAGE')
  const globalAdministrator = user?.roles.includes('ADMINISTRATOR') ?? false
  const [searchParams, setSearchParams] = useSearchParams()
  const type = params.type?.toUpperCase() as CatalogType
  const validType = catalogTypes.has(type)
  const mode = getDialogMode(location.pathname, params.id)
  const listPath = validType ? `/admin/catalogs/${type}` : '/admin/catalogs/CATEGORIES'

  const [summary, setSummary] = useState<CatalogTypeSummary>()
  const [items, setItems] = useState<CatalogItem[]>([])
  const [organizations, setOrganizations] = useState<OrganizationSummary[]>([])
  const [technologicalProfiles, setTechnologicalProfiles] = useState<CatalogItem[]>([])
  const [status, setStatus] = useState<StatusFilter>('ACTIVE')
  const [organizationPublicId, setOrganizationPublicId] = useState('')
  const [query, setQuery] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string>()
  const [reloadKey, setReloadKey] = useState(0)
  const [selected, setSelected] = useState<CatalogItem>()
  const [statusCandidate, setStatusCandidate] = useState<{ item: CatalogItem; action: 'activate' | 'deactivate' }>()
  const [deleteCandidate, setDeleteCandidate] = useState<CatalogItem>()
  const [busy, setBusy] = useState(false)
  const [form, setForm] = useState<CatalogPayload>({ code: '', name: '', description: '', displayOrder: 0 })
  const debouncedQuery = useDebouncedValue(query, 250)
  const paginationResetKey = `${debouncedQuery}\u0000${organizationPublicId}\u0000${status}`
  const previousPaginationResetKey = useRef(paginationResetKey)
  const page = parsePage(searchParams.get('page'))
  const size = parsePageSize(searchParams.get('size'))
  const reload = useCallback(() => setReloadKey((value) => value + 1), [])

  useEffect(() => {
    if (!validType) navigate('/admin/catalogs/CATEGORIES', { replace: true })
  }, [navigate, validType])

  useEffect(() => {
    if (!validType) return

    const controller = new AbortController()
    const requestedStatus: StatusFilter = mode && mode !== 'create' ? 'ALL' : status

    setLoading(true)
    setError(undefined)

    Promise.all([
      getCatalogTypes(controller.signal),
      getCatalogItems(type, {
        status: requestedStatus,
        organizationPublicId: globalAdministrator ? organizationPublicId || undefined : undefined,
        signal: controller.signal
      }),
      globalAdministrator
        ? searchOrganizations({ status: 'ALL', size: 100, signal: controller.signal }).then((page) => page.content)
        : Promise.resolve([] as OrganizationSummary[]),
      type === 'PROFESSIONAL_PROFILES'
        ? getCatalogItems('TECHNOLOGICAL_PROFILES', { status: 'ACTIVE', signal: controller.signal })
        : Promise.resolve([] as CatalogItem[]),
      params.id ? getCatalogItem(type, params.id, controller.signal) : Promise.resolve(undefined)
    ])
      .then(([types, values, organizationValues, techProfiles, requestedItem]) => {
        setSummary(types.find((item) => item.type === type))
        setItems(values)
        setOrganizations(organizationValues)
        setTechnologicalProfiles(techProfiles)

        if (mode === 'create') {
          setSelected(undefined)
          setForm({
            code: '',
            name: '',
            description: '',
            displayOrder: 0,
            organizationPublicId: organizationPublicId || undefined
          })
          return
        }

        if (params.id) {
          if (!requestedItem) {
            toast.error('No fue posible abrir el registro', 'El registro solicitado no existe o no está disponible en este contexto.')
            navigate(listPath, { replace: true })
            return
          }

          setSelected(requestedItem)
          setForm({
            code: requestedItem.code,
            name: requestedItem.name,
            description: requestedItem.description ?? '',
            displayOrder: requestedItem.displayOrder,
            organizationPublicId: requestedItem.organizationPublicId,
            suggestedTechnologicalProfile: requestedItem.suggestedTechnologicalProfile,
            expectedVersion: requestedItem.version
          })
        } else {
          setSelected(undefined)
        }
      })
      .catch((requestError: unknown) => {
        if (controller.signal.aborted) return
        setError(requestError instanceof ApiRequestError
          ? requestError.message
          : 'No fue posible consultar el catálogo.')
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })

    return () => controller.abort()
  }, [globalAdministrator, listPath, mode, navigate, organizationPublicId, params.id, reloadKey, status, toast, type, validType])

  const filtered = useMemo(() => {
    const term = debouncedQuery.trim().toLocaleLowerCase('es-MX')
    if (!term) return items
    return items.filter((item) => `${item.code} ${item.name} ${item.description ?? ''} ${item.organizationName ?? ''}`
      .toLocaleLowerCase('es-MX').includes(term))
  }, [debouncedQuery, items])
  const pageData = useClientPagination(filtered, page, size)

  useEffect(() => {
    if (pageData.page === page) return
    const next = new URLSearchParams(searchParams)
    if (pageData.page > 0) next.set('page', String(pageData.page))
    else next.delete('page')
    setSearchParams(next, { replace: true })
  }, [page, pageData.page, searchParams, setSearchParams])

  useEffect(() => {
    if (previousPaginationResetKey.current === paginationResetKey) return
    previousPaginationResetKey.current = paginationResetKey

    setSearchParams((current) => {
      if (!current.has('page')) return current
      const next = new URLSearchParams(current)
      next.delete('page')
      return next
    }, { replace: true })
  }, [paginationResetKey, setSearchParams])

  function setPage(nextPage: number) {
    const next = new URLSearchParams(searchParams)
    if (nextPage > 0) next.set('page', String(nextPage))
    else next.delete('page')
    setSearchParams(next)
  }

  function setPageSize(nextSize: PageSize) {
    const next = new URLSearchParams(searchParams)
    next.delete('page')
    if (nextSize === 10) next.delete('size')
    else next.set('size', String(nextSize))
    setSearchParams(next)
  }

  function openCreate() {
    navigate(`${listPath}/new`)
  }

  function open(modeToOpen: Exclude<DialogMode, 'create'>, item: CatalogItem) {
    const suffix = modeToOpen === 'view' ? '' : `/${modeToOpen}`
    navigate(`${listPath}/${item.id}${suffix}`)
  }

  function close() {
    if (busy) return
    navigate(listPath)
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (busy) return

    setBusy(true)
    try {
      const payload: CatalogPayload = {
        ...form,
        code: form.code.trim(),
        name: form.name.trim(),
        description: form.description?.trim() || undefined,
        organizationPublicId: globalAdministrator ? form.organizationPublicId : undefined,
        expectedVersion: selected?.version
      }

      if (selected) await updateCatalogItem(type, selected.id, payload)
      else await createCatalogItem(type, payload)

      toast.success(selected ? 'Registro actualizado correctamente' : 'Registro creado correctamente')
      setSelected(undefined)
      reload()
      navigate(listPath, { replace: true })
    } catch (requestError) {
      toast.error(
        'No fue posible guardar el registro',
        requestError instanceof ApiRequestError ? requestError.message : undefined
      )
    } finally {
      setBusy(false)
    }
  }

  async function confirmStatusChange() {
    if (!statusCandidate || busy) return

    setBusy(true)
    try {
      await changeCatalogItemStatus(
        type,
        statusCandidate.item.id,
        statusCandidate.action,
        statusCandidate.item.version
      )
      toast.success(statusCandidate.action === 'activate'
        ? 'Registro activado correctamente.'
        : 'El registro fue inactivado y ya no estará disponible para nuevas asignaciones.')
      setStatusCandidate(undefined)
      reload()
    } catch (requestError) {
      toast.error('No fue posible cambiar el estado', requestError instanceof ApiRequestError ? requestError.message : undefined)
    } finally {
      setBusy(false)
    }
  }

  async function requestDelete(item: CatalogItem) {
    if (busy) return

    setBusy(true)
    try {
      const dependencyResult: CatalogDependencies = await getCatalogDependencies(type, item.id)
      if (!dependencyResult.deletable || dependencyResult.total > 0) {
        toast.error(
          'No es posible eliminar este registro porque actualmente está siendo utilizado. Puedes inactivarlo para evitar que esté disponible en nuevos registros.',
          dependencyResult.details.join(' ') || undefined
        )
        return
      }
      setDeleteCandidate(item)
    } catch (requestError) {
      toast.error('No fue posible validar el uso del registro', requestError instanceof ApiRequestError ? requestError.message : undefined)
    } finally {
      setBusy(false)
    }
  }

  async function confirmDelete() {
    if (!deleteCandidate || busy) return

    setBusy(true)
    try {
      await deleteCatalogItem(type, deleteCandidate.id, deleteCandidate.version)
      toast.success('El registro fue eliminado definitivamente.')
      setDeleteCandidate(undefined)
      reload()
    } catch (requestError) {
      toast.error('No fue posible eliminar el registro', requestError instanceof ApiRequestError ? requestError.message : undefined)
    } finally {
      setBusy(false)
    }
  }

  if (!validType) return null

  return (
    <main className="content-page resource-page ns-list-page catalog-items-page">
      {!mode && canManage && (
        <div className="ns-list-action-bar" aria-label={`Acciones de ${summary?.name ?? 'catálogo'}`}>
          <button className="primary-button ns-create-button" type="button" onClick={openCreate}>
            <Icon name="plus" size={15} /> Nuevo registro
          </button>
        </div>
      )}

      <FilterToolbar
        resultLabel={`${filtered.length} ${filtered.length === 1 ? 'registro' : 'registros'}`}
        hasActiveFilters={Boolean(query || status !== 'ACTIVE' || organizationPublicId)}
        onClear={() => { setQuery(''); setStatus('ACTIVE'); setOrganizationPublicId('') }}
      >
        <ResourceSearchField value={query} onChange={setQuery} placeholder="Buscar por nombre, código o descripción" />
        {globalAdministrator && (
          <ResourceSelectField label="Organización" value={organizationPublicId} onChange={setOrganizationPublicId}>
            <option value="">Todas las organizaciones</option>
            {organizations.map((organization) => (
              <option key={organization.publicId} value={organization.publicId}>{organization.name}</option>
            ))}
          </ResourceSelectField>
        )}
        <ResourceSelectField label="Estado" value={status} onChange={(value) => setStatus(value as StatusFilter)}>
          <option value="ACTIVE">Activos</option>
          <option value="INACTIVE">Inactivos</option>
          <option value="ALL">Todos</option>
        </ResourceSelectField>
      </FilterToolbar>

      {error && (
        <section className="inline-error-panel" role="alert">
          <div className="inline-error-icon"><Icon name="error" /></div>
          <div><strong>No fue posible cargar el catálogo</strong><p>{error}</p></div>
          <button className="secondary-button compact-button" type="button" onClick={reload}>Reintentar</button>
        </section>
      )}

      <section className="ns-data-panel" aria-busy={loading}>
        <div className="ns-data-table-wrap">
          <table className="ns-data-table">
            <thead>
              <tr>
                <th>Nombre</th>
                <th>Código</th>
                {globalAdministrator && <th>Organización</th>}
                <th>Actualización</th>
                <th>Estado</th>
                <th className="ns-actions-column">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {loading && (
                <tr><td className="ns-table-empty" colSpan={globalAdministrator ? 6 : 5}>Cargando registros…</td></tr>
              )}
              {!loading && filtered.length === 0 && (
                <tr>
                  <td className="ns-table-empty" colSpan={globalAdministrator ? 6 : 5}>
                    <strong>No hay registros</strong>
                    <span>{canManage ? 'Ajusta los filtros o crea un valor nuevo.' : 'Ajusta los filtros para consultar los registros disponibles.'}</span>
                  </td>
                </tr>
              )}
              {!loading && pageData.content.map((item) => (
                <tr key={item.id}>
                  <td className="ns-primary-cell"><strong>{item.name}</strong><small>{item.description || 'Sin descripción'}</small></td>
                  <td><code>{item.code}</code></td>
                  {globalAdministrator && <td>{item.organizationName ?? 'GLOBAL'}</td>}
                  <td>{formatDate(item.updatedAt ?? item.createdAt)}</td>
                  <td><span className={`status-badge status-${item.status.toLowerCase()}`}>{item.status === 'ACTIVE' ? 'Activo' : 'Inactivo'}</span></td>
                  <td>
                    <TableActions>
                      <TableActionButton label="Ver" icon="eye" onClick={() => open('view', item)} />
                      {canManage && <TableActionButton label="Editar" icon="edit" tone="primary" onClick={() => open('edit', item)} />}
                      {canManage && item.status === 'ACTIVE' && <TableActionButton label="Inactivar" icon="archive" disabled={busy} onClick={() => setStatusCandidate({ item, action: 'deactivate' })} />}
                      {canManage && item.status === 'INACTIVE' && <TableActionButton label="Activar" icon="restore" tone="primary" disabled={busy} onClick={() => setStatusCandidate({ item, action: 'activate' })} />}
                      {canManage && <TableActionButton label="Eliminar" icon="trash" tone="danger" disabled={busy} onClick={() => void requestDelete(item)} />}
                    </TableActions>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
<TablePagination
          currentPage={pageData.page}
          pageSize={size}
          totalElements={pageData.totalElements}
          totalPages={pageData.totalPages}
          onPageChange={setPage}
          onPageSizeChange={setPageSize}
          isLoading={loading}
          compact
        />
      </section>

      {mode && (
        <div className="ns-dialog-backdrop" role="presentation" onMouseDown={(event) => { if (event.currentTarget === event.target) close() }}>
          <section className="ns-resource-dialog catalog-dialog" role="dialog" aria-modal="true" aria-labelledby="catalog-dialog-title">
            <header>
              <div>
                <p className="eyebrow">{mode === 'create' ? 'Nuevo registro' : mode === 'edit' ? 'Editar' : 'Ver'}</p>
                <h2 id="catalog-dialog-title">{selected?.name ?? summary?.name}</h2>
              </div>
              <button aria-label="Cerrar" className="ns-dialog-close" disabled={busy} type="button" onClick={close}>
                <Icon name="close" size={17} />
              </button>
            </header>

            {(mode === 'create' || mode === 'edit') && canManage && (
              <form className="ns-dialog-form" onSubmit={(event) => void submit(event)}>
                <div className="catalog-form-grid">
                  <label className="ns-dialog-field">
                    <span>Nombre</span>
                    <input autoFocus maxLength={200} required value={form.name} onChange={(event) => setForm((value) => ({ ...value, name: event.target.value }))} />
                  </label>
                  <label className="ns-dialog-field">
                    <span>Código</span>
                    <input maxLength={120} required disabled={mode === 'edit'} value={form.code} onChange={(event) => setForm((value) => ({ ...value, code: event.target.value }))} />
                  </label>
                  <label className="ns-dialog-field">
                    <span>Orden</span>
                    <input min={0} type="number" value={form.displayOrder ?? 0} onChange={(event) => setForm((value) => ({ ...value, displayOrder: Number(event.target.value) }))} />
                  </label>
                  {globalAdministrator && (
                    <label className="ns-dialog-field">
                      <span>Organización propietaria</span>
                      <SelectField disabled={mode === 'edit'} value={form.organizationPublicId ?? ''}
                        onChange={(nextValue) => setForm((value) => ({ ...value, organizationPublicId: nextValue || undefined }))}
                        ariaLabel="Organización propietaria"
                        options={[
                          { value: '', label: 'GLOBAL' },
                          ...organizations
                            .filter((organization) => organization.organizationType === 'CUSTOMER' && organization.status === 'ACTIVE')
                            .map((organization) => ({ value: organization.publicId, label: organization.name }))
                        ]} />
                    </label>
                  )}
                  {type === 'PROFESSIONAL_PROFILES' && (
                    <label className="ns-dialog-field">
                      <span>Perfil tecnológico sugerido</span>
                      <SelectField value={form.suggestedTechnologicalProfile ?? ''}
                        onChange={(nextValue) => setForm((value) => ({ ...value, suggestedTechnologicalProfile: nextValue || undefined }))}
                        ariaLabel="Perfil tecnológico sugerido"
                        options={[
                          { value: '', label: 'Sin sugerencia' },
                          ...technologicalProfiles
                            .filter((profile) => profile.scope === 'GLOBAL'
                              || (!globalAdministrator)
                              || Boolean(form.organizationPublicId && profile.organizationPublicId === form.organizationPublicId))
                            .map((profile) => ({ value: profile.code, label: profile.name }))
                        ]} />
                    </label>
                  )}
                  <label className="ns-dialog-field catalog-description-field">
                    <span>Descripción</span>
                    <textarea maxLength={500} rows={4} value={form.description ?? ''} onChange={(event) => setForm((value) => ({ ...value, description: event.target.value }))} />
                  </label>
                </div>
                <footer>
                  <button className="secondary-button" disabled={busy} type="button" onClick={close}>Cancelar</button>
                  <button className="primary-button" disabled={busy || !form.name.trim() || !form.code.trim()} type="submit">
                    {busy ? 'Guardando…' : mode === 'edit' ? 'Guardar cambios' : 'Crear registro'}
                  </button>
                </footer>
              </form>
            )}

            {mode === 'view' && selected && (
              <>
                <div className="catalog-readonly-grid">
                  <div><span>Nombre</span><strong>{selected.name}</strong></div>
                  <div><span>Código</span><strong>{selected.code}</strong></div>
                  <div><span>Estado</span><strong>{selected.status === 'ACTIVE' ? 'Activo' : 'Inactivo'}</strong></div>
                  <div><span>Orden</span><strong>{selected.displayOrder}</strong></div>
                  <div><span>Usos o dependencias</span><strong>{selected.dependencyCount}</strong></div>
                  <div><span>Creación</span><strong>{formatDate(selected.createdAt)}</strong></div>
                  {selected.organizationName && <div><span>Organización</span><strong>{selected.organizationName}</strong></div>}
                  <div className="catalog-readonly-wide"><span>Descripción</span><strong>{selected.description || 'Sin descripción'}</strong></div>
                </div>
                <footer><button className="secondary-button" type="button" onClick={close}>Volver</button></footer>
              </>
            )}

          </section>
        </div>
      )}

      <ConfirmDialog
        open={Boolean(statusCandidate)}
        title={statusCandidate?.action === 'activate' ? 'Activar registro' : 'Inactivar registro'}
        description={statusCandidate?.action === 'activate'
          ? 'Este registro volverá a estar disponible para nuevas asignaciones. ¿Deseas continuar?'
          : 'Este registro dejará de estar disponible para nuevas asignaciones, pero se conservará en los registros donde ya está siendo utilizado. ¿Deseas continuar?'}
        confirmLabel={statusCandidate?.action === 'activate' ? 'Confirmar activación' : 'Confirmar inactivación'}
        busy={busy}
        onCancel={() => setStatusCandidate(undefined)}
        onConfirm={() => void confirmStatusChange()}
      />

      <ConfirmDialog
        open={Boolean(deleteCandidate)}
        title="Eliminar registro"
        description="Esta acción eliminará permanentemente el registro del catálogo. ¿Deseas continuar?"
        confirmLabel="Confirmar eliminación"
        tone="danger"
        busy={busy}
        onCancel={() => setDeleteCandidate(undefined)}
        onConfirm={() => void confirmDelete()}
      />
    </main>
  )
}
