import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  changeQuestionStatus,
  deleteQuestion,
  getQuestion,
  restoreQuestion
} from '../features/questions/api/questionApi'
import { ApiRequestError, isPlatformRequestFailure } from '../shared/api/apiClient'
import { BackButton } from '../shared/components/BackButton'
import { ConfirmDialog } from '../shared/components/ConfirmDialog'
import { Icon } from '../shared/components/Icon'
import { LoadingScreen } from '../shared/components/LoadingScreen'
import { useToast } from '../shared/components/ToastProvider'
import type { QuestionDetail } from '../shared/types/questions'

type Action = 'ACTIVATE' | 'INACTIVATE' | 'DELETE' | 'RESTORE'

const labels = {
  ACTIVE: 'Activa',
  ARCHIVED: 'Inactiva',
  DELETED: 'Eliminada'
} as const

export function QuestionManagementPage() {
  const { publicId = '' } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const [question, setQuestion] = useState<QuestionDetail>()
  const [error, setError] = useState<string>()
  const [action, setAction] = useState<Action>()
  const [busy, setBusy] = useState(false)
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

  async function execute() {
    if (!question || !action || busy) return
    setBusy(true)
    try {
      if (action === 'ACTIVATE') {
        await changeQuestionStatus(question.publicId, 'ACTIVE', question.entityVersion)
      } else if (action === 'INACTIVATE') {
        await changeQuestionStatus(question.publicId, 'ARCHIVED', question.entityVersion)
      } else if (action === 'DELETE') {
        await deleteQuestion(question.publicId, question.entityVersion,
          'Eliminación física desde Administrar pregunta.')
      } else {
        await restoreQuestion(question.publicId, question.entityVersion)
      }
      toast.success(action === 'DELETE' ? 'Pregunta eliminada correctamente.' : action === 'INACTIVATE' ? 'Pregunta inactivada correctamente.' : action === 'ACTIVATE' ? 'Pregunta activada correctamente.' : 'Pregunta restaurada correctamente.')
      setAction(undefined)
      reload()
    } catch (requestError) {
      if (!isPlatformRequestFailure(requestError)) {
        toast.error('No fue posible completar la operación.',
          requestError instanceof ApiRequestError ? requestError.message : undefined)
      }
    } finally {
      setBusy(false)
    }
  }

  if (error && !question) {
    return <main className="content-page"><BackButton fallback="/admin/questions" /><section className="inline-error-panel" role="alert"><Icon name="error" /><div><strong>No fue posible cargar la administración</strong><p>{error}</p></div><button className="secondary-button" type="button" onClick={reload}>Reintentar</button></section></main>
  }
  if (!question) return <LoadingScreen />

  const hasDependencies = question.forms.length > 0 || question.collections.length > 0
  const title = action === 'ACTIVATE' ? 'Activar pregunta'
    : action === 'INACTIVATE' ? 'Inactivar pregunta'
      : action === 'DELETE' ? 'Eliminar pregunta'
        : 'Restaurar pregunta'
  const description = action === 'DELETE' && hasDependencies
    ? 'La pregunta está relacionada con contenido actual. La eliminación será física y el backend impedirá la operación si existe actividad histórica que deba conservarse.'
    : action === 'DELETE'
      ? 'La eliminación será física y definitiva. Si existe actividad histórica, la operación será rechazada y deberás inactivar la pregunta.'
      : action === 'INACTIVATE'
        ? 'La pregunta dejará de estar disponible para nuevos usos y se retirará de relaciones operativas, conservando la información histórica.'
        : 'Confirma el cambio de estado de la pregunta.'

  return (
    <main className="content-page narrow-content resource-page">
      <BackButton fallback="/admin/questions" />
      <div className="page-heading resource-heading"><div><p className="eyebrow">Banco de preguntas</p><h1>Administrar pregunta</h1><p className="muted">Inactivación lógica, dependencias y eliminación física.</p></div></div>

      <section className="detail-card">
        <div className="question-card-top"><span className={`status-badge status-${question.status.toLowerCase()}`}>{labels[question.status]}</span><span>{question.ownership.scope === 'GLOBAL' ? 'GLOBAL' : question.ownership.organizationName}</span></div>
        <h2>{question.statement}</h2>
        <div className="question-governance-detail-grid">
          <div><span>Alcance</span><strong>{question.ownership.scope === 'GLOBAL' ? 'Global' : 'Organización'}</strong></div>
          <div><span>Organización propietaria</span><strong>{question.ownership.organizationName ?? 'GLOBAL'}</strong></div>
          <div><span>Formularios relacionados</span><strong>{question.forms.length}</strong></div>
          <div><span>Colecciones relacionadas</span><strong>{question.collections.length}</strong></div>
        </div>
      </section>

      <section className="detail-card">
        <div className="section-heading"><div><p className="eyebrow">Estado</p><h2>Acciones disponibles</h2></div></div>
        <div className="button-row">
          {question.status === 'ACTIVE' && <button className="secondary-button" type="button" onClick={() => setAction('INACTIVATE')}>Inactivar</button>}
          {question.status === 'ARCHIVED' && <button className="primary-button" type="button" onClick={() => setAction('ACTIVATE')}>Activar</button>}
          {question.status !== 'DELETED' && <button className="danger-button" type="button" onClick={() => setAction('DELETE')}>Eliminar</button>}
          {question.status === 'DELETED' && <button className="primary-button" type="button" onClick={() => setAction('RESTORE')}>Restaurar como inactiva</button>}
          <button className="secondary-button" type="button" onClick={() => navigate(`/admin/questions/${question.publicId}`)}>Ver pregunta</button>
        </div>
      </section>

      <section className="detail-card question-usage-panel">
        <div><strong>Formularios</strong>{question.forms.length ? question.forms.map((form) => <span key={form.publicId}>{form.name}</span>) : <em>Sin formularios relacionados.</em>}</div>
        <div><strong>Colecciones</strong>{question.collections.length ? question.collections.map((collection) => <span key={collection.publicId}>{collection.name}</span>) : <em>Sin colecciones relacionadas.</em>}</div>
      </section>

      <ConfirmDialog
        open={Boolean(action)}
        title={title}
        description={description}
        confirmLabel={action === 'DELETE' ? 'Eliminar' : action === 'INACTIVATE' ? 'Inactivar' : action === 'RESTORE' ? 'Restaurar' : 'Activar'}
        tone={action === 'DELETE' || action === 'INACTIVATE' ? 'danger' : 'primary'}
        busy={busy}
        onCancel={() => setAction(undefined)}
        onConfirm={() => void execute()}
      />
    </main>
  )
}
