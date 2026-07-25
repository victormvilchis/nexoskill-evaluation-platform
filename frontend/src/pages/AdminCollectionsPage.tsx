import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { searchCollections } from '../features/questions/api/questionApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { Icon } from '../shared/components/Icon'
import type { CollectionPage } from '../shared/types/questions'

export function AdminCollectionsPage() {
  const [data, setData] = useState<CollectionPage>()
  const [query, setQuery] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string>()
  const [reloadKey, setReloadKey] = useState(0)

  const reload = useCallback(() => setReloadKey((value) => value + 1), [])

  useEffect(() => {
    const controller = new AbortController()
    const timer = window.setTimeout(() => {
      setLoading(true)
      setError(undefined)

      searchCollections({
        query: query.trim() || undefined,
        size: 50,
        signal: controller.signal
      })
        .then(setData)
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
    }, 250)

    return () => {
      window.clearTimeout(timer)
      controller.abort()
    }
  }, [query, reloadKey])

  const collections = data?.content ?? []

  return (
    <main className="content-page resource-page">
      <div className="page-heading resource-heading">
        <div>
          <p className="eyebrow">Contenido</p>
          <h1>Colecciones</h1>
          <p className="muted">
            Agrupa categorías y preguntas para reutilizarlas en formularios.
          </p>
        </div>
        <div className="heading-actions resource-heading-actions">
          <Link
            className="primary-button button-link"
            to="/admin/question-collections/new"
          >
            <Icon name="plus" size={16} />
            Nueva colección
          </Link>
        </div>
      </div>

      <section className="resource-toolbar" aria-label="Filtros de colecciones">
        <label className="resource-search">
          <Icon name="search" size={18} />
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Buscar colección"
          />
          {query && (
            <button
              aria-label="Limpiar búsqueda"
              className="resource-search-clear"
              type="button"
              onClick={() => setQuery('')}
            >
              <Icon name="close" size={15} />
            </button>
          )}
        </label>
        <span className="resource-total">
          <strong>{data?.totalElements ?? 0}</strong>
          {data?.totalElements === 1 ? ' colección' : ' colecciones'}
        </span>
      </section>

      {error && (
        <section className="inline-error-panel" role="alert">
          <div className="inline-error-icon">
            <Icon name="error" size={20} />
          </div>
          <div>
            <strong>No fue posible cargar las colecciones</strong>
            <p>{error}</p>
          </div>
          <button className="secondary-button compact-button" onClick={reload}>
            Reintentar
          </button>
        </section>
      )}

      {loading && !data ? (
        <div className="resource-card-grid" aria-label="Cargando colecciones">
          {Array.from({ length: 4 }, (_, index) => (
            <div className="resource-card resource-card-skeleton" key={index} />
          ))}
        </div>
      ) : (
        <div className="resource-card-grid">
          {collections.map((collection) => (
            <Link
              className="resource-card collection-resource-card"
              to={`/admin/question-collections/${collection.publicId}`}
              key={collection.publicId}
            >
              <div>
                <div className="resource-card-header">
                  <span
                    className={`status-badge status-${collection.status.toLowerCase()}`}
                  >
                    {collection.status === 'ACTIVE' ? 'Activa' : 'Inactiva'}
                  </span>
                </div>
                <h2>{collection.name}</h2>
                <p>{collection.description || 'Sin descripción'}</p>
              </div>
              <footer>
                <span>{collection.categoryCount} categorías</span>
                <span>{collection.effectiveQuestionCount} preguntas</span>
              </footer>
            </Link>
          ))}
        </div>
      )}

      {!loading && !error && collections.length === 0 && (
        <section className="empty-state-card resource-empty-state">
          <Icon name="collections" size={26} />
          <h2>{query ? 'No encontramos coincidencias' : 'Aún no hay colecciones'}</h2>
          <p>
            {query
              ? 'Prueba con otro término de búsqueda.'
              : 'Agrupa categorías y preguntas para reutilizarlas después en formularios.'}
          </p>
          {!query && (
            <Link
              className="primary-button button-link"
              to="/admin/question-collections/new"
            >
              <Icon name="plus" size={16} /> Nueva colección
            </Link>
          )}
        </section>
      )}
    </main>
  )
}
