import { useEffect, useState } from 'react'
import { getQuestionCatalogs } from '../features/questions/api/questionApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { Icon } from '../shared/components/Icon'
import type { QuestionTechnology } from '../shared/types/questions'

export function QuestionTechnologiesPage() {
  const [technologies, setTechnologies] = useState<QuestionTechnology[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string>()

  useEffect(() => {
    const controller = new AbortController()
    getQuestionCatalogs(controller.signal)
      .then((catalogs) => setTechnologies(catalogs.technologies))
      .catch((requestError: unknown) => {
        if (!controller.signal.aborted) {
          setError(requestError instanceof ApiRequestError
            ? requestError.message
            : 'No fue posible consultar las tecnologías del Banco de Preguntas.')
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [])

  return (
    <main className="content-page resource-page ns-list-page question-technologies-page">
      <header className="ns-page-header">
        <div>
          <p className="eyebrow">Banco de Preguntas</p>
          <h1>Tecnologías</h1>
          <p className="muted">Catálogo controlado utilizado para clasificar y filtrar preguntas globales y organizacionales.</p>
        </div>
      </header>

      {error && (
        <section className="inline-error-panel" role="alert">
          <div className="inline-error-icon"><Icon name="error" /></div>
          <div><strong>No fue posible cargar el catálogo</strong><p>{error}</p></div>
        </section>
      )}

      <section className="ns-data-panel">
        <div className="ns-data-table-wrap">
          <table className="ns-data-table">
            <thead><tr><th>Tecnología</th><th>Código</th><th>Estado</th><th>Orden</th></tr></thead>
            <tbody>
              {loading && <tr><td className="ns-table-empty" colSpan={4}>Cargando tecnologías…</td></tr>}
              {!loading && technologies.length === 0 && !error && (
                <tr><td className="ns-table-empty" colSpan={4}>No hay tecnologías disponibles.</td></tr>
              )}
              {!loading && technologies.map((technology) => (
                <tr key={technology.publicId}>
                  <td className="ns-primary-cell"><strong>{technology.name}</strong></td>
                  <td><code className="ns-code-label">{technology.code}</code></td>
                  <td><span className={`status-badge status-${technology.status.toLowerCase()}`}>{technology.status === 'ACTIVE' ? 'Activa' : 'Inactiva'}</span></td>
                  <td>{technology.displayOrder}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </main>
  )
}
