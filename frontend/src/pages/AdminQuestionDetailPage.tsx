import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  changeQuestionStatus,
  duplicateQuestion,
  getQuestion
} from '../features/questions/api/questionApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { ConfirmDialog } from '../shared/components/ConfirmDialog'
import { Icon } from '../shared/components/Icon'
import { LoadingScreen } from '../shared/components/LoadingScreen'
import { useToast } from '../shared/components/ToastProvider'
import type { QuestionDetail } from '../shared/types/questions'

export function AdminQuestionDetailPage() {
  const { publicId = '' } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const [question, setQuestion] = useState<QuestionDetail>()
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [error, setError] = useState<string>()
  const [reloadKey, setReloadKey] = useState(0)
  const [actionBusy, setActionBusy] = useState(false)

  const reload = useCallback(() => setReloadKey((value) => value + 1), [])

  useEffect(() => {
    const controller = new AbortController()
    setError(undefined)

    getQuestion(publicId, controller.signal)
      .then(setQuestion)
      .catch((requestError: unknown) => {
        if (controller.signal.aborted) return
        setError(
          requestError instanceof ApiRequestError
            ? requestError.message
            : 'No fue posible consultar la pregunta.'
        )
      })

    return () => controller.abort()
  }, [publicId, reloadKey])

  if (error && !question) {
    return (
      <main className="content-page">
        <section className="inline-error-panel" role="alert">
          <div className="inline-error-icon"><Icon name="error" /></div>
          <div>
            <strong>No fue posible cargar la pregunta</strong>
            <p>{error}</p>
          </div>
          <button className="secondary-button" onClick={reload}>Reintentar</button>
        </section>
      </main>
    )
  }

  if (!question) return <LoadingScreen />

  async function toggleStatus() {
    setActionBusy(true)
    try {
      const target = question!.status === 'ACTIVE' ? 'ARCHIVED' : 'ACTIVE'
      const updated = await changeQuestionStatus(
        publicId,
        target,
        question!.entityVersion
      )
      setQuestion(updated)
      setConfirmOpen(false)
      toast.success(target === 'ACTIVE' ? 'Pregunta reactivada' : 'Pregunta archivada')
    } catch (requestError) {
      toast.error(
        'No fue posible cambiar el estado',
        requestError instanceof ApiRequestError ? requestError.message : undefined
      )
    } finally {
      setActionBusy(false)
    }
  }

  async function duplicate() {
    setActionBusy(true)
    try {
      const copy = await duplicateQuestion(publicId)
      toast.success('Pregunta duplicada')
      navigate(`/admin/questions/${copy.publicId}/edit`)
    } catch (requestError) {
      toast.error(
        'No fue posible duplicar la pregunta',
        requestError instanceof ApiRequestError ? requestError.message : undefined
      )
    } finally {
      setActionBusy(false)
    }
  }

  return (
    <main className="content-page narrow-content resource-page">
      <div className="page-heading resource-heading">
        <div>
          <p className="eyebrow">Pregunta</p>
          <h1>Detalle</h1>
        </div>
        <div className="heading-actions resource-heading-actions">
          <button
            className="secondary-button"
            disabled={actionBusy}
            onClick={() => void duplicate()}
          >
            <Icon name="copy" size={15} /> Duplicar
          </button>
          {question.status === 'ACTIVE' && (
            <Link
              className="primary-button button-link"
              to={`/admin/questions/${publicId}/edit`}
            >
              <Icon name="edit" size={15} /> Editar
            </Link>
          )}
          <button
            className="secondary-button"
            disabled={actionBusy}
            onClick={() => setConfirmOpen(true)}
          >
            {question.status === 'ACTIVE' ? 'Archivar' : 'Reactivar'}
          </button>
        </div>
      </div>

      <section className="detail-card question-preview">
        <div className="question-card-top">
          <span className={`status-badge status-${question.status.toLowerCase()}`}>
            {question.status === 'ACTIVE' ? 'Activa' : 'Archivada'}
          </span>
          <span>{question.typeName} · {question.difficultyName}</span>
        </div>

        <h2>{question.statement}</h2>

        {question.promptMedia && (
          <img
            className="question-prompt-image"
            src={question.promptMedia.url}
            alt={question.promptMedia.originalName}
          />
        )}

        {question.codeContent && (
          <pre className="code-preview"><code>{question.codeContent}</code></pre>
        )}

        <div className="chip-row">
          {question.categories.map((category) => (
            <span className="category-chip" key={category.publicId}>
              {category.name}
            </span>
          ))}
        </div>

        {question.options.length > 0 && (
          <div className="answer-preview-list">
            {question.options.map((option) => (
              <div className={option.correct ? 'correct' : ''} key={option.publicId}>
                <span>{option.correct ? '✓' : '○'}</span>
                <div>
                  {option.text && <p>{option.text}</p>}
                  {option.media && (
                    <img src={option.media.url} alt={option.media.originalName} />
                  )}
                </div>
              </div>
            ))}
          </div>
        )}

        {question.explanation && (
          <div className="explanation-box">
            <strong>Explicación</strong>
            <p>{question.explanation}</p>
          </div>
        )}
      </section>

      <ConfirmDialog
        open={confirmOpen}
        title={question.status === 'ACTIVE' ? 'Archivar pregunta' : 'Reactivar pregunta'}
        description={
          question.status === 'ACTIVE'
            ? 'Dejará de estar disponible para nuevas colecciones y formularios.'
            : 'Volverá a estar disponible.'
        }
        confirmLabel={question.status === 'ACTIVE' ? 'Archivar' : 'Reactivar'}
        tone={question.status === 'ACTIVE' ? 'danger' : 'primary'}
        onCancel={() => setConfirmOpen(false)}
        onConfirm={() => void toggleStatus()}
      />
    </main>
  )
}
