import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useAuth } from '../features/authentication/context/AuthContext'
import {
  duplicateQuestion,
  getQuestion,
  getQuestionHistory,
  transitionQuestion
} from '../features/questions/api/questionApi'
import { ApiRequestError } from '../shared/api/apiClient'
import type {
  QuestionDetail,
  QuestionHistory,
  QuestionStatus
} from '../shared/types/questions'

const statusLabels: Record<QuestionStatus, string> = {
  DRAFT: 'Borrador',
  UNDER_REVIEW: 'En revisión',
  APPROVED: 'Aprobada',
  PUBLISHED: 'Publicada',
  ARCHIVED: 'Archivada'
}

function formatDate(value: string | null) {
  if (!value) return 'Sin registro'
  return new Intl.DateTimeFormat('es-MX', {
    dateStyle: 'long',
    timeStyle: 'short'
  }).format(new Date(value))
}

export function AdminQuestionDetailPage() {
  const { publicId } = useParams()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const { user } = useAuth()
  const [question, setQuestion] = useState<QuestionDetail | null>(null)
  const [history, setHistory] = useState<QuestionHistory | null>(null)
  const [loading, setLoading] = useState(true)
  const [processing, setProcessing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)

  const hasPermission = useCallback(
    (permission: string) => user?.permissions.includes(permission) ?? false,
    [user?.permissions]
  )

  const load = useCallback(async () => {
    if (!publicId) return
    const detail = await getQuestion(publicId)
    setQuestion(detail)
    if (hasPermission('QUESTION_VERSION_VIEW')) {
      setHistory(await getQuestionHistory(publicId))
    }
  }, [hasPermission, publicId])

  useEffect(() => {
    if (!publicId) {
      setError('No se indicó la pregunta.')
      setLoading(false)
      return
    }
    load()
      .catch((requestError) => setError(
        requestError instanceof ApiRequestError
          ? requestError.message
          : 'No fue posible consultar la pregunta.'
      ))
      .finally(() => setLoading(false))
  }, [load, publicId])

  async function changeStatus(
    targetStatus: QuestionStatus,
    confirmation: string,
    message: string
  ) {
    if (!question || !publicId || !window.confirm(confirmation)) return
    setProcessing(true)
    setError(null)
    try {
      const updated = await transitionQuestion(publicId, {
        targetStatus,
        expectedEntityVersion: question.entityVersion
      })
      setQuestion(updated)
      setSuccess(message)
      if (hasPermission('QUESTION_VERSION_VIEW')) {
        setHistory(await getQuestionHistory(publicId))
      }
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError ? requestError.message : 'No fue posible cambiar el estado.')
    } finally {
      setProcessing(false)
    }
  }

  async function handleDuplicate() {
    if (!publicId || !window.confirm('Se creará una nueva pregunta en borrador. ¿Continuar?')) return
    setProcessing(true)
    setError(null)
    try {
      const duplicated = await duplicateQuestion(publicId)
      navigate(`/admin/questions/${duplicated.publicId}?duplicated=1`)
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError ? requestError.message : 'No fue posible duplicar la pregunta.')
      setProcessing(false)
    }
  }

  if (loading) {
    return <main className="content-page"><p className="muted">Consultando pregunta…</p></main>
  }

  return (
    <main className="content-page narrow-content">
      <div className="page-heading">
        <div>
          <p className="eyebrow">Banco de preguntas</p>
          <h1>Detalle de pregunta</h1>
          <p className="muted">Administra el contenido, el estado editorial y sus versiones.</p>
        </div>
        <div className="heading-actions">
          <Link className="secondary-button button-link" to="/admin/questions">Volver</Link>
          {question && hasPermission('QUESTION_UPDATE') && (question.status === 'DRAFT' || question.status === 'PUBLISHED') && (
            <Link className="secondary-button button-link" to={`/admin/questions/${question.publicId}/edit`}>Editar</Link>
          )}
        </div>
      </div>

      {(searchParams.get('created') === '1' || searchParams.get('updated') === '1') && (
        <div className="success-message" role="status">La pregunta se guardó correctamente.</div>
      )}
      {searchParams.get('duplicated') === '1' && <div className="success-message">La pregunta se duplicó como borrador.</div>}
      {success && <div className="success-message" role="status">{success}</div>}
      {error && <div className="error-message">{error}</div>}

      {question && (
        <>
          <section className="workflow-actions" aria-label="Flujo editorial">
            <div>
              <strong>Acciones editoriales</strong>
              <span className="muted">Versión actual: {question.versionNumber}</span>
            </div>
            <div className="workflow-button-group">
              {question.status === 'DRAFT' && hasPermission('QUESTION_REVIEW') && <button className="primary-button" disabled={processing} onClick={() => void changeStatus('UNDER_REVIEW', '¿Enviar esta pregunta a revisión?', 'La pregunta fue enviada a revisión.')}>Enviar a revisión</button>}
              {question.status === 'UNDER_REVIEW' && hasPermission('QUESTION_APPROVE') && <button className="primary-button" disabled={processing} onClick={() => void changeStatus('APPROVED', '¿Aprobar esta pregunta?', 'La pregunta fue aprobada.')}>Aprobar</button>}
              {(question.status === 'UNDER_REVIEW' || question.status === 'APPROVED') && hasPermission('QUESTION_REVIEW') && <button className="secondary-button" disabled={processing} onClick={() => void changeStatus('DRAFT', '¿Devolver esta pregunta a borrador?', 'La pregunta volvió a borrador.')}>Devolver a borrador</button>}
              {question.status === 'APPROVED' && hasPermission('QUESTION_PUBLISH') && <button className="primary-button" disabled={processing} onClick={() => void changeStatus('PUBLISHED', 'La versión quedará disponible para futuras evaluaciones. ¿Publicar?', 'La pregunta fue publicada.')}>Publicar</button>}
              {question.status === 'PUBLISHED' && hasPermission('QUESTION_ARCHIVE') && <button className="danger-button" disabled={processing} onClick={() => void changeStatus('ARCHIVED', '¿Archivar esta pregunta publicada?', 'La pregunta fue archivada.')}>Archivar</button>}
              {hasPermission('QUESTION_DUPLICATE') && <button className="secondary-button" disabled={processing} onClick={() => void handleDuplicate()}>Duplicar</button>}
            </div>
          </section>

          <div className="question-detail-layout">
            <section className="detail-card question-statement-card">
              <div className="detail-card-heading">
                <div><span className="muted-label">Enunciado</span><h2>{question.statement}</h2></div>
                <span className={`entity-status question-status-${question.status.toLowerCase()}`}>{statusLabels[question.status]}</span>
              </div>
              {question.publishedVersionNumber && question.publishedVersionNumber !== question.versionNumber && (
                <div className="version-note">La versión {question.publishedVersionNumber} continúa publicada mientras se trabaja en la versión {question.versionNumber}.</div>
              )}
              <dl className="question-metadata-grid">
                <div><dt>Tipo</dt><dd>{question.typeName}</dd></div>
                <div><dt>Dificultad</dt><dd>{question.difficultyName}</dd></div>
                <div><dt>Categoría</dt><dd>{question.categoryName}</dd></div>
                <div><dt>Versión</dt><dd>{question.versionNumber}</dd></div>
                <div><dt>Creada</dt><dd>{formatDate(question.createdAt)}</dd></div>
                <div><dt>Última modificación</dt><dd>{formatDate(question.updatedAt)}</dd></div>
              </dl>
            </section>

            <section className="detail-card">
              <p className="eyebrow">Configuración</p><h2>Opciones de respuesta</h2>
              <ol className="question-answer-list">
                {question.options.map((option) => <li className={option.correct ? 'correct-answer' : ''} key={option.publicId}><span>{option.text}</span>{option.correct && <strong>Respuesta correcta</strong>}</li>)}
              </ol>
            </section>

            <section className="detail-card">
              <p className="eyebrow">Retroalimentación</p><h2>Explicación</h2>
              <p className="question-explanation">{question.explanation || 'No se agregó una explicación.'}</p>
            </section>

            {history && (
              <section className="detail-card">
                <p className="eyebrow">Trazabilidad</p><h2>Historial de versiones</h2>
                <div className="version-history-list">
                  {history.versions.map((version) => (
                    <article key={version.versionNumber}>
                      <div><strong>Versión {version.versionNumber}</strong><span className={`entity-status question-status-${version.status.toLowerCase()}`}>{statusLabels[version.status]}</span></div>
                      <p>{version.changeSummary || 'Sin resumen de cambios.'}</p>
                      <small>Creada: {formatDate(version.createdAt)}{version.publishedAt ? ` · Publicada: ${formatDate(version.publishedAt)}` : ''}</small>
                    </article>
                  ))}
                </div>
              </section>
            )}
          </div>
        </>
      )}
    </main>
  )
}
