import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import {
  changeQuestionCategoryStatus,
  createQuestionCategory,
  getQuestionCategories
} from '../features/questions/api/questionApi'
import { ApiRequestError } from '../shared/api/apiClient'
import type { QuestionCategory } from '../shared/types/questions'

export function QuestionCategoriesPage() {
  const [categories, setCategories] = useState<QuestionCategory[]>([])
  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)

  useEffect(() => {
    getQuestionCategories()
      .then(setCategories)
      .catch(() => setError('No fue posible consultar las categorías.'))
  }, [])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    setSuccess(null)

    try {
      const created = await createQuestionCategory({
        code: code.trim() || undefined,
        name: name.trim(),
        description: description.trim() || undefined
      })
      setCategories((current) =>
        [...current, created].sort((left, right) =>
          left.name.localeCompare(right.name, 'es-MX')
        )
      )
      setCode('')
      setName('')
      setDescription('')
      setSuccess('La categoría se creó correctamente.')
    } catch (requestError) {
      setError(
        requestError instanceof ApiRequestError
          ? requestError.message
          : 'No fue posible crear la categoría.'
      )
    } finally {
      setSubmitting(false)
    }
  }

  async function handleStatusChange(category: QuestionCategory) {
    const targetStatus = category.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
    const actionLabel = targetStatus === 'ACTIVE' ? 'activar' : 'desactivar'
    if (!window.confirm(`¿Deseas ${actionLabel} la categoría ${category.name}?`)) {
      return
    }
    setError(null)
    setSuccess(null)
    try {
      const updated = await changeQuestionCategoryStatus(
        category.publicId,
        targetStatus
      )
      setCategories((current) =>
        current.map((item) =>
          item.publicId === updated.publicId ? updated : item
        )
      )
      setSuccess(
        targetStatus === 'ACTIVE'
          ? 'La categoría quedó activa.'
          : 'La categoría quedó inactiva y ya no podrá asignarse a nuevas preguntas.'
      )
    } catch (requestError) {
      setError(
        requestError instanceof ApiRequestError
          ? requestError.message
          : 'No fue posible actualizar la categoría.'
      )
    }
  }

  return (
    <main className="content-page narrow-content">
      <div className="page-heading">
        <div>
          <p className="eyebrow">Banco de preguntas</p>
          <h1>Categorías</h1>
          <p className="muted">
            Organiza las preguntas por tecnología, materia o dominio de
            conocimiento.
          </p>
        </div>
        <Link className="secondary-button button-link" to="/admin/questions">
          Volver
        </Link>
      </div>

      {success && <div className="success-message">{success}</div>}
      {error && <div className="error-message dashboard-error">{error}</div>}

      <div className="catalog-management-grid">
        <section className="detail-card">
          <p className="eyebrow">Administración de catálogo</p>
          <h2>Categorías disponibles</h2>
          <div className="category-list">
            {categories.map((category) => (
              <article key={category.publicId}>
                <div>
                  <strong>{category.name}</strong>
                  <small>{category.code}</small>
                  <span className={`status-badge status-${category.status.toLowerCase()}`}>
                    {category.status === 'ACTIVE' ? 'Activa' : 'Inactiva'}
                  </span>
                </div>
                <p>{category.description || 'Sin descripción.'}</p>
                <button
                  className={category.status === 'ACTIVE' ? 'danger-button compact-button' : 'secondary-button compact-button'}
                  type="button"
                  onClick={() => void handleStatusChange(category)}
                >
                  {category.status === 'ACTIVE' ? 'Desactivar' : 'Activar'}
                </button>
              </article>
            ))}
          </div>
        </section>

        <section className="detail-card">
          <p className="eyebrow">Nueva categoría</p>
          <h2>Agregar categoría</h2>
          <form
            className="compact-form"
            onSubmit={(event) => void handleSubmit(event)}
          >
            <div className="form-field">
              <label htmlFor="categoryName">Nombre</label>
              <input
                id="categoryName"
                value={name}
                maxLength={150}
                required
                onChange={(event) => setName(event.target.value)}
              />
            </div>

            <div className="form-field">
              <label htmlFor="categoryCode">Código</label>
              <input
                id="categoryCode"
                value={code}
                maxLength={80}
                placeholder="Se genera a partir del nombre"
                onChange={(event) => setCode(event.target.value)}
              />
            </div>

            <div className="form-field">
              <label htmlFor="categoryDescription">Descripción</label>
              <textarea
                id="categoryDescription"
                value={description}
                rows={4}
                maxLength={500}
                onChange={(event) => setDescription(event.target.value)}
              />
            </div>

            <button className="primary-button" type="submit" disabled={submitting}>
              {submitting ? 'Creando…' : 'Crear categoría'}
            </button>
          </form>
        </section>
      </div>
    </main>
  )
}
