import { useNavigate } from 'react-router-dom'
import { createCollection } from '../features/questions/api/questionApi'
import { CollectionEditor } from '../features/questions/components/CollectionEditor'
import { useToast } from '../shared/components/ToastProvider'
import type { CollectionPayload } from '../shared/types/questions'
import { BackButton } from '../shared/components/BackButton'

export function CreateCollectionPage() {
  const navigate = useNavigate()
  const toast = useToast()

  async function save(payload: CollectionPayload) {
    const collection = await createCollection(payload)
    toast.success('Colección creada', 'El contenido quedó disponible para formularios.')
    navigate(`/admin/question-collections/${collection.publicId}`)
  }

  return (
    <main className="content-page editor-page collection-editor-page">
      <BackButton fallback="/admin/question-collections" />
      <div className="page-heading compact resource-heading">
        <div>
          <p className="eyebrow">Colecciones</p>
          <h1>Nueva colección</h1>
          <p className="muted">
            Combina categorías dinámicas y preguntas específicas en un solo recurso.
          </p>
        </div>
      </div>
      <CollectionEditor onSubmit={save} label="Crear colección" />
    </main>
  )
}
