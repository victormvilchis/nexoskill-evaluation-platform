import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import {
  changeCatalogItemStatus,
  createCatalogItem,
  deleteCatalogItem,
  getCatalogDependencies,
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
import { FilterToolbar } from '../shared/components/FilterToolbar'
import { Icon } from '../shared/components/Icon'
import { ResourceSearchField, ResourceSelectField } from '../shared/components/ResourceFilters'
import { TableActionButton, TableActions } from '../shared/components/TableActions'
import { TablePagination } from '../shared/components/TablePagination'
import { useClientPagination } from '../shared/hooks/useClientPagination'
import { parsePage, parsePageSize, type PageSize } from '../shared/types/pagination'
import { useToast } from '../shared/components/ToastProvider'
import { useDebouncedValue } from '../shared/hooks/useDebouncedValue'

type DialogMode = 'create' | 'view' | 'edit' | 'manage'
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
  if (pathname.endsWith('/manage')) return 'manage'
  if (id) return 'view'
  return undefined
}

export function CatalogItemsPage() {
  const params = useParams<{ type: string; id?: string }>()
  const location = useLocation()
  const navigate = useNavigate()
  const toast = useToast()
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
  const [dependencies, setDependencies] = useState<CatalogDependencies>()
  const [busy, setBusy] = useState(false)
  const [form, setForm] = useState<CatalogPayload>({ code: '', name: '', description: '', displayOrder: 0 })
  const debouncedQuery = useDebouncedValue(query, 250)
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
        organizationPublicId: type === 'CATEGORIES' ? organizationPublicId || undefined : undefined,
        signal: controller.signal
      }),
      type === 'CATEGORIES'
        ? searchOrganizations({ status: 'ALL', size: 100, signal: controller.signal }).then((page) => page.content)
        : Promise.resolve([] as OrganizationSummary[]),
      type === 'PROFESSIONAL_PROFILES'
        ? getCatalogItems('TECHNOLOGICAL_PROFILES', { status: 'ACTIVE', signal: controller.signal })
        : Promise.resolve([] as CatalogItem[])
    ])
      .then(([types, values, organizationValues, techProfiles]) => {
        setSummary(types.find((item) => item.type === type))
        setItems(values)
        setOrganizations(organizationValues)
        setTechnologicalProfiles(techProfiles)

        if (mode === 'create') {
          setSelected(undefined)
          setDependencies(undefined)
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
          const currentItem = values.find((item) => String(item.id) === params.id)
          if (!currentItem) {
            toast.error('No fue posible abrir el registro', 'El registro solicitado no existe o no está disponible en este contexto.')
            navigate(listPath, { replace: true })
            return
          }

          setSelected(currentItem)
          setForm({
            code: currentItem.code,
            name: currentItem.name,
            description: currentItem.description ?? '',
            displayOrder: currentItem.displayOrder,
            organizationPublicId: currentItem.organizationPublicId,
            suggestedTechnologicalProfile: currentItem.suggestedTechnologicalProfile,
            expectedVersion: currentItem.version
          })
        } else {
          setSelected(undefined)
          setDependencies(undefined)
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
  }, [listPath, mode, navigate, organizationPublicId, params.id, reloadKey, status, toast, type, validType])

  useEffect(() => {
    if (mode !== 'manage' || !selected) {
      setDependencies(undefined)
      return
    }

    let active = true
    setDependencies(undefined)
    getCatalogDependencies(type, selected.id)
      .then((value) => {
        if (active) setDependencies(value)
      })
      .catch((requestError: unknown) => {
        if (!active) return
        toast.error(
          'No fue posible cargar las dependencias',
          requestError instanceof ApiRequestError ? requestError.message : undefined
        )
      })

    return () => {
      active = false
    }
  }, [mode, selected, toast, type])

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
    if (!searchParams.has('page')) return
    const next = new URLSearchParams(searchParams)
    next.delete('page')
    setSearchParams(next, { replace: true })
  }, [debouncedQuery, organizationPublicId, searchParams, setSearchParams, status])

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
        organizationPublicId: type === 'CATEGORIES' ? form.organizationPublicId : undefined,
        expectedVersion: selected?.version
      }

      if (selected) await updateCatalogItem(type, selected.id, payload)
      else await createCatalogItem(type, payload)

      toast.success(selected ? 'Registro actualizado correctamente' : 'Registro creado correctamente')
      setSelected(undefined)
      setDependencies(undefined)
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

  async function changeStatus(action: 'activate' | 'deactivate') {
    if (!selected || busy) return

    setBusy(true)
    try {
      await changeCatalogItemStatus(type, selected.id, action, selected.version)
      toast.success(action === 'activate' ? 'Registro activado correctamente' : 'Registro inactivado correctamente')
      setSelected(undefined)
      setDependencies(undefined)
      reload()
      navigate(listPath, { replace: true })
    } catch (requestError) {
      toast.error('No fue posible cambiar el estado', requestError instanceof ApiRequestError ? requestError.message : undefined)
    } finally {
      setBusy(false)
    }
  }

  async function remove() {
    if (!selected || busy) return

    setBusy(true)
    try {
      await deleteCatalogItem(type, selected.id, selected.version)
      toast.success('Registro eliminado correctamente')
      setSelected(undefined)
      setDependencies(undefined)
      reload()
      navigate(listPath, { replace: true })
    } catch (requestError) {
      toast.error('No fue posible eliminar el registro', requestError instanceof ApiRequestError ? requestError.message : undefined)
    } finally {
      setBusy(false)
    }
  }

  if (!validType) return null

  return (
    <main className="content-page resource-page ns-list-page catalog-items-page">
      <header className="ns-page-header">
        <div>
          <p className="eyebrow">Administración · Catálogos</p>
          <h1>{summary?.name ?? 'Catálogo'}</h1>
          <p className="muted">{summary?.description}</p>
        </div>
        {!mode && (
          <div className="catalog-header-actions">
            <button className="primary-button" type="button" onClick={openCreate}>
              <Icon name="plus" size={16} /> Nuevo registro
            </button>
          </div>
        )}
      </header>

      <FilterToolbar
        resultLabel={`${filtered.length} ${filtered.length === 1 ? 'registro' : 'registros'}`}
        hasActiveFilters={Boolean(query || status !== 'ACTIVE' || organizationPublicId)}
        onClear={() => { setQuery(''); setStatus('ACTIVE'); setOrganizationPublicId('') }}
      >
        <ResourceSearchField value={query} onChange={setQuery} placeholder="Buscar por nombre, código o descripción" />
        {type === 'CATEGORIES' && (
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
                {type === 'CATEGORIES' && <th>Organización</th>}
                <th>Usos</th>
                <th>Actualización</th>
                <th>Estado</th>
                <th className="ns-actions-column">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {loading && (
                <tr><td className="ns-table-empty" colSpan={type === 'CATEGORIES' ? 7 : 6}>Cargando registros…</td></tr>
              )}
              {!loading && filtered.length === 0 && (
                <tr>
                  <td className="ns-table-empty" colSpan={type === 'CATEGORIES' ? 7 : 6}>
                    <strong>No hay registros</strong>
                    <span>Ajusta los filtros o crea un valor nuevo.</span>
                  </td>
                </tr>
              )}
              {!loading && pageData.content.map((item) => (
                <tr key={item.id}>
                  <td className="ns-primary-cell"><strong>{item.name}</strong><small>{item.description || 'Sin descripción'}</small></td>
                  <td><code>{item.code}</code></td>
                  {type === 'CATEGORIES' && <td>{item.organizationName ?? 'GLOBAL'}</td>}
                  <td>{item.dependencyCount}</td>
                  <td>{formatDate(item.updatedAt ?? item.createdAt)}</td>
                  <td><span className={`status-badge status-${item.status.toLowerCase()}`}>{item.status === 'ACTIVE' ? 'Activo' : 'Inactivo'}</span></td>
                  <td>
                    <TableActions>
                      <TableActionButton label="Ver" icon="eye" onClick={() => open('view', item)} />
                      <TableActionButton label="Editar" icon="edit" tone="primary" onClick={() => open('edit', item)} />
                      <TableActionButton label="Administrar" icon="archive" onClick={() => open('manage', item)} />
                    </TableActions>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>        </div>
        <TablePagination currentPage={pageData.page} pageSize={pageData.size} totalElements={pageData.totalElements} totalPages={pageData.totalPages} isLoading={loading} onPageChange={setPage} onPageSizeChange={setPageSize} />
      </section>
      {mode && (
        <div className="ns-dialog-backdrop" role="presentation" onMouseDown={(event) => { if (event.currentTarget === event.target) close() }}>
          <section className="ns-resource-dialog catalog-dialog" role="dialog" aria-modal="true" aria-labelledby="catalog-dialog-title">
            <header>
              <div>
                <p className="eyebrow">{mode === 'create' ? 'Nuevo registro' : mode === 'edit' ? 'Editar' : mode === 'manage' ? 'Administrar' : 'Ver'}</p>
                <h2 id="catalog-dialog-title">{selected?.name ?? summary?.name}</h2>
              </div>
              <button aria-label="Cerrar" className="ns-dialog-close" disabled={busy} type="button" onClick={close}>
                <Icon name="close" size={17} />
              </button>
            </header>

            {(mode === 'create' || mode === 'edit') && (
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
                  {type === 'CATEGORIES' && (
                    <label className="ns-dialog-field">
                      <span>Organización propietaria</span>
                      <select disabled={mode === 'edit'} value={form.organizationPublicId ?? ''} onChange={(event) => setForm((value) => ({ ...value, organizationPublicId: event.target.value || undefined }))}>
                        <option value="">GLOBAL</option>
                        {organizations.filter((organization) => organization.organizationType === 'CUSTOMER').map((organization) => (
                          <option key={organization.publicId} value={organization.publicId}>{organization.name}</option>
                        ))}
                      </select>
                    </label>
                  )}
                  {type === 'PROFESSIONAL_PROFILES' && (
                    <label className="ns-dialog-field">
                      <span>Perfil tecnológico sugerido</span>
                      <select value={form.suggestedTechnologicalProfile ?? ''} onChange={(event) => setForm((value) => ({ ...value, suggestedTechnologicalProfile: event.target.value || undefined }))}>
                        <option value="">Sin sugerencia</option>
                        {technologicalProfiles.map((profile) => <option key={profile.code} value={profile.code}>{profile.name}</option>)}
                      </select>
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

            {mode === 'manage' && selected && (
              <>
                <div className="catalog-management-summary">
                  <div><span>Estado actual</span><strong>{selected.status === 'ACTIVE' ? 'Activo' : 'Inactivo'}</strong></div>
                  <div><span>Dependencias</span><strong>{dependencies?.total ?? selected.dependencyCount}</strong></div>
                </div>
                {dependencies?.details.map((detail) => <p className="catalog-dependency-detail" key={detail}>{detail}</p>)}
                <section className="catalog-action-panel">
                  <h3>{selected.status === 'ACTIVE' ? 'Inactivar registro' : 'Reactivar registro'}</h3>
                  <p>{selected.status === 'ACTIVE'
                    ? 'Dejará de aparecer en nuevos formularios. Las relaciones históricas se conservarán.'
                    : 'Volverá a estar disponible en los selectores operativos.'}</p>
                  <button className="secondary-button" disabled={busy} type="button" onClick={() => void changeStatus(selected.status === 'ACTIVE' ? 'deactivate' : 'activate')}>
                    {selected.status === 'ACTIVE' ? 'Inactivar' : 'Activar'}
                  </button>
                </section>
                {selected.status === 'INACTIVE' && (
                  <section className="catalog-action-panel catalog-danger-panel">
                    <h3>Eliminar registro</h3>
                    <p>{dependencies?.deletable
                      ? 'El registro nunca ha sido utilizado y puede eliminarse de forma segura.'
                      : 'No es posible eliminarlo porque mantiene relaciones históricas.'}</p>
                    <button className="danger-button" disabled={busy || !dependencies?.deletable} type="button" onClick={() => void remove()}>
                      Eliminar definitivamente
                    </button>
                  </section>
                )}
                <footer><button className="secondary-button" disabled={busy} type="button" onClick={close}>Volver</button></footer>
              </>
            )}
          </section>
        </div>
      )}
    </main>
  )
}
