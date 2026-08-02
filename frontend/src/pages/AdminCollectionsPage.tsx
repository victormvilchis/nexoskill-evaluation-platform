import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { searchLearningCollections } from '../features/collections/api/collectionApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { FilterToolbar } from '../shared/components/FilterToolbar'
import { Icon } from '../shared/components/Icon'
import { ResourceSearchField, ResourceSelectField } from '../shared/components/ResourceFilters'
import { TableActionLink, TableActions } from '../shared/components/TableActions'
import { TablePagination } from '../shared/components/TablePagination'
import { useDebouncedValue } from '../shared/hooks/useDebouncedValue'
import type { CollectionStatus, CollectionSummary } from '../shared/types/collections'
import { useAuth } from '../features/authentication/context/AuthContext'
import { normalizePagedResponse, parsePage, parsePageSize, type PagedResponse, type PageSize } from '../shared/types/pagination'

function statusLabel(status: CollectionStatus) {
  const labels: Record<CollectionStatus, string> = {
    DRAFT: 'Borrador', ACTIVE: 'Activa', INACTIVE: 'Inactiva', ARCHIVED: 'Archivada'
  }
  return labels[status]
}

export function AdminCollectionsPage() {
  const { user } = useAuth()
  const canManage = user?.permissions.includes('COLLECTION_MANAGE') ?? false
  const [searchParams, setSearchParams] = useSearchParams()
  const [query, setQuery] = useState(searchParams.get('query') ?? '')
  const [status, setStatus] = useState(searchParams.get('status') ?? 'ACTIVE')
  const [data, setData] = useState<PagedResponse<CollectionSummary>>({
    content: [], page: 0, size: 10, totalElements: 0, totalPages: 0
  })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string>()
  const [reloadKey, setReloadKey] = useState(0)
  const debouncedQuery = useDebouncedValue(query, 300)
  const page = parsePage(searchParams.get('page'))
  const size = parsePageSize(searchParams.get('size'))
  const sort = searchParams.get('sort') ?? 'updatedAt'
  const direction = searchParams.get('direction') === 'ASC' ? 'ASC' : 'DESC'

  function updateUrl(patch: Record<string, string | number | undefined>, resetPage = false) {
    setSearchParams((current) => {
      const next = new URLSearchParams(current)
      Object.entries(patch).forEach(([key, value]) => {
        if (value === undefined || value === '' || (key === 'page' && Number(value) === 0) || (key === 'size' && Number(value) === 10)) {
          next.delete(key)
        } else next.set(key, String(value))
      })
      if (resetPage) next.delete('page')
      return next
    }, { replace: true })
  }

  useEffect(() => {
    const currentQuery = searchParams.get('query') ?? ''
    const currentStatus = searchParams.get('status') ?? 'ACTIVE'
    if (currentQuery === debouncedQuery.trim() && currentStatus === status) return
    updateUrl({ query: debouncedQuery || undefined, status: status === 'ACTIVE' ? undefined : status }, true)
  }, [debouncedQuery, searchParams, status])

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(undefined)
    searchLearningCollections({
      query: debouncedQuery,
      status,
      page,
      size,
      sort,
      direction,
      signal: controller.signal
    })
      .then((response) => {
        const normalized = normalizePagedResponse(response)
        setData(normalized)
        if (normalized.totalPages > 0 && page >= normalized.totalPages) updateUrl({ page: normalized.totalPages - 1 })
      })
      .catch((requestError: unknown) => {
        if (controller.signal.aborted) return
        setError(requestError instanceof ApiRequestError ? requestError.message : 'No fue posible consultar las colecciones.')
      })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [debouncedQuery, status, page, size, sort, direction, reloadKey])

  const activeFilters = useMemo(() => Boolean(query.trim()) || status !== 'ACTIVE', [query, status])

  return (
    <main className="content-page resource-page ns-list-page">
      {canManage && <div className="ns-list-action-bar" aria-label="Acciones de colecciones"><Link className="primary-button button-link ns-create-button" to="/admin/collections/new"><Icon name="plus" size={15} /> Nueva colección</Link></div>}

      <FilterToolbar hasActiveFilters={activeFilters} onClear={() => { setQuery(''); setStatus('ACTIVE'); updateUrl({ query: undefined, status: undefined, page: undefined }) }}>
        <ResourceSearchField value={query} onChange={setQuery} placeholder="Buscar por nombre, código o descripción" />
        <ResourceSelectField label="Estado" value={status} onChange={setStatus}>
          <option value="ACTIVE">Activa</option><option value="ALL">Todos</option><option value="DRAFT">Borrador</option><option value="INACTIVE">Inactiva</option><option value="ARCHIVED">Archivada</option>
        </ResourceSelectField>
      </FilterToolbar>

      {error && <section className="inline-error-panel" role="alert"><div className="inline-error-icon"><Icon name="error" size={20} /></div><div><strong>No fue posible cargar las colecciones</strong><p>{error}</p></div><button className="secondary-button" type="button" onClick={() => setReloadKey((value) => value + 1)}>Reintentar</button></section>}

      <section className="ns-data-panel" aria-busy={loading}>
        <div className="ns-data-table-wrap">
          <table className="ns-data-table">
            <thead><tr><th>Colección</th><th>Niveles</th><th>Formularios activos</th><th>Estado</th><th className="ns-actions-column">Acciones</th></tr></thead>
            <tbody>
              {loading && <tr><td colSpan={5} className="ns-table-empty">Cargando colecciones…</td></tr>}
              {!loading && !error && data.content.length === 0 && <tr><td colSpan={5} className="ns-table-empty"><strong>{activeFilters ? 'No encontramos coincidencias' : 'Aún no hay colecciones'}</strong><span>{activeFilters ? 'Ajusta o limpia los filtros.' : 'Crea una colección y agrega formularios por nivel.'}</span></td></tr>}
              {!loading && !error && data.content.map((collection) => <tr key={collection.publicId}>
                <td className="ns-primary-cell"><strong>{collection.name}</strong><small>{collection.description || 'Sin descripción'}</small><small><code className="ns-code-label">{collection.code}</code></small></td>
                <td><strong>{collection.levelCount}</strong></td><td>{collection.activeLevelCount}</td>
                <td><span className={`status-badge status-${collection.status.toLowerCase()}`}>{statusLabel(collection.status)}</span></td>
                <td><TableActions>
                  <TableActionLink to={`/admin/collections/${collection.publicId}`} label="Ver" icon="eye" />
                  {canManage && <TableActionLink to={`/admin/collections/${collection.publicId}/edit`} label="Editar" icon="edit" tone="primary" />}
                </TableActions></td>
              </tr>)}
            </tbody>
          </table>
        </div>
        <TablePagination currentPage={page} pageSize={data.size} totalElements={data.totalElements} totalPages={data.totalPages} isLoading={loading} onPageChange={(nextPage) => updateUrl({ page: nextPage })} onPageSizeChange={(nextSize: PageSize) => updateUrl({ size: nextSize, page: undefined })} />
      </section>
    </main>
  )
}
