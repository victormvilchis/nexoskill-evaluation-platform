import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiRequestError } from '../shared/api/apiClient'
import { BackButton } from '../shared/components/BackButton'
import { useToast } from '../shared/components/ToastProvider'
import { CollectionBuilder } from '../features/collections/components/CollectionBuilder'
import { createLearningCollection } from '../features/collections/api/collectionApi'
import type { CollectionPayload } from '../shared/types/collections'

export function CreateCollectionPage() {
  const navigate = useNavigate()
  const toast = useToast()
  const [saving, setSaving] = useState(false)

  async function save(payload: CollectionPayload) {
    setSaving(true)
    try {
      const collection = await createLearningCollection(payload)
      toast.success(
        'Colección creada',
        'Los formularios quedaron organizados por nivel.'
      )
      navigate(`/admin/collections/${collection.publicId}`, { replace: true })
    } catch (error) {
      toast.error(
        'No fue posible crear la colección',
        error instanceof ApiRequestError
          ? error.message
          : 'Revisa la información e intenta nuevamente.'
      )
    } finally {
      setSaving(false)
    }
  }

  return (
    <main className="content-page lc-page lc-editor-page">
      <BackButton fallback="/admin/collections" label="Volver a colecciones" />
      <div className="lc-page-header lc-page-header-compact">
        <div>
          <p className="eyebrow">Colecciones</p>
          <h1>Nueva colección</h1>
          <p>Configura una ruta progresiva utilizando formularios existentes.</p>
        </div>
      </div>
      <CollectionBuilder
        saving={saving}
        submitLabel="Crear colección"
        onCancel={() => navigate('/admin/collections')}
        onSubmit={save}
      />
    </main>
  )
}
