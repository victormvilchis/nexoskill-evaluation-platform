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
import { ConfirmDialog } from '../shared/components/ConfirmDialog'
import { Icon } from '../shared/components/Icon'
import { useToast } from '../shared/components/ToastProvider'
import type { QuestionDetail, QuestionHistory, QuestionStatus } from '../shared/types/questions'

const statusLabels: Record<QuestionStatus, string> = {
  DRAFT: 'Estado anterior',
  UNDER_REVIEW: 'Estado anterior',
  APPROVED: 'Estado anterior',
  PUBLISHED: 'Publicada',
  ARCHIVED: 'Archivada'
}

function formatDate(value: string | null) {
  if (!value) return 'Sin registro'
  return new Intl.DateTimeFormat('es-MX', {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(new Date(value))
}

export function AdminQuestionDetailPage() {
  const { publicId } = useParams()
  const [searchParams, setSearchParams] = useSearchParams()
  const navigate = useNavigate()
  const { user } = useAuth()
  const toast = useToast()
  const [question, setQuestion] = useState<QuestionDetail | null>(null)
  const [history, setHistory] = useState<QuestionHistory | null>(null)
  const [loading, setLoading] = useState(true)
  const [processing, setProcessing] = useState(false)
  const [criticalError, setCriticalError] = useState<string | null>(null)
  const [confirmation, setConfirmation] = useState<'archive' | 'duplicate' | null>(null)

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
      setCriticalError('No se indicó la pregunta.')
      setLoading(false)
      return
    }
    load()
      .catch((requestError) => {
        const message = requestError instanceof ApiRequestError
          ? requestError.message
          : 'No fue posible consultar la pregunta.'
        setCriticalError(message)
        toast.error('No fue posible cargar la pregunta', message)
      })
      .finally(() => setLoading(false))
  }, [load, publicId, toast])

  useEffect(() => {
    const created = searchParams.get('created') === '1'
    const updated = searchParams.get('updated') === '1'
    const duplicated = searchParams.get('duplicated') === '1'
    if (!created && !updated && !duplicated) return

    if (created) toast.success('Pregunta creada', 'La pregunta quedó publicada y disponible.')
    if (updated) toast.success('Cambios publicados', 'Se creó una nueva versión de la pregunta.')
    if (duplicated) toast.success('Pregunta duplicada', 'La copia quedó publicada correctamente.')
    setSearchParams(new URLSearchParams(), { replace: true })
  }, [searchParams, setSearchParams, toast])

  async function archiveQuestion() {
    if (!question || !publicId) return
    setProcessing(true)
    try {
      const updated = await transitionQuestion(publicId, {
        targetStatus: 'ARCHIVED',
        expectedEntityVersion: question.entityVersion
      })
      setQuestion(updated)
      if (hasPermission('QUESTION_VERSION_VIEW')) {
        setHistory(await getQuestionHistory(publicId))
      }
      toast.success('Pregunta archivada', 'Ya no estará disponible para nuevas evaluaciones.')
      setConfirmation(null)
    } catch (requestError) {
      toast.error(
        'No fue posible archivar',
        requestError instanceof ApiRequestError
          ? requestError.message
          : 'Intenta nuevamente.'
      )
    } finally {
      setProcessing(false)
    }
  }

  async function handleDuplicate() {
    if (!publicId) return
    setProcessing(true)
    try {
      const duplicated = await duplicateQuestion(publicId)
      navigate(`/admin/questions/${duplicated.publicId}?duplicated=1`)
    } catch (requestError) {
      toast.error(
        'No fue posible duplicar',
        requestError instanceof ApiRequestError
          ? requestError.message
          : 'Intenta nuevamente.'
      )
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
          <p className="muted">Consulta el contenido, clasificación e historial.</p>
        </div>
        <div className="heading-actions">
          <Link className="secondary-button button-link" to="/admin/questions">
            Volver
          </Link>
          {question && hasPermission('QUESTION_UPDATE') && (
            <Link className="primary-button button-link" to={`/admin/questions/${question.publicId}/edit`}>
              <Icon name="edit" size={16} />Editar
            </Link>
          )}
        </div>
      </div>

      {criticalError && <div className="error-message">{criticalError}</div>}

      {question && (
        <>
          <section className="compact-action-bar" aria-label="Acciones de pregunta">
            <div className="compact-action-summary">
              <span className={`entity-status question-status-${question.status.toLowerCase()}`}>
                {statusLabels[question.status]}
              </span>
              <span>Versión {question.versionNumber}</span>
            </div>
            <div className="heading-actions">
              {hasPermission('QUESTION_DUPLICATE') && (
                <button className="secondary-button" disabled={processing} type="button" onClick={() => setConfirmation('duplicate')}>
                  <Icon name="copy" size={16} />Duplicar
                </button>
              )}
              {question.status === 'PUBLISHED' && hasPermission('QUESTION_ARCHIVE') && (
                <button className="danger-button" disabled={processing} type="button" onClick={() => setConfirmation('archive')}>
                  <Icon name="archive" size={16} />Archivar
                </button>
              )}
            </div>
          </section>

          <div className="question-detail-layout">
            <section className="detail-card question-statement-card">
              <div className="detail-card-heading">
                <div>
                  <span className="muted-label">Enunciado</span>
                  <h2>{question.statement}</h2>
                </div>
              </div>
              <dl className="question-metadata-grid">
                <div><dt>Tipo</dt><dd>{question.typeName}</dd></div>
                <div><dt>Dificultad</dt><dd>{question.difficultyName}</dd></div>
                <div><dt>Categoría</dt><dd>{question.categoryName}</dd></div>
                <div><dt>Versión</dt><dd>{question.versionNumber}</dd></div>
                <div><dt>Creación</dt><dd>{formatDate(question.createdAt)}</dd></div>
                <div><dt>Modificación</dt><dd>{formatDate(question.updatedAt)}</dd></div>
              </dl>
            </section>

            <section className="detail-card">
              <div className="card-title-row">
                <div><p className="eyebrow">Configuración</p><h2>Opciones de respuesta</h2></div>
                <span className="count-badge">{question.options.length}</span>
              </div>
              <ol className="question-answer-list">
                {question.options.map((option) => (
                  <li className={option.correct ? 'correct-answer' : ''} key={option.publicId}>
                    <span>{option.text}</span>
                    {option.correct && <strong><Icon name="check" size={14} />Correcta</strong>}
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

            {history && (
              <section className="detail-card">
                <p className="eyebrow">Trazabilidad</p>
                <h2>Historial de versiones</h2>
                <div className="version-history-list">
                  {history.versions.map((version) => (
                    <article key={version.versionNumber}>
                      <div>
                        <strong>Versión {version.versionNumber}</strong>
                        <span className={`entity-status question-status-${version.status.toLowerCase()}`}>
                          {statusLabels[version.status]}
                        </span>
                      </div>
                      <p>{version.changeSummary || 'Actualización de contenido.'}</p>
                      <small>
                        {formatDate(version.createdAt)}
                        {version.publishedAt ? ` · Publicada ${formatDate(version.publishedAt)}` : ''}
                      </small>
                    </article>
                  ))}
                </div>
              </section>
            )}
          </div>
        </>
      )}

      <ConfirmDialog
        open={confirmation === 'archive'}
        title="Archivar pregunta"
        description="La pregunta dejará de estar disponible para nuevas evaluaciones. Su historial se conservará."
        confirmLabel="Archivar"
        tone="danger"
        busy={processing}
        onCancel={() => setConfirmation(null)}
        onConfirm={() => void archiveQuestion()}
      />
      <ConfirmDialog
        open={confirmation === 'duplicate'}
        title="Duplicar pregunta"
        description="Se creará una copia independiente y se publicará automáticamente."
        confirmLabel="Duplicar y publicar"
        busy={processing}
        onCancel={() => setConfirmation(null)}
        onConfirm={() => void handleDuplicate()}
      />
    </main>
  )
}
