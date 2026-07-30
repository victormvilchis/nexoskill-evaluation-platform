import { useAuth } from '../features/authentication/context/AuthContext'
import { createQuestion } from '../features/questions/api/questionApi'
import { QuestionEditorV2 } from '../features/questions/components/QuestionEditorV2'
import { BackButton } from '../shared/components/BackButton'
import { useSaveNavigation } from '../shared/hooks/useSaveNavigation'
import type { QuestionPayload } from '../shared/types/questions'

export function CreateQuestionPage() {
  const completeSave = useSaveNavigation('/admin/questions')
  const { user } = useAuth()
  const globalAdministrator = Boolean(user?.roles.includes('ADMINISTRATOR'))
  async function save(payload: QuestionPayload) {
    await createQuestion(payload)
    completeSave({ title: 'Pregunta creada correctamente.' })
  }
  return (
    <main className="content-page editor-page">
      <BackButton fallback="/admin/questions" />
      <div className="page-heading compact"><div><p className="eyebrow">Banco de preguntas</p><h1>Nueva pregunta</h1><p className="muted">Configura categoría, dificultad, contenido, etiquetas y disponibilidad.</p></div></div>
      <QuestionEditorV2 globalAdministrator={globalAdministrator} onSubmit={save} submitLabel="Crear pregunta" />
    </main>
  )
}
