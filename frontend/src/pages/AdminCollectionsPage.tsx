import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { searchLearningCollections } from '../features/collections/api/collectionApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { FilterToolbar } from '../shared/components/FilterToolbar'
import { Icon } from '../shared/components/Icon'
import {
  ResourceSearchField,
  ResourceSelectField
} from '../shared/components/ResourceFilters'
import { TableActionLink, TableActions } from '../shared/components/TableActions'
import { useDebouncedValue } from '../shared/hooks/useDebouncedValue'
import type { CollectionStatus, CollectionSummary } from '../shared/types/collections'

function statusLabel(status: CollectionStatus) {
  const labels: Record<CollectionStatus, string> = {
    DRAFT: 'Borrador',
    ACTIVE: 'Activa',
    INACTIVE: 'Inactiva',
    ARCHIVED: 'Archivada'
  }
  return labels[status]
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('es-MX', {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(new Date(value))
}

export function AdminCollectionsPage() {
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState('ACTIVE')
  const [collections, setCollections] = useState<CollectionSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string>()
  const [reloadKey, setReloadKey] = useState(0)
  const debouncedQuery = useDebouncedValue(query, 250)

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(undefined)

    searchLearningCollections({
      query: debouncedQuery,
      status,
      signal: controller.signal
    })
      .then(setCollections)
      .catch((requestError: unknown) => {
        if (controller.signal.aborted) return
        setError(
          requestError instanceof ApiRequestError
            ? requestError.message
            : 'No fue posible consultar las colecciones.'
        )
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })

    return () => controller.abort()
  }, [debouncedQuery, status, reloadKey])

  const activeFilters = useMemo(
    () => Boolean(query.trim()) || status !== 'ACTIVE',
    [query, status]
  )

  return (
    <main className="content-page resource-page ns-list-page">
      <header className="ns-page-header">
        <div>
          <p className="eyebrow">Aprendizaje</p>
          <h1>Colecciones</h1>
          <p className="muted">Organiza formularios en niveles progresivos.</p>
        </div>
        <Link className="primary-button button-link" to="/admin/collections/new">
          <Icon name="plus" size={17} /> Nueva colección
        </Link>
      </header>

      <FilterToolbar
        resultLabel={`${collections.length} ${collections.length === 1 ? 'colección' : 'colecciones'}`}
        hasActiveFilters={activeFilters}
        onClear={() => {
          setQuery('')
          setStatus('ACTIVE')
        }}
      >
        <ResourceSearchField
          value={query}
          onChange={setQuery}
          placeholder="Buscar por nombre, código o descripción"
        />
        <ResourceSelectField label="Estado" value={status} onChange={setStatus}>
          <option value="ACTIVE">Activa</option>
          <option value="ALL">Todos</option>
          <option value="DRAFT">Borrador</option>
          <option value="INACTIVE">Inactiva</option>
          <option value="ARCHIVED">Archivada</option>
        </ResourceSelectField>
      </FilterToolbar>

      {error && (
        <section className="inline-error-panel" role="alert">
          <div className="inline-error-icon"><Icon name="error" size={20} /></div>
          <div><strong>No fue posible cargar las colecciones</strong><p>{error}</p></div>
          <button className="secondary-button" type="button" onClick={() => setReloadKey((value) => value + 1)}>
            Reintentar
          </button>
        </section>
      )}

      <section className="ns-data-panel" aria-busy={loading}>
        <div className="ns-data-table-wrap">
          <table className="ns-data-table">
            <thead>
              <tr>
                <th>Colección</th>
                <th>Niveles</th>
                <th>Formularios activos</th>
                <th>Última actualización</th>
                <th>Estado</th>
                <th className="ns-actions-column">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {loading && <tr><td colSpan={6} className="ns-table-empty">Cargando colecciones…</td></tr>}
              {!loading && !error && collections.length === 0 && (
                <tr>
                  <td colSpan={6} className="ns-table-empty">
                    <strong>{activeFilters ? 'No encontramos coincidencias' : 'Aún no hay colecciones'}</strong>
                    <span>{activeFilters ? 'Ajusta o limpia los filtros.' : 'Crea una colección y agrega formularios por nivel.'}</span>
                  </td>
                </tr>
              )}
              {!loading && collections.map((collection) => (
                <tr key={collection.publicId}>
                  <td className="ns-primary-cell">
                    <strong>{collection.name}</strong>
                    <small>{collection.description || 'Sin descripción'}</small>
                    <small><code className="ns-code-label">{collection.code}</code></small>
                  </td>
                  <td><strong>{collection.levelCount}</strong></td>
                  <td>{collection.activeLevelCount}</td>
                  <td>{formatDate(collection.updatedAt)}</td>
                  <td>
                    <span className={`status-badge status-${collection.status.toLowerCase()}`}>
                      {statusLabel(collection.status)}
                    </span>
                  </td>
                  <td>
                    <TableActions>
                      <TableActionLink
                        to={`/admin/collections/${collection.publicId}`}
                        label="Editar"
                        icon="edit"
                        tone="primary"
                      />
                    </TableActions>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </main>
  )
}
