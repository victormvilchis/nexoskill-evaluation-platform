import { useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { getQuestion } from '../features/questions/api/questionApi'
import { ApiRequestError } from '../shared/api/apiClient'
import type { QuestionDetail, QuestionStatus } from '../shared/types/questions'

const statusLabels: Record<QuestionStatus, string> = {
  DRAFT: 'Borrador',
  UNDER_REVIEW: 'En revisión',
  APPROVED: 'Aprobada',
  PUBLISHED: 'Publicada',
  ARCHIVED: 'Archivada'
}

function formatDate(value: string | null) {
  if (!value) return 'Sin modificaciones'
  return new Intl.DateTimeFormat('es-MX', {
    dateStyle: 'long',
    timeStyle: 'short'
  }).format(new Date(value))
}

export function AdminQuestionDetailPage() {
  const { publicId } = useParams()
  const [searchParams] = useSearchParams()
  const [question, setQuestion] = useState<QuestionDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!publicId) {
      setError('No se indicó la pregunta.')
      setLoading(false)
      return
    }

    getQuestion(publicId)
      .then(setQuestion)
      .catch((requestError) =>
        setError(
          requestError instanceof ApiRequestError
            ? requestError.message
            : 'No fue posible consultar la pregunta.'
        )
      )
      .finally(() => setLoading(false))
  }, [publicId])

  if (loading) {
    return (
      <main className="content-page">
        <p className="muted">Consultando pregunta…</p>
      </main>
    )
  }

  return (
    <main className="content-page narrow-content">
      <div className="page-heading">
        <div>
          <p className="eyebrow">Banco de preguntas</p>
          <h1>Detalle de pregunta</h1>
          <p className="muted">
            Consulta la versión vigente y sus respuestas configuradas.
          </p>
        </div>
        <Link className="secondary-button button-link" to="/admin/questions">
          Volver
        </Link>
      </div>

      {searchParams.get('created') === '1' && (
        <div className="success-message" role="status">
          La pregunta se guardó correctamente como borrador.
        </div>
      )}

      {error && <div className="error-message">{error}</div>}

      {question && (
        <div className="question-detail-layout">
          <section className="detail-card question-statement-card">
            <div className="detail-card-heading">
              <div>
                <span className="muted-label">Enunciado</span>
                <h2>{question.statement}</h2>
              </div>
              <span
                className={`entity-status question-status-${question.status.toLowerCase()}`}
              >
                {statusLabels[question.status]}
              </span>
            </div>

            <dl className="question-metadata-grid">
              <div>
                <dt>Tipo</dt>
                <dd>{question.typeName}</dd>
              </div>
              <div>
                <dt>Dificultad</dt>
                <dd>{question.difficultyName}</dd>
              </div>
              <div>
                <dt>Categoría</dt>
                <dd>{question.categoryName}</dd>
              </div>
              <div>
                <dt>Versión</dt>
                <dd>{question.versionNumber}</dd>
              </div>
              <div>
                <dt>Creada</dt>
                <dd>{formatDate(question.createdAt)}</dd>
              </div>
              <div>
                <dt>Última modificación</dt>
                <dd>{formatDate(question.updatedAt)}</dd>
              </div>
            </dl>
          </section>

          <section className="detail-card">
            <div className="section-heading compact-section-heading">
              <div>
                <p className="eyebrow">Configuración</p>
                <h2>Opciones de respuesta</h2>
              </div>
            </div>

            <ol className="question-answer-list">
              {question.options.map((option) => (
                <li
                  className={option.correct ? 'correct-answer' : ''}
                  key={option.publicId}
                >
                  <span>{option.text}</span>
                  {option.correct && <strong>Respuesta correcta</strong>}
                </li>
              ))}
            </ol>
          </section>

          <section className="detail-card">
            <p className="eyebrow">Retroalimentación</p>
            <h2>Explicación</h2>
            <p className="question-explanation">
              {question.explanation || 'No se agregó una explicación.'}
            </p>
          </section>
        </div>
      )}
    </main>
  )
}
