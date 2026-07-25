import { useNavigate } from 'react-router-dom'
import { createQuestion } from '../features/questions/api/questionApi'
import { QuestionEditor } from '../features/questions/components/QuestionEditor'
import { BackButton } from '../shared/components/BackButton'
import { useToast } from '../shared/components/ToastProvider'
import type { QuestionPayload } from '../shared/types/questions'

export function CreateQuestionPage() {
  const navigate = useNavigate()
  const toast = useToast()

  async function save(payload: QuestionPayload) {
    const question = await createQuestion(payload)
    toast.success('Pregunta creada')
    navigate(`/admin/questions/${question.publicId}`)
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
