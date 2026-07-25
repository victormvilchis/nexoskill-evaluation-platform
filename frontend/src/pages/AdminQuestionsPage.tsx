import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import {
  getQuestionCatalogs,
  searchQuestions
} from '../features/questions/api/questionApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { Icon } from '../shared/components/Icon'
import type {
  QuestionCatalogs,
  QuestionDifficultyCode,
  QuestionPage,
  QuestionStatus,
  QuestionTypeCode
} from '../shared/types/questions'

const statusOptions: Array<{ value: QuestionStatus | ''; label: string }> = [
  { value: '', label: 'Todos los estados' },
  { value: 'PUBLISHED', label: 'Publicadas' },
  { value: 'ARCHIVED', label: 'Archivadas' }
]

const statusLabels: Record<QuestionStatus, string> = {
  DRAFT: 'Estado anterior',
  UNDER_REVIEW: 'Estado anterior',
  APPROVED: 'Estado anterior',
  PUBLISHED: 'Publicada',
  ARCHIVED: 'Archivada'
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('es-MX', {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(new Date(value))
}

function truncate(value: string, length = 140) {
  return value.length <= length ? value : `${value.slice(0, length)}…`
}

export function AdminQuestionsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [query, setQuery] = useState(searchParams.get('query') ?? '')
  const [status, setStatus] = useState<QuestionStatus | ''>(
    (searchParams.get('status') as QuestionStatus | null) ?? ''
  )
  const [typeCode, setTypeCode] = useState<QuestionTypeCode | ''>(
    (searchParams.get('typeCode') as QuestionTypeCode | null) ?? ''
  )
  const [difficultyCode, setDifficultyCode] =
    useState<QuestionDifficultyCode | ''>(
      (searchParams.get('difficultyCode') as QuestionDifficultyCode | null) ?? ''
    )
  const [categoryPublicId, setCategoryPublicId] = useState(
    searchParams.get('categoryPublicId') ?? ''
  )
  const [catalogs, setCatalogs] = useState<QuestionCatalogs | null>(null)
  const [data, setData] = useState<QuestionPage | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const page = Math.max(Number(searchParams.get('page') ?? 0), 0)

  useEffect(() => {
    getQuestionCatalogs()
      .then(setCatalogs)
      .catch(() => setError('No fue posible cargar los catálogos de preguntas.'))
  }, [])

  useEffect(() => {
    let active = true
    setLoading(true)
    setError(null)

    searchQuestions({
      query: searchParams.get('query') ?? '',
      status: (searchParams.get('status') as QuestionStatus | null) ?? '',
      typeCode:
        (searchParams.get('typeCode') as QuestionTypeCode | null) ?? '',
      difficultyCode:
        (searchParams.get('difficultyCode') as QuestionDifficultyCode | null) ??
        '',
      categoryPublicId: searchParams.get('categoryPublicId') ?? '',
      page
    })
      .then((response) => {
        if (active) setData(response)
      })
      .catch((requestError) => {
        if (!active) return
        setError(
          requestError instanceof ApiRequestError
            ? requestError.message
            : 'No fue posible consultar el banco de preguntas.'
        )
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [page, searchParams])

  function applyFilters() {
    const next = new URLSearchParams()
    if (query.trim()) next.set('query', query.trim())
    if (status) next.set('status', status)
    if (typeCode) next.set('typeCode', typeCode)
    if (difficultyCode) next.set('difficultyCode', difficultyCode)
    if (categoryPublicId) next.set('categoryPublicId', categoryPublicId)
    setSearchParams(next)
  }

  function clearFilters() {
    setQuery('')
    setStatus('')
    setTypeCode('')
    setDifficultyCode('')
    setCategoryPublicId('')
    setSearchParams(new URLSearchParams())
  }

  function goToPage(nextPage: number) {
    const next = new URLSearchParams(searchParams)
    next.delete('created')
    next.set('page', String(nextPage))
    setSearchParams(next)
  }

  return (
    <main className="content-page">
      <div className="page-heading">
        <div>
          <p className="eyebrow">Contenido de evaluaciones</p>
          <h1>Banco de preguntas</h1>
          <p className="muted">
            Crea, clasifica y consulta preguntas antes de incorporarlas a una
            evaluación.
          </p>
        </div>
        <div className="heading-actions">
          <Link className="secondary-button button-link" to="/admin/question-categories">
            <Icon name="categories" size={16} />Categorías
          </Link>
          <Link className="primary-button button-link" to="/admin/questions/new">
            <Icon name="plus" size={16} />Nueva pregunta
          </Link>
        </div>
      </div>

      <section className="question-filter-panel" aria-label="Filtros">
        <div className="form-field question-search-field">
          <label htmlFor="query">Buscar</label>
          <input
            id="query"
            value={query}
            placeholder="Enunciado o categoría"
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') applyFilters()
            }}
          />
        </div>

        <div className="form-field">
          <label htmlFor="status">Estado</label>
          <select
            id="status"
            value={status}
            onChange={(event) =>
              setStatus(event.target.value as QuestionStatus | '')
            }
          >
            {statusOptions.map((option) => (
              <option key={option.value || 'ALL'} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>

        <div className="form-field">
          <label htmlFor="typeCode">Tipo</label>
          <select
            id="typeCode"
            value={typeCode}
            onChange={(event) =>
              setTypeCode(event.target.value as QuestionTypeCode | '')
            }
          >
            <option value="">Todos los tipos</option>
            {catalogs?.types.map((type) => (
              <option key={type.code} value={type.code}>
                {type.name}
              </option>
            ))}
          </select>
        </div>

        <div className="form-field">
          <label htmlFor="difficultyCode">Dificultad</label>
          <select
            id="difficultyCode"
            value={difficultyCode}
            onChange={(event) =>
              setDifficultyCode(
                event.target.value as QuestionDifficultyCode | ''
              )
            }
          >
            <option value="">Todas</option>
            {catalogs?.difficulties.map((difficulty) => (
              <option key={difficulty.code} value={difficulty.code}>
                {difficulty.name}
              </option>
            ))}
          </select>
        </div>

        <div className="form-field">
          <label htmlFor="category">Categoría</label>
          <select
            id="category"
            value={categoryPublicId}
            onChange={(event) => setCategoryPublicId(event.target.value)}
          >
            <option value="">Todas las categorías</option>
            {catalogs?.categories.map((category) => (
              <option key={category.publicId} value={category.publicId}>
                {category.name}
              </option>
            ))}
          </select>
        </div>

        <div className="filter-actions">
          <button className="primary-button" type="button" onClick={applyFilters}>
            Aplicar
          </button>
          <button
            className="secondary-button"
            type="button"
            onClick={clearFilters}
          >
            Limpiar
          </button>
        </div>
      </section>

      {error && (
        <div className="error-message dashboard-error" role="alert">
          {error}
        </div>
      )}

      <section className="table-panel">
        <div className="table-summary">
          <span>
            {loading
              ? 'Consultando preguntas…'
              : `${data?.totalElements ?? 0} preguntas encontradas`}
          </span>
          <strong>Página {(data?.page ?? 0) + 1}</strong>
        </div>

        <div className="responsive-table">
          <table>
            <thead>
              <tr>
                <th>Pregunta</th>
                <th>Tipo</th>
                <th>Dificultad</th>
                <th>Categoría</th>
                <th>Estado</th>
                <th>Creación</th>
                <th aria-label="Acciones" />
              </tr>
            </thead>
            <tbody>
              {!loading && data?.content.length === 0 && (
                <tr>
                  <td className="empty-cell" colSpan={7}>
                    No hay preguntas que coincidan con los filtros.
                  </td>
                </tr>
              )}

              {data?.content.map((question) => (
                <tr key={question.publicId}>
                  <td>
                    <strong>{truncate(question.statement)}</strong>
                    <small>Versión {question.versionNumber}</small>
                  </td>
                  <td>{question.typeName}</td>
                  <td>{question.difficultyName}</td>
                  <td>{question.categoryName}</td>
                  <td>
                    <span
                      className={`entity-status question-status-${question.status.toLowerCase()}`}
                    >
                      {statusLabels[question.status]}
                    </span>
                  </td>
                  <td>{formatDate(question.createdAt)}</td>
                  <td>
                    <Link
                      className="table-link"
                      to={`/admin/questions/${question.publicId}`}
                    >
                      Ver
                      <Icon name="chevronRight" size={14} />
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {(data?.totalPages ?? 0) > 1 && (
          <div className="pagination-bar">
            <button
              className="secondary-button"
              type="button"
              disabled={page <= 0}
              onClick={() => goToPage(page - 1)}
            >
              Anterior
            </button>
            <span>
              Página {page + 1} de {data?.totalPages ?? 1}
            </span>
            <button
              className="secondary-button"
              type="button"
              disabled={page + 1 >= (data?.totalPages ?? 1)}
              onClick={() => goToPage(page + 1)}
            >
              Siguiente
            </button>
          </div>
        )}
      </section>
    </main>
  )
}
