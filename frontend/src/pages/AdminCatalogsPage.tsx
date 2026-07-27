import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getCatalogTypes } from '../features/catalogs/api/catalogApi'
import type { CatalogTypeSummary } from '../features/catalogs/types/catalogs'
import { ApiRequestError } from '../shared/api/apiClient'
import { Icon } from '../shared/components/Icon'

function formatDate(value?: string) {
  if (!value) return 'Sin modificaciones'
  return new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium', timeStyle: 'short' })
    .format(new Date(value))
}

export function AdminCatalogsPage() {
  const [catalogs, setCatalogs] = useState<CatalogTypeSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string>()
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(undefined)
    getCatalogTypes(controller.signal)
      .then(setCatalogs)
      .catch((requestError: unknown) => {
        if (controller.signal.aborted) return
        setError(requestError instanceof ApiRequestError
          ? requestError.message
          : 'No fue posible consultar los catálogos.')
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [reloadKey])

  return (
    <main className="content-page resource-page ns-list-page catalog-hub-page">
      <header className="ns-page-header">
        <div>
          <p className="eyebrow">Administración</p>
          <h1>Catálogos</h1>
          <p className="muted">Administra desde un solo lugar los datos maestros reutilizables de NexoSkill.</p>
        </div>
      </header>

      {error && (
        <section className="inline-error-panel" role="alert">
          <div className="inline-error-icon"><Icon name="error" /></div>
          <div><strong>No fue posible cargar los catálogos</strong><p>{error}</p></div>
          <button className="secondary-button compact-button" type="button" onClick={() => setReloadKey((value) => value + 1)}>Reintentar</button>
        </section>
      )}

      <section className="catalog-type-grid" aria-busy={loading}>
        {loading && <div className="catalog-loading-card">Cargando catálogos…</div>}
        {!loading && catalogs.map((catalog) => (
          <article className="catalog-type-card" key={catalog.type}>
            <div className="catalog-type-card-icon"><Icon name={catalog.type === 'TECHNOLOGIES' ? 'code' : 'categories'} /></div>
            <div className="catalog-type-card-body">
              <div>
                <h2>{catalog.name}</h2>
                <p>{catalog.description}</p>
              </div>
              <dl>
                <div><dt>Activos</dt><dd>{catalog.activeCount}</dd></div>
                <div><dt>Inactivos</dt><dd>{catalog.inactiveCount}</dd></div>
              </dl>
              <small>Última modificación: {formatDate(catalog.lastModifiedAt)}</small>
            </div>
            <Link className="secondary-button button-link" to={`/admin/catalogs/${catalog.type}`}>
              Administrar <Icon name="chevronRight" size={15} />
            </Link>
          </article>
        ))}
      </section>
    </main>
  )
}
