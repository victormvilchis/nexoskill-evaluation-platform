import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import {
  changeQuestionCategoryStatus,
  createQuestionCategory,
  getQuestionCategories,
  updateQuestionCategory
} from '../features/questions/api/questionApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { FilterToolbar } from '../shared/components/FilterToolbar'
import { Icon } from '../shared/components/Icon'
import {
  ResourceSearchField,
  ResourceSelectField
} from '../shared/components/ResourceFilters'
import {
  TableActionButton,
  TableActions
} from '../shared/components/TableActions'
import { useToast } from '../shared/components/ToastProvider'
import { useDebouncedValue } from '../shared/hooks/useDebouncedValue'
import type { CatalogStatus, QuestionCategory } from '../shared/types/questions'

function formatDate(value?: string) {
  if (!value) return 'Sin cambios'
  return new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium' }).format(new Date(value))
}

export function QuestionCategoriesPage() {
  const toast = useToast()
  const [categories, setCategories] = useState<QuestionCategory[]>([])
  const [editing, setEditing] = useState<QuestionCategory>()
  const [editorOpen, setEditorOpen] = useState(false)
  const [name, setName] = useState('')
  const [code, setCode] = useState('')
  const [description, setDescription] = useState('')
  const [query, setQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState<CatalogStatus | ''>('')
  const [busy, setBusy] = useState(false)
  const [statusBusyId, setStatusBusyId] = useState<string>()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string>()
  const [reloadKey, setReloadKey] = useState(0)
  const debouncedQuery = useDebouncedValue(query, 250)
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

  useEffect(() => {
    if (!editorOpen) return
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape' && !busy) closeEditor()
    }
    window.addEventListener('keydown', closeOnEscape)
    return () => window.removeEventListener('keydown', closeOnEscape)
  }, [editorOpen, busy])

  const filtered = useMemo(() => {
    const term = debouncedQuery.trim().toLocaleLowerCase('es-MX')
    return categories.filter((category) => {
      const matchesText = !term || `${category.name} ${category.code} ${category.description ?? ''}`
        .toLocaleLowerCase('es-MX')
        .includes(term)
      return matchesText && (!statusFilter || category.status === statusFilter)
    })
  }, [categories, debouncedQuery, statusFilter])

  const hasFilters = Boolean(query || statusFilter)

  function openEditor(category?: QuestionCategory) {
    setEditing(category)
    setName(category?.name ?? '')
    setCode(category?.code ?? '')
    setDescription(category?.description ?? '')
    setEditorOpen(true)
  }

  function closeEditor() {
    setEditorOpen(false)
    setEditing(undefined)
    setName('')
    setCode('')
    setDescription('')
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)

    try {
      const saved = editing
        ? await updateQuestionCategory(editing.publicId, {
            name: name.trim(),
            code: code.trim(),
            description: description.trim(),
            expectedEntityVersion: editing.entityVersion
          })
        : await createQuestionCategory({
            name: name.trim(),
            code: code.trim() || undefined,
            description: description.trim()
          })

      setCategories((current) =>
        [...current.filter((category) => category.publicId !== saved.publicId), saved]
          .sort((left, right) => left.name.localeCompare(right.name, 'es-MX'))
      )
      closeEditor()
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
      toast.success(saved.status === 'ACTIVE' ? 'Categoría activada' : 'Categoría desactivada')
    } catch (requestError) {
      const message = requestError instanceof ApiRequestError
        ? requestError.message
        : 'No fue posible cambiar el estado.'
      toast.error('No fue posible cambiar el estado', message)
      if (requestError instanceof ApiRequestError && requestError.status === 409) reload()
    } finally {
      setStatusBusyId(undefined)
    }
  }

  return (
    <main className="content-page resource-page ns-list-page">
      <header className="ns-page-header">
        <div>
          <p className="eyebrow">Contenido</p>
          <h1>Categorías</h1>
          <p className="muted">Clasifica preguntas en uno o varios temas.</p>
        </div>
        <button className="primary-button" type="button" onClick={() => openEditor()}>
          <Icon name="plus" size={16} /> Nueva categoría
        </button>
      </header>

      <FilterToolbar
        resultLabel={`${filtered.length} ${filtered.length === 1 ? 'categoría' : 'categorías'}`}
        hasActiveFilters={hasFilters}
        onClear={() => {
          setQuery('')
          setStatusFilter('')
        }}
      >
        <ResourceSearchField
          value={query}
          onChange={setQuery}
          placeholder="Buscar por nombre, código o descripción"
        />
        <ResourceSelectField label="Estado" value={statusFilter} onChange={(value) => setStatusFilter(value as CatalogStatus | '')}>
          <option value="">Todos</option>
          <option value="ACTIVE">Activas</option>
          <option value="INACTIVE">Inactivas</option>
        </ResourceSelectField>
      </FilterToolbar>

      {error && (
        <section className="inline-error-panel" role="alert">
          <div className="inline-error-icon"><Icon name="error" /></div>
          <div><strong>No fue posible cargar las categorías</strong><p>{error}</p></div>
          <button className="secondary-button compact-button" onClick={reload}>Reintentar</button>
        </section>
      )}

      <section className="ns-data-panel" aria-busy={loading}>
        <div className="ns-data-table-wrap">
          <table className="ns-data-table">
            <thead>
              <tr>
                <th>Categoría</th>
                <th>Código</th>
                <th>Preguntas</th>
                <th>Última actualización</th>
                <th>Estado</th>
                <th className="ns-actions-column">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {loading && <tr><td colSpan={6} className="ns-table-empty">Cargando categorías…</td></tr>}
              {!loading && filtered.length === 0 && (
                <tr>
                  <td colSpan={6} className="ns-table-empty">
                    <strong>{hasFilters ? 'No encontramos coincidencias' : 'Aún no hay categorías'}</strong>
                    <span>{hasFilters ? 'Ajusta o limpia los filtros.' : 'Crea la primera categoría para organizar el banco.'}</span>
                  </td>
                </tr>
              )}
              {!loading && filtered.map((category) => (
                <tr key={category.publicId}>
                  <td className="ns-primary-cell">
                    <strong>{category.name}</strong>
                    <small>{category.description || 'Sin descripción'}</small>
                  </td>
                  <td><code className="ns-code-label">{category.code}</code></td>
                  <td>{category.questionCount}</td>
                  <td>{formatDate(category.updatedAt ?? category.createdAt)}</td>
                  <td>
                    <span className={`status-badge status-${category.status.toLowerCase()}`}>
                      {category.status === 'ACTIVE' ? 'Activa' : 'Inactiva'}
                    </span>
                  </td>
                  <td>
                    <TableActions>
                      <TableActionButton label="Editar" icon="edit" tone="primary" onClick={() => openEditor(category)} />
                      <TableActionButton
                        disabled={statusBusyId === category.publicId}
                        label={category.status === 'ACTIVE' ? 'Desactivar' : 'Activar'}
                        icon={category.status === 'ACTIVE' ? 'archive' : 'restore'}
                        onClick={() => void changeStatus(category)}
                      />
                    </TableActions>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {editorOpen && (
        <div className="ns-dialog-backdrop" role="presentation" onMouseDown={(event) => {
          if (event.currentTarget === event.target && !busy) closeEditor()
        }}>
          <section aria-labelledby="category-dialog-title" aria-modal="true" className="ns-resource-dialog" role="dialog">
            <header>
              <div>
                <p className="eyebrow">{editing ? 'Editar' : 'Nueva'} categoría</p>
                <h2 id="category-dialog-title">{editing ? 'Actualizar categoría' : 'Crear categoría'}</h2>
              </div>
              <button aria-label="Cerrar" className="ns-dialog-close" disabled={busy} type="button" onClick={closeEditor}>
                <Icon name="close" size={17} />
              </button>
            </header>
            <form className="ns-dialog-form" onSubmit={(event) => void submit(event)}>
              <label className="ns-dialog-field">
                <span>Nombre</span>
                <input autoFocus maxLength={150} required value={name} onChange={(event) => setName(event.target.value)} />
              </label>
              <label className="ns-dialog-field">
                <span>Código</span>
                <input maxLength={100} value={code} onChange={(event) => setCode(event.target.value)} placeholder="Se genera automáticamente" />
              </label>
              <label className="ns-dialog-field">
                <span>Descripción</span>
                <textarea maxLength={500} rows={4} value={description} onChange={(event) => setDescription(event.target.value)} />
              </label>
              <footer>
                <button className="secondary-button" disabled={busy} type="button" onClick={closeEditor}>Cancelar</button>
                <button className="primary-button" disabled={busy || name.trim().length < 2} type="submit">
                  {busy ? 'Guardando…' : editing ? 'Guardar cambios' : 'Crear categoría'}
                </button>
              </footer>
            </form>
          </section>
        </div>
      )}
    </main>
  )
}
