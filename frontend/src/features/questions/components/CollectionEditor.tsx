import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { getQuestionCategories, searchQuestions } from '../api/questionApi'
import { ApiRequestError } from '../../../shared/api/apiClient'
import { Icon } from '../../../shared/components/Icon'
import type {
  CollectionDetail,
  CollectionPayload,
  QuestionCategory,
  QuestionSummary
} from '../../../shared/types/questions'

interface CollectionEditorProps {
  initial?: CollectionDetail
  onSubmit: (payload: CollectionPayload) => Promise<void>
  label: string
}

export function CollectionEditor({ initial, onSubmit, label }: CollectionEditorProps) {
  const [name, setName] = useState(initial?.name ?? '')
  const [description, setDescription] = useState(initial?.description ?? '')
  const [categories, setCategories] = useState<QuestionCategory[]>([])
  const [questions, setQuestions] = useState<QuestionSummary[]>([])
  const [selectedCategories, setSelectedCategories] = useState<string[]>(
    initial?.categories.map((category) => category.publicId) ?? []
  )
  const [selectedQuestions, setSelectedQuestions] = useState<string[]>(
    initial?.explicitQuestions.map((question) => question.publicId) ?? []
  )
  const [query, setQuery] = useState('')
  const [loadingResources, setLoadingResources] = useState(true)
  const [loadError, setLoadError] = useState<string>()
  const [submitError, setSubmitError] = useState<string>()
  const [busy, setBusy] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)

  const reloadResources = useCallback(
    () => setReloadKey((value) => value + 1),
    []
  )

  useEffect(() => {
    const controller = new AbortController()
    setLoadingResources(true)
    setLoadError(undefined)

    Promise.all([
      getQuestionCategories(controller.signal),
      searchQuestions({ status: 'ACTIVE', size: 100, signal: controller.signal })
    ])
      .then(([categoryItems, questionPage]) => {
        setCategories(categoryItems)
        setQuestions(questionPage.content)
      })
      .catch((requestError: unknown) => {
        if (controller.signal.aborted) return
        setLoadError(
          requestError instanceof ApiRequestError
            ? requestError.message
            : 'No fue posible cargar las categorías y preguntas.'
        )
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoadingResources(false)
      })

    return () => controller.abort()
  }, [reloadKey])

  const filteredQuestions = useMemo(() => {
    const normalizedQuery = query.trim().toLocaleLowerCase('es-MX')
    if (!normalizedQuery) return questions

    return questions.filter((question) => {
      const categoryNames = question.categories.map((category) => category.name).join(' ')
      return `${question.statement} ${categoryNames}`
        .toLocaleLowerCase('es-MX')
        .includes(normalizedQuery)
    })
  }, [query, questions])

  function toggleCategory(publicId: string, checked: boolean) {
    setSelectedCategories((current) =>
      checked
        ? current.includes(publicId)
          ? current
          : [...current, publicId]
        : current.filter((id) => id !== publicId)
    )
  }

  function toggleQuestion(publicId: string, checked: boolean) {
    setSelectedQuestions((current) =>
      checked
        ? current.includes(publicId)
          ? current
          : [...current, publicId]
        : current.filter((id) => id !== publicId)
    )
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSubmitError(undefined)
    setBusy(true)

    try {
      await onSubmit({
        name: name.trim(),
        description: description.trim() || undefined,
        categoryPublicIds: selectedCategories,
        questionPublicIds: selectedQuestions
      })
    } catch (requestError) {
      setSubmitError(
        requestError instanceof ApiRequestError
          ? requestError.message
          : 'No fue posible guardar la colección.'
      )
    } finally {
      setBusy(false)
    }
  }

  const hasContent = selectedCategories.length > 0 || selectedQuestions.length > 0

  return (
    <form className="collection-editor" onSubmit={(event) => void submit(event)}>
      <section className="editor-card collection-main-card">
        <div className="editor-card-heading">
          <div>
            <span className="editor-step">1</span>
            <div>
              <h2>Información general</h2>
              <p>Identifica la colección para reutilizarla en formularios.</p>
            </div>
          </div>
        </div>
        <div className="collection-main-grid">
          <div className="form-field">
            <label htmlFor="collection-name">Nombre</label>
            <input
              id="collection-name"
              required
              maxLength={180}
              value={name}
              onChange={(event) => setName(event.target.value)}
              placeholder="Ej. Certificación APX"
            />
          </div>
          <div className="form-field collection-description-field">
            <label htmlFor="collection-description">Descripción</label>
            <textarea
              id="collection-description"
              rows={3}
              maxLength={1_000}
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              placeholder="Describe brevemente el propósito de la colección"
            />
          </div>
        </div>
      </section>

      {loadError && (
        <section className="inline-error-panel" role="alert">
          <div className="inline-error-icon">
            <Icon name="error" size={20} />
          </div>
          <div>
            <strong>No fue posible cargar el contenido disponible</strong>
            <p>{loadError}</p>
          </div>
          <button
            className="secondary-button compact-button"
            type="button"
            onClick={reloadResources}
          >
            Reintentar
          </button>
        </section>
      )}

      <section className="editor-card">
        <div className="editor-card-heading">
          <div>
            <span className="editor-step">2</span>
            <div>
              <h2>Categorías dinámicas</h2>
              <p>
                Las preguntas activas de estas categorías se incorporan automáticamente.
              </p>
            </div>
          </div>
          <span className="selection-counter">
            {selectedCategories.length} seleccionadas
          </span>
        </div>

        {loadingResources ? (
          <div className="inline-loading-state">Cargando categorías…</div>
        ) : categories.length > 0 ? (
          <div className="category-selector collection-category-selector">
            {categories.map((category) => {
              const selected = selectedCategories.includes(category.publicId)
              const disabled = category.status !== 'ACTIVE' && !selected
              return (
                <label
                  className={`category-choice ${selected ? 'selected' : ''} ${disabled ? 'disabled' : ''}`}
                  key={category.publicId}
                >
                  <input
                    type="checkbox"
                    disabled={disabled}
                    checked={selected}
                    onChange={(event) =>
                      toggleCategory(category.publicId, event.target.checked)
                    }
                  />
                  <span>{category.name}</span>
                  {category.status !== 'ACTIVE' && <small>Inactiva</small>}
                </label>
              )
            })}
          </div>
        ) : (
          <div className="compact-empty-state">
            <p>No hay categorías disponibles.</p>
            <Link to="/admin/question-categories">Administrar categorías</Link>
          </div>
        )}
      </section>

      <section className="editor-card">
        <div className="editor-card-heading collection-question-heading">
          <div>
            <span className="editor-step">3</span>
            <div>
              <h2>Preguntas específicas</h2>
              <p>Agrega preguntas que no dependan de una categoría completa.</p>
            </div>
          </div>
          <span className="selection-counter">
            {selectedQuestions.length} seleccionadas
          </span>
        </div>

        <label className="resource-search collection-question-search">
          <Icon name="search" size={18} />
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Filtrar por enunciado o categoría"
          />
          {query && (
            <button
              aria-label="Limpiar búsqueda"
              className="resource-search-clear"
              type="button"
              onClick={() => setQuery('')}
            >
              <Icon name="close" size={15} />
            </button>
          )}
        </label>

        {loadingResources ? (
          <div className="inline-loading-state">Cargando preguntas…</div>
        ) : filteredQuestions.length > 0 ? (
          <div className="question-pick-list collection-question-list">
            {filteredQuestions.map((question) => (
              <label
                className={
                  selectedQuestions.includes(question.publicId) ? 'selected' : ''
                }
                key={question.publicId}
              >
                <input
                  type="checkbox"
                  checked={selectedQuestions.includes(question.publicId)}
                  onChange={(event) =>
                    toggleQuestion(question.publicId, event.target.checked)
                  }
                />
                <span>
                  <strong>{question.statement}</strong>
                  <small>
                    {question.typeName}
                    {question.categories.length > 0
                      ? ` · ${question.categories.map((category) => category.name).join(' · ')}`
                      : ''}
                  </small>
                </span>
              </label>
            ))}
          </div>
        ) : (
          <div className="compact-empty-state">
            <p>
              {query
                ? 'No hay preguntas que coincidan con el filtro.'
                : 'No hay preguntas activas disponibles.'}
            </p>
            {!query && <Link to="/admin/questions/new">Crear una pregunta</Link>}
          </div>
        )}
      </section>

      {!hasContent && !loadingResources && (
        <p className="collection-content-hint">
          Selecciona al menos una categoría o una pregunta específica.
        </p>
      )}

      {submitError && (
        <section className="inline-error-panel compact" role="alert">
          <div className="inline-error-icon">
            <Icon name="error" size={18} />
          </div>
          <div>
            <strong>No fue posible guardar la colección</strong>
            <p>{submitError}</p>
          </div>
        </section>
      )}

      <div className="sticky-form-actions collection-form-actions">
        <div className="collection-form-summary">
          <strong>{selectedCategories.length + selectedQuestions.length}</strong>
          <span>elementos seleccionados</span>
        </div>
        <div>
          <Link
            className="secondary-button button-link"
            to="/admin/question-collections"
          >
            Cancelar
          </Link>
          <button
            className="primary-button"
            disabled={busy || loadingResources || !hasContent || !name.trim()}
          >
            {busy ? 'Guardando…' : label}
          </button>
        </div>
      </div>
    </form>
  )
}
