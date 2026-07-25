import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ApiRequestError } from '../shared/api/apiClient'
import { BackButton } from '../shared/components/BackButton'
import { Icon } from '../shared/components/Icon'
import { useToast } from '../shared/components/ToastProvider'
import {
  changeLearningCollectionStatus,
  getLearningCollection,
  updateLearningCollection
} from '../features/collections/api/collectionApi'
import { CollectionBuilder } from '../features/collections/components/CollectionBuilder'
import type {
  CollectionDetail,
  CollectionPayload,
  CollectionStatus
} from '../shared/types/collections'

function statusLabel(status: CollectionStatus) {
  const labels: Record<CollectionStatus, string> = {
    DRAFT: 'Borrador',
    ACTIVE: 'Activa',
    INACTIVE: 'Inactiva',
    ARCHIVED: 'Archivada'
  }
  return labels[status]
}

export function CollectionDetailPage() {
  const { publicId } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const [collection, setCollection] = useState<CollectionDetail>()
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [changingStatus, setChangingStatus] = useState(false)
  const [error, setError] = useState<string>()

  useEffect(() => {
    if (!publicId) return
    setLoading(true)
    setError(undefined)
    getLearningCollection(publicId)
      .then(setCollection)
      .catch((requestError: unknown) => {
        setError(
          requestError instanceof ApiRequestError
            ? requestError.message
            : 'No fue posible consultar la colección.'
        )
      })
      .finally(() => setLoading(false))
  }, [publicId])

  async function save(payload: CollectionPayload) {
    if (!publicId) return
    setSaving(true)
    try {
      const updated = await updateLearningCollection(publicId, payload)
      setCollection(updated)
      toast.success('Colección actualizada', 'El orden de los niveles fue guardado.')
    } catch (requestError) {
      toast.error(
        'No fue posible guardar la colección',
        requestError instanceof ApiRequestError
          ? requestError.message
          : 'Revisa la información e intenta nuevamente.'
      )
    } finally {
      setSaving(false)
    }
  }

  async function changeStatus(status: CollectionStatus) {
    if (!publicId) return
    setChangingStatus(true)
    try {
      const updated = await changeLearningCollectionStatus(publicId, status)
      setCollection(updated)
      toast.success('Estado actualizado', `La colección ahora está ${statusLabel(status).toLowerCase()}.`)
    } catch (requestError) {
      toast.error(
        'No fue posible cambiar el estado',
        requestError instanceof ApiRequestError
          ? requestError.message
          : 'Intenta nuevamente.'
      )
    } finally {
      setChangingStatus(false)
    }
  }

  if (loading) {
    return (
      <main className="content-page lc-page">
        <BackButton fallback="/admin/collections" label="Volver a colecciones" />
        <div className="lc-loading-page">Cargando colección…</div>
      </main>
    )
  }

  if (!collection || error) {
    return (
      <main className="content-page lc-page">
        <BackButton fallback="/admin/collections" label="Volver a colecciones" />
        <section className="lc-error-panel" role="alert">
          <div><Icon name="error" size={20} /></div>
          <div>
            <strong>No fue posible abrir la colección</strong>
            <p>{error || 'La colección ya no está disponible.'}</p>
          </div>
        </section>
      </main>
    )
  }

  return (
    <main className="content-page lc-page lc-editor-page">
      <BackButton fallback="/admin/collections" label="Volver a colecciones" />
      <div className="lc-page-header lc-page-header-compact">
        <div>
          <div className="lc-heading-status-row">
            <p className="eyebrow">Colecciones</p>
            <span className={`lc-status lc-status-${collection.status.toLowerCase()}`}>
              {statusLabel(collection.status)}
            </span>
          </div>
          <h1>{collection.name}</h1>
          <p>Administra los formularios y el orden de desbloqueo de los niveles.</p>
        </div>
        <div className="lc-status-actions">
          {collection.status !== 'ACTIVE' && collection.status !== 'ARCHIVED' && (
            <button
              className="primary-button"
              disabled={changingStatus}
              type="button"
              onClick={() => void changeStatus('ACTIVE')}
            >
              Activar
            </button>
          )}
          {collection.status === 'ACTIVE' && (
            <button
              className="secondary-button"
              disabled={changingStatus}
              type="button"
              onClick={() => void changeStatus('INACTIVE')}
            >
              Desactivar
            </button>
          )}
          {collection.status === 'INACTIVE' && (
            <button
              className="secondary-button"
              disabled={changingStatus}
              type="button"
              onClick={() => void changeStatus('DRAFT')}
            >
              Volver a borrador
            </button>
          )}
          {collection.status !== 'ARCHIVED' && (
            <button
              className="danger-ghost-button"
              disabled={changingStatus}
              type="button"
              onClick={() => void changeStatus('ARCHIVED')}
            >
              Archivar
            </button>
          )}
        </div>
      </div>

      {collection.status === 'ARCHIVED' ? (
        <section className="lc-archived-panel">
          <Icon name="archive" size={24} />
          <div>
            <strong>Esta colección está archivada</strong>
            <p>Se conserva para consulta, pero ya no puede modificarse ni asignarse.</p>
          </div>
        </section>
      ) : (
        <CollectionBuilder
          initial={collection}
          saving={saving}
          submitLabel="Guardar cambios"
          onCancel={() => navigate('/admin/collections')}
          onSubmit={save}
        />
      )}
    </main>
  )
}
