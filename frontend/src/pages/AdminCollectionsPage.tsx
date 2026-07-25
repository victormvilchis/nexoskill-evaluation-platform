import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiRequestError } from '../shared/api/apiClient'
import { Icon } from '../shared/components/Icon'
import { useDebouncedValue } from '../shared/hooks/useDebouncedValue'
import type { CollectionStatus, CollectionSummary } from '../shared/types/collections'
import { searchLearningCollections } from '../features/collections/api/collectionApi'

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
  const [status, setStatus] = useState('ALL')
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
    () => Number(Boolean(query.trim())) + Number(status !== 'ALL'),
    [query, status]
  )

  function clearFilters() {
    setQuery('')
    setStatus('ALL')
  }

  return (
    <main className="content-page lc-page">
      <div className="lc-page-header">
        <div>
          <p className="eyebrow">Aprendizaje</p>
          <h1>Colecciones</h1>
          <p>
            Organiza formularios en niveles progresivos. El participante comienza
            en el Nivel 1 y desbloquea los siguientes al aprobar.
          </p>
        </div>
        <Link className="primary-button button-link" to="/admin/collections/new">
          <Icon name="plus" size={17} />
          Nueva colección
        </Link>
      </div>

      <section className="lc-list-toolbar" aria-label="Filtros de colecciones">
        <label className="lc-list-search">
          <Icon name="search" size={19} />
          <input
            aria-label="Buscar colecciones"
            placeholder="Buscar por nombre, código o descripción"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
          {query && (
            <button aria-label="Limpiar búsqueda" type="button" onClick={() => setQuery('')}>
              <Icon name="close" size={15} />
            </button>
          )}
        </label>

        <label className="lc-list-select">
          <span>Estado</span>
          <select value={status} onChange={(event) => setStatus(event.target.value)}>
            <option value="ALL">Todos</option>
            <option value="DRAFT">Borrador</option>
            <option value="ACTIVE">Activa</option>
            <option value="INACTIVE">Inactiva</option>
            <option value="ARCHIVED">Archivada</option>
          </select>
        </label>

        <div className="lc-toolbar-summary">
          <strong>{collections.length}</strong>
          <span>{collections.length === 1 ? 'colección' : 'colecciones'}</span>
        </div>

        {activeFilters > 0 && (
          <button className="lc-clear-filters" type="button" onClick={clearFilters}>
            Limpiar filtros ({activeFilters})
          </button>
        )}
      </section>

      {error && (
        <section className="lc-error-panel" role="alert">
          <div>
            <Icon name="error" size={20} />
          </div>
          <div>
            <strong>No fue posible cargar las colecciones</strong>
            <p>{error}</p>
          </div>
          <button className="secondary-button" type="button" onClick={() => setReloadKey((value) => value + 1)}>
            Reintentar
          </button>
        </section>
      )}

      {loading ? (
        <section className="lc-collection-grid" aria-label="Cargando colecciones">
          {Array.from({ length: 4 }, (_, index) => (
            <div className="lc-collection-card lc-skeleton" key={index} />
          ))}
        </section>
      ) : !error && collections.length > 0 ? (
        <section className="lc-collection-grid">
          {collections.map((collection) => (
            <Link
              className="lc-collection-card"
              key={collection.publicId}
              to={`/admin/collections/${collection.publicId}`}
            >
              <div className="lc-card-topline">
                <span className={`lc-status lc-status-${collection.status.toLowerCase()}`}>
                  {statusLabel(collection.status)}
                </span>
                <span className="lc-collection-code">{collection.code}</span>
              </div>

              <div className="lc-collection-copy">
                <h2>{collection.name}</h2>
                <p>{collection.description || 'Sin descripción'}</p>
              </div>

              <div className="lc-level-preview" aria-label={`${collection.levelCount} niveles`}>
                {collection.levelCount === 0 ? (
                  <span className="lc-no-levels">Sin niveles configurados</span>
                ) : (
                  Array.from({ length: Math.min(collection.levelCount, 5) }, (_, index) => (
                    <span className="lc-mini-level" key={index}>{index + 1}</span>
                  ))
                )}
                {collection.levelCount > 5 && (
                  <span className="lc-more-levels">+{collection.levelCount - 5}</span>
                )}
              </div>

              <footer className="lc-card-footer">
                <div>
                  <strong>{collection.levelCount}</strong>
                  <span>{collection.levelCount === 1 ? 'nivel' : 'niveles'}</span>
                </div>
                <div>
                  <strong>{collection.activeLevelCount}</strong>
                  <span>formularios activos</span>
                </div>
                <time dateTime={collection.updatedAt}>
                  Actualizada {formatDate(collection.updatedAt)}
                </time>
              </footer>
            </Link>
          ))}
        </section>
      ) : !error ? (
        <section className="lc-empty-list">
          <div className="lc-empty-icon"><Icon name="clipboard" size={28} /></div>
          <h2>{activeFilters ? 'No encontramos coincidencias' : 'Aún no hay colecciones'}</h2>
          <p>
            {activeFilters
              ? 'Modifica o limpia los filtros para ver otros resultados.'
              : 'Crea una colección y agrega formularios para configurar sus niveles.'}
          </p>
          {activeFilters ? (
            <button className="secondary-button" type="button" onClick={clearFilters}>
              Limpiar filtros
            </button>
          ) : (
            <Link className="primary-button button-link" to="/admin/collections/new">
              <Icon name="plus" size={17} />
              Crear primera colección
            </Link>
          )}
        </section>
      ) : null}
    </main>
  )
}
