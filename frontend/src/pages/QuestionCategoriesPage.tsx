import { useCallback, useEffect, useState, type FormEvent } from 'react'
import {
  changeQuestionCategoryStatus,
  createQuestionCategory,
  getQuestionCategories,
  updateQuestionCategory
} from '../features/questions/api/questionApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { Icon } from '../shared/components/Icon'
import { useToast } from '../shared/components/ToastProvider'
import type { QuestionCategory } from '../shared/types/questions'

export function QuestionCategoriesPage() {
  const toast = useToast()
  const [categories, setCategories] = useState<QuestionCategory[]>([])
  const [editing, setEditing] = useState<QuestionCategory>()
  const [name, setName] = useState('')
  const [code, setCode] = useState('')
  const [description, setDescription] = useState('')
  const [busy, setBusy] = useState(false)
  const [statusBusyId, setStatusBusyId] = useState<string>()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string>()
  const [reloadKey, setReloadKey] = useState(0)

  const reload = useCallback(() => setReloadKey((value) => value + 1), [])

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(undefined)

    getQuestionCategories(controller.signal)
      .then(setCategories)
      .catch((requestError: unknown) => {
        if (controller.signal.aborted) return
        setError(
          requestError instanceof ApiRequestError
            ? requestError.message
            : 'No fue posible consultar las categorías.'
        )
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })

    return () => controller.abort()
  }, [reloadKey])

  function select(category?: QuestionCategory) {
    setEditing(category)
    setName(category?.name ?? '')
    setCode(category?.code ?? '')
    setDescription(category?.description ?? '')
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)

    try {
      const saved = editing
        ? await updateQuestionCategory(editing.publicId, {
            name,
            code,
            description,
            expectedEntityVersion: editing.entityVersion
          })
        : await createQuestionCategory({
            name,
            code: code || undefined,
            description
          })

      setCategories((current) =>
        [...current.filter((category) => category.publicId !== saved.publicId), saved]
          .sort((left, right) => left.name.localeCompare(right.name, 'es-MX'))
      )
      select()
      toast.success(editing ? 'Categoría actualizada' : 'Categoría creada')
    } catch (requestError) {
      toast.error(
        'No fue posible guardar la categoría',
        requestError instanceof ApiRequestError ? requestError.message : undefined
      )
    } finally {
      setBusy(false)
    }
  }

  async function changeStatus(category: QuestionCategory) {
    setStatusBusyId(category.publicId)
    try {
      const saved = await changeQuestionCategoryStatus(
        category.publicId,
        category.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE',
        category.entityVersion
      )
      setCategories((current) =>
        current.map((item) => (item.publicId === saved.publicId ? saved : item))
      )
      toast.success(
        saved.status === 'ACTIVE' ? 'Categoría activada' : 'Categoría desactivada'
      )
    } catch (requestError) {
      toast.error(
        'No fue posible cambiar el estado',
        requestError instanceof ApiRequestError ? requestError.message : undefined
      )
    } finally {
      setStatusBusyId(undefined)
    }
  }

  return (
    <main className="content-page resource-page">
      <div className="page-heading resource-heading">
        <div>
          <p className="eyebrow">Contenido</p>
          <h1>Categorías</h1>
          <p className="muted">Clasifica preguntas en uno o varios temas.</p>
        </div>
      </div>

      {error && (
        <section className="inline-error-panel" role="alert">
          <div className="inline-error-icon"><Icon name="error" /></div>
          <div>
            <strong>No fue posible cargar las categorías</strong>
            <p>{error}</p>
          </div>
          <button className="secondary-button compact-button" onClick={reload}>
            Reintentar
          </button>
        </section>
      )}

      <div className="catalog-management-grid category-management-layout">
        <section className="detail-card category-list-card">
          <div className="card-title-row">
            <div>
              <h2>Categorías</h2>
              <p className="muted">Administra los temas disponibles en el banco.</p>
            </div>
            <span className="resource-total compact">
              <strong>{categories.length}</strong>
            </span>
          </div>

          {loading ? (
            <div className="inline-loading-state">Cargando categorías…</div>
          ) : categories.length > 0 ? (
            <div className="category-list">
              {categories.map((category) => (
                <article key={category.publicId}>
                  <div className="category-main">
                    <span className="category-icon"><Icon name="categories" /></span>
                    <div>
                      <strong>{category.name}</strong>
                      <small>
                        {category.questionCount} preguntas · {category.code}
                      </small>
                    </div>
                  </div>
                  <div className="category-actions">
                    <span
                      className={`status-badge status-${category.status.toLowerCase()}`}
                    >
                      {category.status === 'ACTIVE' ? 'Activa' : 'Inactiva'}
                    </span>
                    <button
                      aria-label={`Editar ${category.name}`}
                      className="icon-button"
                      type="button"
                      onClick={() => select(category)}
                    >
                      <Icon name="edit" />
                    </button>
                    <button
                      className="secondary-button compact-button"
                      disabled={statusBusyId === category.publicId}
                      type="button"
                      onClick={() => void changeStatus(category)}
                    >
                      {statusBusyId === category.publicId
                        ? 'Actualizando…'
                        : category.status === 'ACTIVE'
                          ? 'Desactivar'
                          : 'Activar'}
                    </button>
                  </div>
                </article>
              ))}
            </div>
          ) : (
            <div className="compact-empty-state">
              <p>Aún no hay categorías.</p>
            </div>
          )}
        </section>

        <section className="detail-card category-form-card">
          <p className="eyebrow">{editing ? 'Editar' : 'Nueva'} categoría</p>
          <h2>{editing ? 'Actualizar categoría' : 'Agregar categoría'}</h2>
          <form className="compact-form" onSubmit={(event) => void submit(event)}>
            <div className="form-field">
              <label htmlFor="category-name">Nombre</label>
              <input
                id="category-name"
                required
                value={name}
                onChange={(event) => setName(event.target.value)}
              />
            </div>
            <div className="form-field">
              <label htmlFor="category-code">Código</label>
              <input
                id="category-code"
                value={code}
                onChange={(event) => setCode(event.target.value)}
                placeholder="Se genera automáticamente"
              />
            </div>
            <div className="form-field">
              <label htmlFor="category-description">Descripción</label>
              <textarea
                id="category-description"
                rows={3}
                value={description}
                onChange={(event) => setDescription(event.target.value)}
              />
            </div>
            <div className="form-actions">
              <button className="primary-button" disabled={busy}>
                {busy ? 'Guardando…' : editing ? 'Guardar cambios' : 'Crear categoría'}
              </button>
              {editing && (
                <button
                  className="secondary-button"
                  type="button"
                  onClick={() => select()}
                >
                  Cancelar
                </button>
              )}
            </div>
          </form>
        </section>
      </div>
    </main>
  )
}
