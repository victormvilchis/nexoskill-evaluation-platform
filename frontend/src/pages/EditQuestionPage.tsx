import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { getQuestion, updateQuestion } from '../features/questions/api/questionApi'
import { QuestionEditor } from '../features/questions/components/QuestionEditor'
import { ApiRequestError } from '../shared/api/apiClient'
import { Icon } from '../shared/components/Icon'
import { LoadingScreen } from '../shared/components/LoadingScreen'
import { useToast } from '../shared/components/ToastProvider'
import type { QuestionDetail, QuestionPayload } from '../shared/types/questions'

export function EditQuestionPage() {
  const { publicId = '' } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
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
    const updated = await updateQuestion(publicId, {
      ...payload,
      expectedEntityVersion: question!.entityVersion
    })
    toast.success('Pregunta actualizada')
    navigate(`/admin/questions/${updated.publicId}`)
  }

  return (
    <main className="content-page editor-page">
      <div className="page-heading compact resource-heading">
        <div>
          <p className="eyebrow">Banco de preguntas</p>
          <h1>Editar pregunta</h1>
          <p className="muted">
            Los cambios se reflejarán en las colecciones y formularios que la utilicen.
          </p>
        </div>
      </div>
      <QuestionEditor
        initial={question}
        onSubmit={save}
        submitLabel="Guardar cambios"
      />
    </main>
  )
}
