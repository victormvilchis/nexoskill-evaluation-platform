import { BackButton } from '../shared/components/BackButton'
import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  changeQuestionStatus,
  deleteQuestion,
  duplicateQuestion,
  getQuestion,
  restoreQuestion
} from '../features/questions/api/questionApi'
import { JavaCodePreview } from '../features/questions/components/JavaCodePanel'
import { ApiRequestError } from '../shared/api/apiClient'
import { ConfirmDialog } from '../shared/components/ConfirmDialog'
import { Icon } from '../shared/components/Icon'
import { LoadingScreen } from '../shared/components/LoadingScreen'
import { useToast } from '../shared/components/ToastProvider'
import type { QuestionDetail } from '../shared/types/questions'

type Action = 'STATUS' | 'DELETE' | 'RESTORE' | null

export function AdminQuestionDetailPage() {
  const { publicId = '' } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const [question, setQuestion] = useState<QuestionDetail>()
  const [action, setAction] = useState<Action>(null)
  const [error, setError] = useState<string>()
  const [reloadKey, setReloadKey] = useState(0)
  const [busy, setBusy] = useState(false)
  const reload = useCallback(() => setReloadKey((value) => value + 1), [])

  useEffect(() => {
    const controller = new AbortController()
    setError(undefined)
    getQuestion(publicId, controller.signal)
      .then(setQuestion)
      .catch((requestError: unknown) => {
        if (!controller.signal.aborted) {
          setError(requestError instanceof ApiRequestError
            ? requestError.message
            : 'No fue posible consultar la pregunta.')
        }
      })
    return () => controller.abort()
  }, [publicId, reloadKey])

  if (error && !question) {
    return (
      <main className="content-page">
        <BackButton fallback="/admin/questions" />
        <section className="inline-error-panel">
          <div className="inline-error-icon"><Icon name="error" /></div>
          <div><strong>No fue posible cargar la pregunta</strong><p>{error}</p></div>
          <button className="secondary-button" onClick={reload}>Reintentar</button>
        </section>
      </main>
    )
  }
  if (!question) return <LoadingScreen />

  const deleted = question.status === 'DELETED'

  async function execute() {
    if (!action || !question) return
    setBusy(true)
    try {
      let updated: QuestionDetail
      if (action === 'DELETE') {
        updated = await deleteQuestion(publicId, question.entityVersion, 'Eliminación administrativa')
      } else if (action === 'RESTORE') {
        updated = await restoreQuestion(publicId, question.entityVersion)
      } else {
        updated = await changeQuestionStatus(
          publicId,
          question.status === 'ACTIVE' ? 'ARCHIVED' : 'ACTIVE',
          question.entityVersion
        )
      }
      setQuestion(updated)
      setAction(null)
      toast.success(action === 'DELETE'
        ? 'Pregunta eliminada'
        : action === 'RESTORE'
          ? 'Pregunta restaurada como archivada'
          : updated.status === 'ACTIVE'
            ? 'Pregunta reactivada'
            : 'Pregunta archivada')
    } catch (requestError) {
      toast.error('No fue posible completar la operación',
        requestError instanceof ApiRequestError ? requestError.message : undefined)
    } finally {
      setBusy(false)
    }
  }

  async function duplicate() {
    setBusy(true)
    try {
      const copy = await duplicateQuestion(publicId)
      toast.success('Pregunta duplicada')
      navigate(`/admin/questions/${copy.publicId}/edit`)
    } catch (requestError) {
      toast.error('No fue posible duplicar la pregunta',
        requestError instanceof ApiRequestError ? requestError.message : undefined)
    } finally {
      setBusy(false)
    }
  }

  const title = action === 'DELETE'
    ? 'Eliminar pregunta'
    : action === 'RESTORE'
      ? 'Restaurar pregunta'
      : question.status === 'ACTIVE'
        ? 'Archivar pregunta'
        : 'Reactivar pregunta'
  const description = action === 'DELETE'
    ? 'Desaparecerá del banco normal, pero el registro permanecerá almacenado.'
    : action === 'RESTORE'
      ? 'La pregunta volverá como archivada. Después podrás reactivarla.'
      : question.status === 'ACTIVE'
        ? 'Dejará de estar disponible para formularios nuevos.'
        : 'Volverá a estar disponible.'

  return (
    <main className="content-page narrow-content resource-page">
      <BackButton fallback="/admin/questions" />
      <div className="page-heading resource-heading">
        <div><p className="eyebrow">Pregunta</p><h1>Detalle</h1></div>
        <div className="heading-actions resource-heading-actions">
          {!deleted && (
            <>
              <button className="secondary-button" disabled={busy} onClick={() => void duplicate()}>
                <Icon name="copy" size={15} /> Duplicar
              </button>
              {question.status === 'ACTIVE' && (
                <Link className="primary-button button-link" to={`/admin/questions/${publicId}/edit`}>
                  <Icon name="edit" size={15} /> Editar
                </Link>
              )}
              <button className="secondary-button" disabled={busy} onClick={() => setAction('STATUS')}>
                {question.status === 'ACTIVE' ? 'Archivar' : 'Reactivar'}
              </button>
              <button className="danger-button" disabled={busy} onClick={() => setAction('DELETE')}>Eliminar</button>
            </>
          )}
          {deleted && <button className="primary-button" disabled={busy} onClick={() => setAction('RESTORE')}>Restaurar</button>}
        </div>
      </div>

      <section className={`detail-card question-preview question-preview-v2 ${deleted ? 'deleted-detail' : ''}`}>
        <div className="question-card-top">
          <span className={`status-badge status-${question.status.toLowerCase()}`}>
            {question.status === 'ACTIVE' ? 'Activa' : question.status === 'ARCHIVED' ? 'Archivada' : 'Eliminada'}
          </span>
          <span>{question.typeName}</span>
        </div>
        <h2>{question.statement}</h2>
        {question.promptMedia && <img className="question-prompt-image" src={question.promptMedia.url} alt={question.promptMedia.originalName} />}
        {question.codeContent && <JavaCodePreview code={question.codeContent} />}

        <div className="chip-row">
          {question.categories.map((category) => <span className="category-chip" key={category.publicId}>{category.name}</span>)}
        </div>

        <div className="question-governance-detail-grid">
          <div><span>Alcance</span><strong>{question.ownership.scope}</strong></div>
          <div><span>Organización propietaria</span><strong>{question.ownership.organizationName ?? 'Sin propietario'}</strong></div>
          <div><span>Tecnología</span><strong>{question.technology?.name ?? 'Sin tecnología'}</strong></div>
          <div><span>Dificultad</span><strong>{question.difficultyName ?? 'Sin dificultad'}</strong></div>
          <div><span>Nivel</span><strong>{question.levelCode ?? 'Sin nivel'}</strong></div>
          <div><span>Usuario creador</span><strong>{question.ownership.creatorName ?? 'Sin registro'}</strong></div>
          {question.ownership.clonedToGlobal && (
            <div className="question-governance-wide"><span>Origen organizacional</span><strong>{question.ownership.sourceOrganizationName ?? 'Sin registro'} · Versión {question.ownership.sourceQuestionVersion ?? '—'}</strong></div>
          )}
        </div>

        {question.options.length > 0 && question.typeCode !== 'MATCHING' && (
          <div className="answer-preview-list answer-preview-list-v2">
            {question.options.map((option) => (
              <div className={option.correct ? 'correct' : ''} key={option.publicId}>
                <span>{option.correct ? '✓' : '○'}</span>
                <div>
                  {option.text && <p>{option.text}</p>}
                  {option.media && <img src={option.media.url} alt={option.media.originalName} />}
                  {option.feedback && <small className="option-feedback-preview">{option.feedback}</small>}
                </div>
              </div>
            ))}
          </div>
        )}

        {question.typeCode === 'MATCHING' && (
          <div className="matching-preview-list">
            {question.options.map((option, index) => (
              <div className="matching-preview-row" key={option.publicId}>
                <span className="matching-preview-number">{index + 1}</span>
                <div>{option.text && <p>{option.text}</p>}{option.media && <img src={option.media.url} alt={option.media.originalName} />}</div>
                <span className="matching-preview-arrow">↔</span>
                <div>{option.matchText && <p>{option.matchText}</p>}{option.matchMedia && <img src={option.matchMedia.url} alt={option.matchMedia.originalName} />}</div>
                {option.feedback && <small>{option.feedback}</small>}
              </div>
            ))}
          </div>
        )}

        {question.typeCode === 'OPEN_TEXT' && (
          <div className="open-answer-preview">Respuesta abierta · Revisión manual</div>
        )}

        {question.explanation && (
          <div className="explanation-box"><strong>Explicación general</strong><p>{question.explanation}</p></div>
        )}

        <div className="question-usage-panel">
          <div>
            <strong>Formularios</strong>
            {question.forms.length
              ? question.forms.map((form) => <span key={form.publicId}>{form.name}</span>)
              : <em>Esta pregunta no está en ningún formulario.</em>}
          </div>
          <div>
            <strong>Colecciones</strong>
            {question.collections.length
              ? question.collections.map((collection) => <span key={collection.publicId}>{collection.name}</span>)
              : <em>Esta pregunta no está en ninguna colección.</em>}
          </div>
        </div>
      </section>

      <ConfirmDialog
        open={action !== null}
        title={title}
        description={description}
        confirmLabel={action === 'DELETE' ? 'Eliminar' : action === 'RESTORE' ? 'Restaurar' : question.status === 'ACTIVE' ? 'Archivar' : 'Reactivar'}
        tone={action === 'DELETE' || question.status === 'ACTIVE' ? 'danger' : 'primary'}
        onCancel={() => setAction(null)}
        onConfirm={() => void execute()}
      />
    </main>
  )
}
