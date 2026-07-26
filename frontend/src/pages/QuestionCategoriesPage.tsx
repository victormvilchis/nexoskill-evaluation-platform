import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import {
  changeQuestionCategoryStatus,
  createQuestionCategory,
  deleteQuestionCategory,
  getQuestionCategories,
  getQuestionCategoryDependencies,
  getQuestionCategoryStatusHistory,
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
import type {
  CatalogStatus,
  QuestionCategory,
  QuestionCategoryDependencies,
  QuestionCategoryStatusHistory
} from '../shared/types/questions'

type StatusFilter = CatalogStatus | 'ALL'

type DialogMode = 'view' | 'edit' | 'manage'

const statusLabels: Record<CatalogStatus, string> = {
  ACTIVE: 'Activa',
  INACTIVE: 'Inactiva',
  DELETED: 'Eliminada'
}

function formatDate(value?: string) {
  if (!value) return 'Sin registro'
  return new Intl.DateTimeFormat('es-MX', {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(new Date(value))
}

export function QuestionCategoriesPage() {
  const toast = useToast()
  const [categories, setCategories] = useState<QuestionCategory[]>([])
  const [selected, setSelected] = useState<QuestionCategory>()
  const [dialogMode, setDialogMode] = useState<DialogMode>()
  const [editorOpen, setEditorOpen] = useState(false)
  const [name, setName] = useState('')
  const [code, setCode] = useState('')
  const [description, setDescription] = useState('')
  const [query, setQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ACTIVE')
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string>()
  const [reloadKey, setReloadKey] = useState(0)
  const [dependencies, setDependencies] = useState<QuestionCategoryDependencies>()
  const [history, setHistory] = useState<QuestionCategoryStatusHistory[]>([])
  const [managementLoading, setManagementLoading] = useState(false)
  const [managementReason, setManagementReason] = useState('')
  const debouncedQuery = useDebouncedValue(query, 250)
  const reload = useCallback(() => setReloadKey((value) => value + 1), [])

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(undefined)

    getQuestionCategories(controller.signal, statusFilter)
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
  }, [reloadKey, statusFilter])

  useEffect(() => {
    if (!editorOpen && !dialogMode) return
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape' && !busy) closeDialog()
    }
    window.addEventListener('keydown', closeOnEscape)
    return () => window.removeEventListener('keydown', closeOnEscape)
  }, [editorOpen, dialogMode, busy])

  const filtered = useMemo(() => {
    const term = debouncedQuery.trim().toLocaleLowerCase('es-MX')
    return categories.filter((category) => !term || `${category.name} ${category.code} ${category.description ?? ''} ${category.ownerOrganizationName ?? ''}`
      .toLocaleLowerCase('es-MX')
      .includes(term))
  }, [categories, debouncedQuery])

  const hasFilters = Boolean(query || statusFilter !== 'ACTIVE')

  function openCreate() {
    setSelected(undefined)
    setName('')
    setCode('')
    setDescription('')
    setEditorOpen(true)
    setDialogMode(undefined)
  }

  function openDialog(mode: DialogMode, category: QuestionCategory) {
    setSelected(category)
    setDialogMode(mode)
    setEditorOpen(mode === 'edit')
    setName(category.name)
    setCode(category.code)
    setDescription(category.description ?? '')
    setDependencies(undefined)
    setHistory([])
    setManagementReason('')

    if (mode === 'manage') {
      setManagementLoading(true)
      Promise.all([
        getQuestionCategoryDependencies(category.publicId),
        getQuestionCategoryStatusHistory(category.publicId)
      ])
        .then(([dependencyResponse, historyResponse]) => {
          setDependencies(dependencyResponse)
          setHistory(historyResponse)
        })
        .catch((requestError) => {
          toast.error(
            'No fue posible cargar la administración',
            requestError instanceof ApiRequestError ? requestError.message : undefined
          )
        })
        .finally(() => setManagementLoading(false))
    }
  }

  function closeDialog() {
    setEditorOpen(false)
    setDialogMode(undefined)
    setSelected(undefined)
    setName('')
    setCode('')
    setDescription('')
    setDependencies(undefined)
    setHistory([])
    setManagementReason('')
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (busy) return
    setBusy(true)

    try {
      const saved = selected
        ? await updateQuestionCategory(selected.publicId, {
            name: name.trim(),
            code: code.trim(),
            description: description.trim(),
            expectedEntityVersion: selected.entityVersion
          })
        : await createQuestionCategory({
            name: name.trim(),
            code: code.trim() || undefined,
            description: description.trim()
          })

      closeDialog()
      reload()
      toast.success(selected ? 'Categoría actualizada' : 'Categoría creada')
      if (saved.status !== statusFilter && statusFilter !== 'ALL') reload()
    } catch (requestError) {
      toast.error(
        'No fue posible guardar la categoría',
        requestError instanceof ApiRequestError ? requestError.message : undefined
      )
    } finally {
      setBusy(false)
    }
  }

  async function executeStatusAction(action: 'activate' | 'deactivate' | 'delete') {
    if (!selected) return
    setBusy(true)
    try {
      const saved = action === 'delete'
        ? await deleteQuestionCategory(selected.publicId, selected.entityVersion, managementReason.trim() || undefined)
        : await changeQuestionCategoryStatus(
            selected.publicId,
            action === 'activate' ? 'ACTIVE' : 'INACTIVE',
            selected.entityVersion,
            managementReason.trim() || undefined
          )
      toast.success(
        action === 'activate'
          ? 'Categoría reactivada'
          : action === 'deactivate'
            ? 'Categoría inactivada'
            : 'Categoría eliminada'
      )
      setSelected(saved)
      closeDialog()
      reload()
    } catch (requestError) {
      const message = requestError instanceof ApiRequestError
        ? requestError.message
        : 'No fue posible completar la acción.'
      toast.error('No fue posible administrar la categoría', message)
      if (requestError instanceof ApiRequestError && requestError.status === 409) reload()
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="content-page resource-page ns-list-page category-lifecycle-page">
      <header className="ns-page-header">
        <div>
          <p className="eyebrow">Contenido</p>
          <h1>Categorías</h1>
          <p className="muted">Administra categorías globales y organizacionales sin romper las preguntas existentes.</p>
        </div>
        <button className="primary-button" type="button" onClick={openCreate}>
          <Icon name="plus" size={16} /> Nueva categoría
        </button>
      </header>

      <FilterToolbar
        resultLabel={`${filtered.length} ${filtered.length === 1 ? 'categoría' : 'categorías'}`}
        hasActiveFilters={hasFilters}
        onClear={() => {
          setQuery('')
          setStatusFilter('ACTIVE')
        }}
      >
        <ResourceSearchField
          value={query}
          onChange={setQuery}
          placeholder="Buscar por nombre, código, descripción u organización"
        />
        <ResourceSelectField label="Estado" value={statusFilter} onChange={(value) => setStatusFilter(value as StatusFilter)}>
          <option value="ACTIVE">Activas</option>
          <option value="INACTIVE">Inactivas</option>
          <option value="DELETED">Eliminadas</option>
          <option value="ALL">Todas</option>
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
                <th>Alcance</th>
                <th>Organización</th>
                <th>Preguntas</th>
                <th>Creación</th>
                <th>Estado</th>
                <th className="ns-actions-column">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {loading && <tr><td colSpan={7} className="ns-table-empty">Cargando categorías…</td></tr>}
              {!loading && filtered.length === 0 && (
                <tr>
                  <td colSpan={7} className="ns-table-empty">
                    <strong>{hasFilters ? 'No encontramos coincidencias' : 'Aún no hay categorías activas'}</strong>
                    <span>{hasFilters ? 'Ajusta o limpia los filtros.' : 'Crea la primera categoría para organizar el banco.'}</span>
                  </td>
                </tr>
              )}
              {!loading && filtered.map((category) => (
                <tr key={category.publicId}>
                  <td className="ns-primary-cell">
                    <strong>{category.name}</strong>
                    <small><code className="ns-code-label">{category.code}</code> · {category.description || 'Sin descripción'}</small>
                  </td>
                  <td>{category.contentScope === 'GLOBAL' ? 'Global' : 'Organizacional'}</td>
                  <td>{category.ownerOrganizationName ?? 'GLOBAL'}</td>
                  <td>{category.questionCount}</td>
                  <td>{formatDate(category.createdAt)}</td>
                  <td>
                    <span className={`status-badge status-${category.status.toLowerCase()}`}>
                      {statusLabels[category.status]}
                    </span>
                  </td>
                  <td>
                    <TableActions>
                      <TableActionButton label="Ver" icon="eye" onClick={() => openDialog('view', category)} />
                      {category.status !== 'DELETED' && (
                        <TableActionButton label="Editar" icon="edit" tone="primary" onClick={() => openDialog('edit', category)} />
                      )}
                      <TableActionButton label="Administrar" icon="archive" onClick={() => openDialog('manage', category)} />
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
          if (event.currentTarget === event.target && !busy) closeDialog()
        }}>
          <section aria-labelledby="category-editor-title" aria-modal="true" className="ns-resource-dialog" role="dialog">
            <header>
              <div>
                <p className="eyebrow">{selected ? 'Editar' : 'Nueva'} categoría</p>
                <h2 id="category-editor-title">{selected ? 'Actualizar categoría' : 'Crear categoría'}</h2>
              </div>
              <button aria-label="Cerrar" className="ns-dialog-close" disabled={busy} type="button" onClick={closeDialog}>
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
                <input maxLength={80} value={code} onChange={(event) => setCode(event.target.value)} placeholder="Se genera automáticamente" />
              </label>
              <label className="ns-dialog-field">
                <span>Descripción</span>
                <textarea maxLength={500} rows={4} value={description} onChange={(event) => setDescription(event.target.value)} />
              </label>
              {selected && (
                <div className="category-dialog-context">
                  <span>Alcance</span>
                  <strong>{selected.contentScope === 'GLOBAL' ? 'GLOBAL' : selected.ownerOrganizationName}</strong>
                </div>
              )}
              <footer>
                <button className="secondary-button" disabled={busy} type="button" onClick={closeDialog}>Cancelar</button>
                <button className="primary-button" disabled={busy || name.trim().length < 2} type="submit">
                  {busy ? 'Guardando…' : selected ? 'Guardar cambios' : 'Crear categoría'}
                </button>
              </footer>
            </form>
          </section>
        </div>
      )}

      {dialogMode === 'view' && selected && (
        <div className="ns-dialog-backdrop" role="presentation" onMouseDown={(event) => {
          if (event.currentTarget === event.target) closeDialog()
        }}>
          <section aria-labelledby="category-view-title" aria-modal="true" className="ns-resource-dialog category-detail-dialog" role="dialog">
            <header>
              <div><p className="eyebrow">Ver categoría</p><h2 id="category-view-title">{selected.name}</h2></div>
              <button aria-label="Cerrar" className="ns-dialog-close" type="button" onClick={closeDialog}><Icon name="close" size={17} /></button>
            </header>
            <div className="category-readonly-grid">
              <div><span>Nombre</span><strong>{selected.name}</strong></div>
              <div><span>Código</span><strong>{selected.code}</strong></div>
              <div><span>Estado</span><strong>{statusLabels[selected.status]}</strong></div>
              <div><span>Alcance</span><strong>{selected.contentScope === 'GLOBAL' ? 'Global' : 'Organizacional'}</strong></div>
              <div><span>Organización propietaria</span><strong>{selected.ownerOrganizationName ?? 'GLOBAL'}</strong></div>
              <div><span>Preguntas relacionadas</span><strong>{selected.questionCount}</strong></div>
              <div><span>Fecha de creación</span><strong>{formatDate(selected.createdAt)}</strong></div>
              <div><span>Última actualización</span><strong>{formatDate(selected.updatedAt)}</strong></div>
              <div className="category-readonly-wide"><span>Descripción</span><strong>{selected.description || 'Sin descripción'}</strong></div>
            </div>
            <footer className="category-dialog-footer"><button className="secondary-button" type="button" onClick={closeDialog}>Cerrar</button></footer>
          </section>
        </div>
      )}

      {dialogMode === 'manage' && selected && (
        <div className="ns-dialog-backdrop" role="presentation" onMouseDown={(event) => {
          if (event.currentTarget === event.target && !busy) closeDialog()
        }}>
          <section aria-labelledby="category-manage-title" aria-modal="true" className="ns-resource-dialog category-management-dialog" role="dialog">
            <header>
              <div><p className="eyebrow">Administrar categoría</p><h2 id="category-manage-title">{selected.name}</h2></div>
              <button aria-label="Cerrar" className="ns-dialog-close" disabled={busy} type="button" onClick={closeDialog}><Icon name="close" size={17} /></button>
            </header>

            {managementLoading ? <p className="muted category-dialog-loading">Cargando dependencias e historial…</p> : (
              <div className="category-management-content">
                <div className="category-management-summary">
                  <div><span>Estado actual</span><strong>{statusLabels[selected.status]}</strong></div>
                  <div><span>Preguntas relacionadas</span><strong>{dependencies?.questionCount ?? selected.questionCount}</strong></div>
                  <div><span>Alcance</span><strong>{selected.contentScope === 'GLOBAL' ? 'GLOBAL' : selected.ownerOrganizationName}</strong></div>
                </div>

                {selected.status === 'ACTIVE' && (
                  <section className="category-action-panel">
                    <h3>Inactivar categoría</h3>
                    <p>La categoría dejará de estar disponible para nuevas preguntas. Las preguntas existentes conservarán esta categoría.</p>
                    <button className="secondary-button" disabled={busy} type="button" onClick={() => void executeStatusAction('deactivate')}>Inactivar categoría</button>
                  </section>
                )}

                {selected.status === 'INACTIVE' && (
                  <>
                    <section className="category-action-panel">
                      <h3>Reactivar categoría</h3>
                      <p>Volverá a aparecer en los selectores y podrá asignarse a nuevas preguntas.</p>
                      <button className="secondary-button" disabled={busy} type="button" onClick={() => void executeStatusAction('activate')}>Reactivar categoría</button>
                    </section>
                    <section className="category-action-panel category-danger-panel">
                      <h3>Eliminar lógicamente</h3>
                      <p>{dependencies?.message ?? 'La eliminación solo está disponible cuando no existen preguntas relacionadas.'}</p>
                      <button className="danger-button" disabled={busy || !dependencies?.canDelete} type="button" onClick={() => void executeStatusAction('delete')}>Eliminar categoría</button>
                    </section>
                  </>
                )}

                {selected.status === 'DELETED' && (
                  <section className="category-action-panel">
                    <h3>Categoría eliminada</h3>
                    <p>La eliminación es lógica. No existe reactivación desde el flujo normal; el historial se conserva.</p>
                  </section>
                )}

                {selected.status !== 'DELETED' && (
                  <label className="ns-dialog-field">
                    <span>Motivo o nota administrativa</span>
                    <textarea maxLength={500} rows={3} value={managementReason} onChange={(event) => setManagementReason(event.target.value)} placeholder="Opcional para activación e inactivación; recomendado para eliminación" />
                  </label>
                )}

                <section className="category-history-section">
                  <h3>Historial de estados</h3>
                  {history.length === 0 ? <p className="muted">Sin movimientos registrados.</p> : (
                    <ol>
                      {history.map((item) => (
                        <li key={item.id}>
                          <strong>{item.previousStatus ? `${statusLabels[item.previousStatus]} → ` : ''}{statusLabels[item.newStatus]}</strong>
                          <span>{formatDate(item.occurredAt)}</span>
                          <small>{item.reason || 'Sin motivo registrado'}</small>
                        </li>
                      ))}
                    </ol>
                  )}
                </section>
              </div>
            )}
            <footer className="category-dialog-footer"><button className="secondary-button" disabled={busy} type="button" onClick={closeDialog}>Cerrar</button></footer>
          </section>
        </div>
      )}
    </main>
  )
}
