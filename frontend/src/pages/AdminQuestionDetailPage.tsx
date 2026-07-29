import { BackButton } from '../shared/components/BackButton'
import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { getQuestion } from '../features/questions/api/questionApi'
import { JavaCodePreview } from '../features/questions/components/JavaCodePanel'
import { ApiRequestError } from '../shared/api/apiClient'
import { Icon } from '../shared/components/Icon'
import { LoadingScreen } from '../shared/components/LoadingScreen'
import type { QuestionDetail } from '../shared/types/questions'

export function AdminQuestionDetailPage() {
  const { publicId = '' } = useParams()
  const [question, setQuestion] = useState<QuestionDetail>()
  const [error, setError] = useState<string>()
  const [reloadKey, setReloadKey] = useState(0)
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

  return (
    <main className="content-page narrow-content resource-page">
      <BackButton fallback="/admin/questions" />
      <div className="page-heading resource-heading">
        <div><p className="eyebrow">Pregunta</p><h1>Detalle</h1><p className="muted">Vista de solo lectura.</p></div>
      </div>

      <section className={`detail-card question-preview question-preview-v2 ${deleted ? 'deleted-detail' : ''}`}>
        <div className="question-card-top">
          <span className={`status-badge status-${question.status.toLowerCase()}`}>
            {question.status === 'ACTIVE' ? 'Activa' : question.status === 'ARCHIVED' ? 'Inactiva' : 'Eliminada'}
          </span>
          <span>{question.typeName}</span>
        </div>
        <h2>{question.statement}</h2>
        {question.promptMedia && <img className="question-prompt-image" src={question.promptMedia.url} alt={question.promptMedia.originalName} />}
        {question.codeContent && <JavaCodePreview code={question.codeContent} />}

        <div className="chip-row">
          {question.categories.map((category) => <span className="category-chip" key={category.publicId}>{category.name}</span>)}
        </div>
        {question.tags.length > 0 && (
          <div className="question-tag-summary" aria-label="Etiquetas temáticas">
            {question.tags.map((tag) => (
              <span className="question-tag-chip question-tag-chip--readonly" key={tag.publicId}>#{tag.slug}</span>
            ))}
          </div>
        )}
        <div className="question-governance-detail-grid">

          <div><span>Alcance</span><strong>{question.ownership.scope}</strong></div>
          <div><span>Organización propietaria</span><strong>{question.ownership.organizationName ?? 'Sin propietario'}</strong></div>
          <div><span>Tecnología</span><strong>{question.technology?.name ?? 'Sin tecnología'}</strong></div>
          <div><span>Dificultad</span><strong>{question.difficultyName ?? 'Sin dificultad'}</strong></div>
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

    </main>
  )
}
