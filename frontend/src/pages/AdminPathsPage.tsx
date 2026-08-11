import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useAuth } from '../features/authentication/context/AuthContext'
import { searchPaths } from '../features/paths/api/pathApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { FilterToolbar } from '../shared/components/FilterToolbar'
import { Icon } from '../shared/components/Icon'
import { ResourceSearchField, ResourceSelectField } from '../shared/components/ResourceFilters'
import { TableActionLink, TableActions } from '../shared/components/TableActions'
import { TablePagination } from '../shared/components/TablePagination'
import { useDebouncedValue } from '../shared/hooks/useDebouncedValue'
import { normalizePagedResponse, parsePage, parsePageSize, type PagedResponse, type PageSize } from '../shared/types/pagination'
import type { PathStatus, PathSummary } from '../shared/types/paths'

function statusLabel(status: PathStatus) {
  return ({ DRAFT: 'Borrador', ACTIVE: 'Activo', INACTIVE: 'Inactivo', ARCHIVED: 'Archivado' } as const)[status]
}

export function AdminPathsPage() {
  const { user } = useAuth()
  const canManage = user?.roles.includes('ADMINISTRATOR') || (user?.permissions.includes('PATH_MANAGE') ?? false)
  const [searchParams, setSearchParams] = useSearchParams()
  const [query, setQuery] = useState(searchParams.get('query') ?? '')
  const [status, setStatus] = useState(searchParams.get('status') ?? 'ACTIVE')
  const [data, setData] = useState<PagedResponse<PathSummary>>({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string>()
  const [reloadKey, setReloadKey] = useState(0)
  const debouncedQuery = useDebouncedValue(query, 300)
  const page = parsePage(searchParams.get('page'))
  const size = parsePageSize(searchParams.get('size'))
  const sort = searchParams.get('sort') ?? 'updatedAt'
  const direction = searchParams.get('direction') === 'ASC' ? 'ASC' : 'DESC'

  const updateUrl = useCallback((patch: Record<string, string | number | undefined>, resetPage = false) => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current)
      Object.entries(patch).forEach(([key, value]) => {
        if (value === undefined || value === '' || (key === 'page' && Number(value) === 0) || (key === 'size' && Number(value) === 10)) next.delete(key)
        else next.set(key, String(value))
      })
      if (resetPage) next.delete('page')
      return next
    }, { replace: true })
  }, [setSearchParams])

  useEffect(() => {
    const currentQuery = searchParams.get('query') ?? ''
    const currentStatus = searchParams.get('status') ?? 'ACTIVE'
    if (currentQuery === debouncedQuery.trim() && currentStatus === status) return
    updateUrl({ query: debouncedQuery || undefined, status: status === 'ACTIVE' ? undefined : status }, true)
  }, [debouncedQuery, searchParams, status, updateUrl])

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(undefined)
    searchPaths({ query: debouncedQuery, status, page, size, sort, direction, signal: controller.signal })
      .then((response) => {
        const normalized = normalizePagedResponse(response)
        setData(normalized)
        if (normalized.totalPages > 0 && page >= normalized.totalPages) updateUrl({ page: normalized.totalPages - 1 })
      })
      .catch((requestError: unknown) => {
        if (controller.signal.aborted) return
        setError(requestError instanceof ApiRequestError ? requestError.message : 'No fue posible consultar los Paths.')
      })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [debouncedQuery, status, page, size, sort, direction, reloadKey, updateUrl])

  const activeFilters = useMemo(() => Boolean(query.trim()) || status !== 'ACTIVE', [query, status])

  return <main className="content-page resource-page ns-list-page path-admin-page">
    {canManage && <div className="ns-list-action-bar" aria-label="Acciones de Paths"><Link className="primary-button button-link ns-create-button" to="/admin/paths/new"><Icon name="plus" size={15} /> Nuevo Path</Link></div>}
    <FilterToolbar hasActiveFilters={activeFilters} onClear={() => { setQuery(''); setStatus('ACTIVE'); updateUrl({ query: undefined, status: undefined, page: undefined }) }}>
      <ResourceSearchField value={query} onChange={setQuery} placeholder="Buscar por nombre, código o descripción" />
      <ResourceSelectField label="Estado" value={status} onChange={setStatus}>
        <option value="ACTIVE">Activo</option><option value="ALL">Todos</option><option value="DRAFT">Borrador</option><option value="INACTIVE">Inactivo</option><option value="ARCHIVED">Archivado</option>
      </ResourceSelectField>
    </FilterToolbar>
    {error && <section className="inline-error-panel" role="alert"><div className="inline-error-icon"><Icon name="error" size={20} /></div><div><strong>No fue posible cargar los Paths</strong><p>{error}</p></div><button className="secondary-button" type="button" onClick={() => setReloadKey((value) => value + 1)}>Reintentar</button></section>}
    <section className="ns-data-panel" aria-busy={loading}>
      <div className="ns-data-table-wrap"><table className="ns-data-table"><thead><tr><th>Path</th><th>Alcance</th><th>Colecciones</th><th>Formularios</th><th>Actualización</th><th>Estado</th><th className="ns-actions-column">Acciones</th></tr></thead><tbody>
        {loading && <tr><td colSpan={7} className="ns-table-empty">Cargando Paths…</td></tr>}
        {!loading && !error && data.content.length === 0 && <tr><td colSpan={7} className="ns-table-empty"><strong>{activeFilters ? 'No encontramos coincidencias' : 'Aún no hay Paths'}</strong><span>{activeFilters ? 'Ajusta o limpia los filtros.' : 'Crea un Path utilizando Colecciones existentes.'}</span></td></tr>}
        {!loading && !error && data.content.map((path) => <tr key={path.publicId}>
          <td className="ns-primary-cell"><strong>{path.name}</strong><small>{path.description || 'Sin descripción'}</small><small><code className="ns-code-label">{path.code}</code></small></td>
          <td><strong>{path.contentScope === 'GLOBAL' ? 'Global' : path.organizationName}</strong><small>{path.contentScope === 'GLOBAL' ? 'Disponible según alcance' : 'Organizacional'}</small></td>
          <td><strong>{path.collectionCount}</strong></td><td>{path.formCount}</td>
          <td>{new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium' }).format(new Date(path.updatedAt))}</td>
          <td><span className={`status-badge status-${path.status.toLowerCase()}`}>{statusLabel(path.status)}</span></td>
          <td><TableActions><TableActionLink to={`/admin/paths/${path.publicId}`} label="Ver" icon="eye" />{canManage && <><TableActionLink to={`/admin/paths/${path.publicId}/edit`} label="Editar" icon="edit" tone="primary" /><TableActionLink to={`/admin/paths/${path.publicId}/manage`} label="Administrar" icon="lock" /></>}</TableActions></td>
        </tr>)}
      </tbody></table></div>
      <TablePagination currentPage={page} pageSize={data.size} totalElements={data.totalElements} totalPages={data.totalPages} isLoading={loading} onPageChange={(nextPage) => updateUrl({ page: nextPage })} onPageSizeChange={(nextSize: PageSize) => updateUrl({ size: nextSize, page: undefined })} />
    </section>
  </main>
}
