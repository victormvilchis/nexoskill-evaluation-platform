import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import {
  changeQuestionCategoryStatus,
  createQuestionCategory,
  getQuestionCategories
} from '../features/questions/api/questionApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { ConfirmDialog } from '../shared/components/ConfirmDialog'
import { Icon } from '../shared/components/Icon'
import { useToast } from '../shared/components/ToastProvider'
import type { QuestionCategory } from '../shared/types/questions'

export function QuestionCategoriesPage() {
  const toast = useToast()
  const [categories, setCategories] = useState<QuestionCategory[]>([])
  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [pendingCategory, setPendingCategory] = useState<QuestionCategory | null>(null)

  useEffect(() => {
    getQuestionCategories()
      .then(setCategories)
      .catch(() => toast.error('No fue posible consultar las categorías'))
  }, [toast])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    try {
      const created = await createQuestionCategory({
        code: code.trim() || undefined,
        name: name.trim(),
        description: description.trim() || undefined
      })
      setCategories((current) => [...current, created].sort((left, right) =>
        left.name.localeCompare(right.name, 'es-MX')
      ))
      setCode('')
      setName('')
      setDescription('')
      toast.success('Categoría creada', `${created.name} ya está disponible.`)
    } catch (requestError) {
      toast.error(
        'No fue posible crear la categoría',
        requestError instanceof ApiRequestError ? requestError.message : undefined
      )
    } finally {
      setSubmitting(false)
    }
  }

  async function confirmStatusChange() {
    if (!pendingCategory) return
    const targetStatus = pendingCategory.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
    setSubmitting(true)
    try {
      const updated = await changeQuestionCategoryStatus(pendingCategory.publicId, targetStatus)
      setCategories((current) => current.map((item) =>
        item.publicId === updated.publicId ? updated : item
      ))
      toast.success(
        targetStatus === 'ACTIVE' ? 'Categoría activada' : 'Categoría desactivada',
        targetStatus === 'ACTIVE'
          ? 'Ya puede utilizarse en nuevas preguntas.'
          : 'Las preguntas existentes conservarán esta categoría.'
      )
      setPendingCategory(null)
    } catch (requestError) {
      toast.error(
        'No fue posible actualizar la categoría',
        requestError instanceof ApiRequestError ? requestError.message : undefined
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="content-page narrow-content">
      <div className="page-heading">
        <div>
          <p className="eyebrow">Banco de preguntas</p>
          <h1>Categorías</h1>
          <p className="muted">Organiza las preguntas por materia o dominio de conocimiento.</p>
        </div>
        <Link className="secondary-button button-link" to="/admin/questions">Volver</Link>
      </div>

      <div className="catalog-management-grid">
        <section className="detail-card">
          <div className="card-title-row">
            <div><p className="eyebrow">Catálogo</p><h2>Categorías disponibles</h2></div>
            <span className="count-badge">{categories.length}</span>
          </div>
          <div className="category-list">
            {categories.length === 0 && <p className="empty-state">Aún no hay categorías registradas.</p>}
            {categories.map((category) => (
              <article key={category.publicId}>
                <div className="category-main">
                  <span className="category-icon"><Icon name="categories" size={17} /></span>
                  <div>
                    <strong>{category.name}</strong>
                    <small>{category.description || 'Sin descripción'}</small>
                  </div>
                </div>
                <div className="category-actions">
                  <span className={`status-badge status-${category.status.toLowerCase()}`}>
                    {category.status === 'ACTIVE' ? 'Activa' : 'Inactiva'}
                  </span>
                  <button
                    className={category.status === 'ACTIVE' ? 'secondary-button compact-button' : 'primary-button compact-button'}
                    type="button"
                    onClick={() => setPendingCategory(category)}
                  >
                    {category.status === 'ACTIVE' ? 'Desactivar' : 'Activar'}
                  </button>
                </div>
              </article>
            ))}
          </div>
        </section>

        <section className="detail-card category-form-card">
          <p className="eyebrow">Nueva categoría</p>
          <h2>Agregar categoría</h2>
          <form className="compact-form" onSubmit={(event) => void handleSubmit(event)}>
            <div className="form-field">
              <label htmlFor="categoryName">Nombre</label>
              <input id="categoryName" value={name} maxLength={150} required onChange={(event) => setName(event.target.value)} />
            </div>
            <div className="form-field">
              <label htmlFor="categoryCode">Código <span className="optional-label">opcional</span></label>
              <input id="categoryCode" value={code} maxLength={80} placeholder="Se genera automáticamente" onChange={(event) => setCode(event.target.value)} />
            </div>
            <div className="form-field">
              <label htmlFor="categoryDescription">Descripción <span className="optional-label">opcional</span></label>
              <textarea id="categoryDescription" value={description} rows={3} maxLength={500} onChange={(event) => setDescription(event.target.value)} />
            </div>
            <button className="primary-button" type="submit" disabled={submitting}>
              <Icon name="plus" size={16} />{submitting ? 'Creando…' : 'Crear categoría'}
            </button>
          </form>
        </section>
      </div>

      <ConfirmDialog
        open={pendingCategory !== null}
        title={pendingCategory?.status === 'ACTIVE' ? 'Desactivar categoría' : 'Activar categoría'}
        description={pendingCategory?.status === 'ACTIVE'
          ? 'Dejará de estar disponible para nuevas preguntas. El contenido existente no se modificará.'
          : 'La categoría volverá a estar disponible para nuevas preguntas.'}
        confirmLabel={pendingCategory?.status === 'ACTIVE' ? 'Desactivar' : 'Activar'}
        tone={pendingCategory?.status === 'ACTIVE' ? 'danger' : 'primary'}
        busy={submitting}
        onCancel={() => setPendingCategory(null)}
        onConfirm={() => void confirmStatusChange()}
      />
    </main>
  )
}
