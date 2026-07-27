import { BackButton } from '../shared/components/BackButton'
import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { getQuestion, updateQuestion } from '../features/questions/api/questionApi'
import { useAuth } from '../features/authentication/context/AuthContext'
import { QuestionEditor } from '../features/questions/components/QuestionEditor'
import { ApiRequestError } from '../shared/api/apiClient'
import { Icon } from '../shared/components/Icon'
import { LoadingScreen } from '../shared/components/LoadingScreen'
import { useSaveNavigation } from '../shared/hooks/useSaveNavigation'
import type { QuestionDetail, QuestionPayload } from '../shared/types/questions'

export function EditQuestionPage() {
  const { publicId = '' } = useParams()
  const completeSave = useSaveNavigation('/admin/questions')
  const { user } = useAuth()
  const globalAdministrator = Boolean(user?.roles.includes('ADMINISTRATOR'))
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
      <BackButton fallback="/admin/questions" />
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

  async function save(payload: QuestionPayload) {
    await updateQuestion(publicId, {
      ...payload,
      expectedEntityVersion: question!.entityVersion
    })
    completeSave({ title: 'Pregunta actualizada correctamente.' })
  }

  return (
    <main className="content-page editor-page">
      <BackButton fallback="/admin/questions" />
      <div className="page-heading compact resource-heading">
        <div>
          <p className="eyebrow">Banco de preguntas</p>
          <h1>Editar pregunta</h1>
          <p className="muted">
            Los cambios se reflejarán en las colecciones y formularios que la utilicen.
          </p>
        </div>
      </div>
      {globalAdministrator && question.ownership.scope === 'ORGANIZATION' && (
        <section className="inline-warning-panel question-transversal-warning" role="status">
          <Icon name="warning" size={20} />
          <div>
            <strong>Edición transversal de contenido organizacional</strong>
            <p>Estás modificando contenido propiedad de {question.ownership.organizationName ?? 'una organización'}. Los cambios afectarán directamente su Banco de Preguntas y quedarán auditados.</p>
          </div>
        </section>
      )}
      <QuestionEditor
        initial={question}
        onSubmit={save}
        submitLabel="Guardar cambios"
      />
    </main>
  )
}
