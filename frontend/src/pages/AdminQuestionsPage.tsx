import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getQuestionCatalogs, searchQuestions } from '../features/questions/api/questionApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { Icon } from '../shared/components/Icon'
import type {
  QuestionCatalogs,
  QuestionPage,
  QuestionStatus,
  QuestionTypeCode
} from '../shared/types/questions'

const statusLabel: Record<QuestionStatus, string> = {
  ACTIVE: 'Activa',
  ARCHIVED: 'Archivada',
  DELETED: 'Eliminada'
}

function monthYear(value: string) {
  return new Intl.DateTimeFormat('es-MX', { month: 'short', year: 'numeric' })
    .format(new Date(value))
    .replace('.', '')
}

export function AdminQuestionsPage() {
  const [data, setData] = useState<QuestionPage>()
  const [catalogs, setCatalogs] = useState<QuestionCatalogs>()
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState<QuestionStatus | ''>('')
  const [typeCode, setTypeCode] = useState<QuestionTypeCode | ''>('')
  const [categoryPublicId, setCategoryPublicId] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string>()
  const [reloadKey, setReloadKey] = useState(0)
  const reload = useCallback(() => setReloadKey((value) => value + 1), [])

  useEffect(() => {
    const controller = new AbortController()
    getQuestionCatalogs(controller.signal).then(setCatalogs).catch(() => undefined)
    return () => controller.abort()
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    const timer = window.setTimeout(() => {
      setLoading(true)
      setError(undefined)
      searchQuestions({
        query: query.trim() || undefined,
        status,
        typeCode,
        categoryPublicId,
        size: 50,
        signal: controller.signal
      })
        .then(setData)
        .catch((requestError: unknown) => {
          if (!controller.signal.aborted) {
            setError(requestError instanceof ApiRequestError
              ? requestError.message
              : 'No fue posible consultar las preguntas.')
          }
        })
        .finally(() => {
          if (!controller.signal.aborted) setLoading(false)
        })
    }, 280)

    return () => {
      window.clearTimeout(timer)
      controller.abort()
    }
  }, [query, status, typeCode, categoryPublicId, reloadKey])

  const questions = data?.content ?? []
  const hasFilters = Boolean(query || status || typeCode || categoryPublicId)

  return (
    <main className="content-page resource-page question-bank-page-v2">
      <div className="page-heading resource-heading">
        <div>
          <p className="eyebrow">Contenido</p>
          <h1>Banco de preguntas</h1>
          <p className="muted">Preguntas Java organizadas por categorías y reutilizadas en formularios.</p>
        </div>
        <div className="heading-actions resource-heading-actions">
          <Link className="primary-button button-link" to="/admin/questions/new">
            <Icon name="plus" size={16} /> Nueva pregunta
          </Link>
        </div>
      </div>

      <section className="question-filter-panel" aria-label="Filtros de preguntas">
        <label className="resource-search question-bank-search">
          <Icon name="search" size={18} />
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Buscar en enunciado, opciones, código Java o categoría"
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

        <label className="compact-filter">
          <span>Tipo</span>
          <select value={typeCode} onChange={(event) => setTypeCode(event.target.value as QuestionTypeCode | '')}>
            <option value="">Todos</option>
            {catalogs?.types.map((type) => <option value={type.code} key={type.code}>{type.name}</option>)}
          </select>
        </label>

        <label className="compact-filter">
          <span>Categoría</span>
          <select value={categoryPublicId} onChange={(event) => setCategoryPublicId(event.target.value)}>
            <option value="">Todas</option>
            {catalogs?.categories.map((category) => (
              <option value={category.publicId} key={category.publicId}>{category.name}</option>
            ))}
          </select>
        </label>

        <label className="compact-filter">
          <span>Estado</span>
          <select value={status} onChange={(event) => setStatus(event.target.value as QuestionStatus | '')}>
            <option value="">Disponibles</option>
            <option value="ACTIVE">Activas</option>
            <option value="ARCHIVED">Archivadas</option>
            <option value="DELETED">Eliminadas</option>
          </select>
        </label>

        <div className="question-filter-summary">
          <span className="resource-total">
            <strong>{data?.totalElements ?? 0}</strong>
            {data?.totalElements === 1 ? ' pregunta' : ' preguntas'}
          </span>
          {hasFilters && (
            <button
              className="filter-clear-button"
              type="button"
              onClick={() => {
                setQuery('')
                setStatus('')
                setTypeCode('')
                setCategoryPublicId('')
              }}
            >
              Limpiar filtros
            </button>
          )}
        </div>
      </section>

      {error && (
        <section className="inline-error-panel" role="alert">
          <div className="inline-error-icon"><Icon name="error" size={20} /></div>
          <div><strong>No fue posible cargar el banco de preguntas</strong><p>{error}</p></div>
          <button className="secondary-button compact-button" onClick={reload}>Reintentar</button>
        </section>
      )}

      {loading && !data ? (
        <div className="question-compact-list">
          {Array.from({ length: 6 }, (_, index) => <div className="question-row-card skeleton" key={index} />)}
        </div>
      ) : (
        <div className="question-compact-list">
          {questions.map((question) => (
            <Link
              className={`question-row-card ${question.status === 'DELETED' ? 'deleted' : ''}`}
              to={`/admin/questions/${question.publicId}`}
              key={question.publicId}
            >
              <div className="question-row-main">
                <div className="question-row-topline">
                  <span className={`status-badge status-${question.status.toLowerCase()}`}>
                    {statusLabel[question.status]}
                  </span>
                  <span className="question-type-pill">{question.typeName}</span>
                  {question.hasCode && <span className="question-feature"><Icon name="code" size={13} /> Java</span>}
                  {question.hasMedia && <span className="question-feature"><Icon name="image" size={13} /> Imagen</span>}
                  <time dateTime={question.createdAt}>{monthYear(question.createdAt)}</time>
                </div>
                <h2>{question.statement}</h2>
                <div className="question-category-line">
                  {question.categories.map((category) => (
                    <span className="category-chip compact" key={category.publicId}>{category.name}</span>
                  ))}
                </div>
              </div>

              <aside className="question-membership-summary">
                <div>
                  <span>Formularios</span>
                  {question.forms.length
                    ? question.forms.slice(0, 2).map((form) => <strong key={form.publicId}>{form.name}</strong>)
                    : <em>Sin formulario</em>}
                  {question.forms.length > 2 && <small>+{question.forms.length - 2} más</small>}
                </div>
                <div>
                  <span>Colecciones</span>
                  {question.collections.length
                    ? question.collections.slice(0, 2).map((collection) => <strong key={collection.publicId}>{collection.name}</strong>)
                    : <em>Sin colección</em>}
                  {question.collections.length > 2 && <small>+{question.collections.length - 2} más</small>}
                </div>
                <Icon name="chevronRight" size={18} />
              </aside>
            </Link>
          ))}
        </div>
      )}

      {!loading && !error && questions.length === 0 && (
        <section className="empty-state-card resource-empty-state">
          <Icon name="questions" size={26} />
          <h2>{status === 'DELETED' ? 'No hay preguntas eliminadas' : hasFilters ? 'No encontramos coincidencias' : 'Aún no hay preguntas'}</h2>
          <p>{hasFilters ? 'Prueba con otros filtros o términos de búsqueda.' : 'Crea la primera pregunta para comenzar a construir tu banco.'}</p>
        </section>
      )}
    </main>
  )
}
