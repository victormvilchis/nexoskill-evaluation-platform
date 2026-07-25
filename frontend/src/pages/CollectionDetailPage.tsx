import { BackButton } from '../shared/components/BackButton'
import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  changeCollectionStatus,
  getCollection,
  updateCollection
} from '../features/questions/api/questionApi'
import { CollectionEditor } from '../features/questions/components/CollectionEditor'
import { ApiRequestError } from '../shared/api/apiClient'
import { Icon } from '../shared/components/Icon'
import { LoadingScreen } from '../shared/components/LoadingScreen'
import { useToast } from '../shared/components/ToastProvider'
import type { CollectionDetail, CollectionPayload } from '../shared/types/questions'

export function CollectionDetailPage() {
  const { publicId = '' } = useParams()
  const toast = useToast()
  const [collection, setCollection] = useState<CollectionDetail>()
  const [editing, setEditing] = useState(false)
  const [error, setError] = useState<string>()
  const [reloadKey, setReloadKey] = useState(0)
  const [statusBusy, setStatusBusy] = useState(false)

  const reload = useCallback(() => setReloadKey((value) => value + 1), [])

  useEffect(() => {
    const controller = new AbortController()
    setError(undefined)

    getCollection(publicId, controller.signal)
      .then(setCollection)
      .catch((requestError: unknown) => {
        if (controller.signal.aborted) return
        setError(
          requestError instanceof ApiRequestError
            ? requestError.message
            : 'No fue posible consultar la colección.'
        )
      })

    return () => controller.abort()
  }, [publicId, reloadKey])

  if (error && !collection) {
    return (
      <main className="content-page">
      <BackButton fallback="/admin/question-collections" />
        <section className="inline-error-panel" role="alert">
          <div className="inline-error-icon"><Icon name="error" /></div>
          <div>
            <strong>No fue posible cargar la colección</strong>
            <p>{error}</p>
          </div>
          <button className="secondary-button" onClick={reload}>Reintentar</button>
        </section>
      </main>
    )
  }

  if (!collection) return <LoadingScreen />

  async function save(payload: CollectionPayload) {
    const updated = await updateCollection(publicId, {
      ...payload,
      expectedEntityVersion: collection!.entityVersion
    })
    setCollection(updated)
    setEditing(false)
    toast.success('Colección actualizada')
  }

  async function changeStatus() {
    setStatusBusy(true)
    try {
      const updated = await changeCollectionStatus(
        publicId,
        collection!.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE',
        collection!.entityVersion
      )
      setCollection(updated)
      toast.success(
        updated.status === 'ACTIVE' ? 'Colección activada' : 'Colección desactivada'
      )
    } catch (requestError) {
      toast.error(
        'No fue posible cambiar el estado',
        requestError instanceof ApiRequestError ? requestError.message : undefined
      )
    } finally {
      setStatusBusy(false)
    }
  }

  if (editing) {
    return (
      <main className="content-page editor-page collection-editor-page">
      <BackButton fallback="/admin/question-collections" />
        <div className="page-heading compact resource-heading">
          <div>
            <p className="eyebrow">Colecciones</p>
            <h1>Editar colección</h1>
            <p className="muted">Actualiza su contenido y configuración.</p>
          </div>
        </div>
        <CollectionEditor
          initial={collection}
          onSubmit={save}
          label="Guardar cambios"
        />
      </main>
    )
  }

  return (
    <main className="content-page resource-page">
      <BackButton fallback="/admin/question-collections" />
      <div className="page-heading resource-heading">
        <div>
          <p className="eyebrow">Colección</p>
          <h1>{collection.name}</h1>
          <p className="muted">
            {collection.description || 'Sin descripción'}
          </p>
        </div>
        <div className="heading-actions resource-heading-actions">
          <button
            className="secondary-button"
            disabled={statusBusy}
            onClick={() => void changeStatus()}
          >
            {statusBusy
              ? 'Actualizando…'
              : collection.status === 'ACTIVE'
                ? 'Desactivar'
                : 'Activar'}
          </button>
          <button className="primary-button" onClick={() => setEditing(true)}>
            <Icon name="edit" size={16} /> Editar
          </button>
        </div>
      </div>

      <section className="detail-card collection-detail-card">
        <div className="card-title-row">
          <div>
            <h2>Contenido efectivo</h2>
            <p className="muted">
              Incluye categorías dinámicas y preguntas agregadas de forma específica.
            </p>
          </div>
          <span className="resource-total compact">
            <strong>{collection.effectiveQuestions.length}</strong> preguntas
          </span>
        </div>

        <div className="chip-row collection-category-summary">
          {collection.categories.map((category) => (
            <span className="category-chip" key={category.publicId}>
              {category.name}
            </span>
          ))}
        </div>

        {collection.effectiveQuestions.length > 0 ? (
          <div className="compact-question-list collection-effective-list">
            {collection.effectiveQuestions.map((question) => (
              <Link to={`/admin/questions/${question.publicId}`} key={question.publicId}>
                <strong>{question.statement}</strong>
                <small>
                  {question.typeName} · {question.difficultyName}
                </small>
              </Link>
            ))}
          </div>
        ) : (
          <div className="compact-empty-state">
            <p>La colección todavía no contiene preguntas efectivas.</p>
          </div>
        )}
      </section>
    </main>
  )
}
