import { createQuestion } from '../features/questions/api/questionApi'
import { QuestionEditor } from '../features/questions/components/QuestionEditor'
import { BackButton } from '../shared/components/BackButton'
import { useSaveNavigation } from '../shared/hooks/useSaveNavigation'
import type { QuestionPayload } from '../shared/types/questions'

export function CreateQuestionPage() {
  const completeSave = useSaveNavigation('/admin/questions')

  async function save(payload: QuestionPayload) {
    await createQuestion(payload)
    completeSave({ title: 'Pregunta creada correctamente.' })
  }

  return (
    <main className="content-page editor-page">
      <BackButton fallback="/admin/questions" />
      <div className="page-heading compact">
        <div>
          <p className="eyebrow">Banco de preguntas</p>
          <h1>Nueva pregunta</h1>
          <p className="muted">Configura contenido Java, categorías y tipo de respuesta.</p>
        </div>
      </div>
      <QuestionEditor onSubmit={save} submitLabel="Crear pregunta" />
    </main>
  )
}
