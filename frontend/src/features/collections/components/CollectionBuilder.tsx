import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type DragEvent,
  type FormEvent
} from 'react'
import { ApiRequestError } from '../../../shared/api/apiClient'
import { Icon } from '../../../shared/components/Icon'
import { useDebouncedValue } from '../../../shared/hooks/useDebouncedValue'
import type {
  CollectionDetail,
  CollectionFormOption,
  CollectionPayload
} from '../../../shared/types/collections'
import { searchCollectionFormOptions } from '../api/collectionApi'

interface CollectionBuilderProps {
  initial?: CollectionDetail
  saving: boolean
  submitLabel: string
  onCancel: () => void
  onSubmit: (payload: CollectionPayload) => Promise<void>
}

const FORM_STATUSES: CollectionFormOption['status'][] = [
  'DRAFT',
  'ACTIVE',
  'DISABLED',
  'CLOSED',
  'ARCHIVED'
]

function normalizeFormStatus(value: unknown): CollectionFormOption['status'] {
  if (
    typeof value === 'string' &&
    FORM_STATUSES.includes(value.toUpperCase() as CollectionFormOption['status'])
  ) {
    return value.toUpperCase() as CollectionFormOption['status']
  }

  return 'DRAFT'
}

function normalizeModeCode(value: unknown): CollectionFormOption['modeCode'] {
  return typeof value === 'string' && value.toUpperCase() === 'PRACTICE'
    ? 'PRACTICE'
    : 'ASSESSMENT'
}

function normalizeText(value: unknown, fallback = ''): string {
  return typeof value === 'string' && value.trim() ? value.trim() : fallback
}

function normalizeNumber(value: unknown, fallback: number): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback
}

function normalizeOption(
  value: Partial<CollectionFormOption> & Record<string, unknown>
): CollectionFormOption {
  return {
    publicId: normalizeText(value.publicId ?? value.formPublicId),
    code: normalizeText(value.code ?? value.formCode),
    title: normalizeText(value.title ?? value.formTitle, 'Formulario sin título'),
    status: normalizeFormStatus(value.status ?? value.formStatus),
    modeCode: normalizeModeCode(value.modeCode ?? value.formMode),
    passingScore: normalizeNumber(value.passingScore, 0),
    durationMinutes:
      typeof value.durationMinutes === 'number' &&
      Number.isFinite(value.durationMinutes)
        ? value.durationMinutes
        : undefined
  }
}

function optionFromLevel(
  level: CollectionDetail['levels'][number]
): CollectionFormOption {
  return normalizeOption(level as unknown as Record<string, unknown>)
}

function modeLabel(mode: CollectionFormOption['modeCode'] | null | undefined) {
  return normalizeModeCode(mode) === 'PRACTICE' ? 'Práctica' : 'Evaluación'
}

function statusLabel(
  status: CollectionFormOption['status'] | null | undefined
) {
  const labels: Record<CollectionFormOption['status'], string> = {
    DRAFT: 'Borrador',
    ACTIVE: 'Activo',
    DISABLED: 'Deshabilitado',
    CLOSED: 'Cerrado',
    ARCHIVED: 'Archivado'
  }

  return labels[normalizeFormStatus(status)]
}

function statusClass(
  status: CollectionFormOption['status'] | null | undefined
): string {
  return normalizeFormStatus(status).toLowerCase()
}

export function CollectionBuilder({
  initial,
  saving,
  submitLabel,
  onCancel,
  onSubmit
}: CollectionBuilderProps) {
  const [name, setName] = useState(initial?.name ?? '')
  const [description, setDescription] = useState(initial?.description ?? '')
  const [levels, setLevels] = useState<CollectionFormOption[]>(
    () =>
      (initial?.levels ?? [])
        .map(optionFromLevel)
        .filter((level) => Boolean(level.publicId))
  )
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState('ALL')
  const [options, setOptions] = useState<CollectionFormOption[]>([])
  const [loadingOptions, setLoadingOptions] = useState(true)
  const [optionsError, setOptionsError] = useState<string>()
  const [formError, setFormError] = useState<string>()
  const [draggedId, setDraggedId] = useState<string>()
  const debouncedQuery = useDebouncedValue(query, 250)
  const requestSequence = useRef(0)

  useEffect(() => {
    const controller = new AbortController()
    const sequence = ++requestSequence.current
    setLoadingOptions(true)
    setOptionsError(undefined)

    searchCollectionFormOptions({
      query: debouncedQuery,
      status,
      signal: controller.signal
    })
      .then((response) => {
        if (sequence !== requestSequence.current) return

        setOptions(
          (response ?? [])
            .map((option) =>
              normalizeOption(
                option as unknown as Partial<CollectionFormOption> &
                  Record<string, unknown>
              )
            )
            .filter((option) => Boolean(option.publicId))
        )
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return
        setOptionsError(
          error instanceof ApiRequestError
            ? error.message
            : 'No fue posible consultar los formularios.'
        )
      })
      .finally(() => {
        if (!controller.signal.aborted && sequence === requestSequence.current) {
          setLoadingOptions(false)
        }
      })

    return () => controller.abort()
  }, [debouncedQuery, status])

  const selectedIds = useMemo(
    () => new Set(levels.map((level) => level.publicId)),
    [levels]
  )

  const availableOptions = useMemo(
    () => options.filter((option) => !selectedIds.has(option.publicId)),
    [options, selectedIds]
  )

  function addForm(option: CollectionFormOption) {
    setLevels((current) => [...current, option])
    setFormError(undefined)
  }

  function removeForm(publicId: string) {
    setLevels((current) => current.filter((item) => item.publicId !== publicId))
  }

  function moveForm(index: number, direction: -1 | 1) {
    setLevels((current) => {
      const target = index + direction
      if (target < 0 || target >= current.length) return current
      const next = [...current]
      const [item] = next.splice(index, 1)
      if (!item) return current
      next.splice(target, 0, item)
      return next
    })
  }

  function dropOn(targetId: string) {
    if (!draggedId || draggedId === targetId) return
    setLevels((current) => {
      const sourceIndex = current.findIndex((item) => item.publicId === draggedId)
      const targetIndex = current.findIndex((item) => item.publicId === targetId)
      if (sourceIndex < 0 || targetIndex < 0) return current
      const next = [...current]
      const [item] = next.splice(sourceIndex, 1)
      if (!item) return current
      next.splice(targetIndex, 0, item)
      return next
    })
    setDraggedId(undefined)
  }

  function handleDrop(event: DragEvent<HTMLElement>, targetId: string) {
    event.preventDefault()
    dropOn(targetId)
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    const normalizedName = name.trim()

    if (normalizedName.length < 3) {
      setFormError('Escribe un nombre de al menos 3 caracteres.')
      return
    }

    if (levels.length === 0) {
      setFormError('Agrega al menos un formulario para construir los niveles.')
      return
    }

    setFormError(undefined)
    await onSubmit({
      name: normalizedName,
      description: description.trim() || undefined,
      formPublicIds: levels.map((level) => level.publicId),
      version: initial?.version
    })
  }

  return (
    <form className="lc-builder" onSubmit={(event) => void submit(event)}>
      <section className="lc-card lc-information-card">
        <div className="lc-section-heading">
          <div className="lc-step-number">1</div>
          <div>
            <h2>Información de la colección</h2>
            <p>Define el programa o ruta que verán los participantes.</p>
          </div>
        </div>

        <div className="lc-form-grid">
          <label className="lc-field lc-field-wide">
            <span>Nombre</span>
            <input
              autoFocus
              maxLength={200}
              placeholder="Ej. Certificación APX"
              value={name}
              onChange={(event) => setName(event.target.value)}
            />
            <small>{name.length}/200</small>
          </label>

          <label className="lc-field lc-field-wide">
            <span>Descripción</span>
            <textarea
              maxLength={2000}
              placeholder="Describe el objetivo y a quién está dirigida esta colección."
              rows={3}
              value={description}
              onChange={(event) => setDescription(event.target.value)}
            />
            <small>{description.length}/2000</small>
          </label>
        </div>
      </section>

      <section className="lc-card lc-levels-card">
        <div className="lc-section-heading lc-section-heading-split">
          <div className="lc-section-heading-copy">
            <div className="lc-step-number">2</div>
            <div>
              <h2>Niveles de la colección</h2>
              <p>
                El Nivel 1 queda disponible al comenzar. Cada nivel posterior se
                desbloquea cuando el participante aprueba el anterior.
              </p>
            </div>
          </div>
          <span className="lc-count-badge">
            {levels.length} {levels.length === 1 ? 'nivel' : 'niveles'}
          </span>
        </div>

        <div className="lc-level-builder-grid">
          <aside className="lc-form-catalog" aria-label="Formularios disponibles">
            <div className="lc-panel-heading">
              <div>
                <h3>Formularios disponibles</h3>
                <p>Busca y agrega formularios a la secuencia.</p>
              </div>
            </div>

            <div className="lc-filter-row">
              <label className="lc-search-field">
                <Icon name="search" size={18} />
                <input
                  placeholder="Buscar por nombre o código"
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                />
                {query && (
                  <button
                    aria-label="Limpiar búsqueda"
                    type="button"
                    onClick={() => setQuery('')}
                  >
                    <Icon name="close" size={14} />
                  </button>
                )}
              </label>

              <label className="lc-select-field">
                <span className="sr-only">Filtrar por estado</span>
                <select value={status} onChange={(event) => setStatus(event.target.value)}>
                  <option value="ALL">Todos los estados</option>
                  <option value="ACTIVE">Activos</option>
                  <option value="DRAFT">Borradores</option>
                  <option value="DISABLED">Deshabilitados</option>
                  <option value="CLOSED">Cerrados</option>
                </select>
              </label>
            </div>

            <div className="lc-catalog-results">
              {loadingOptions && (
                <div className="lc-loading-state">Consultando formularios…</div>
              )}

              {optionsError && !loadingOptions && (
                <div className="lc-inline-error" role="alert">
                  <Icon name="error" size={18} />
                  <span>{optionsError}</span>
                </div>
              )}

              {!loadingOptions && !optionsError && availableOptions.length === 0 && (
                <div className="lc-empty-panel">
                  <Icon name="clipboard" size={24} />
                  <strong>No hay formularios disponibles</strong>
                  <span>
                    {query || status !== 'ALL'
                      ? 'Prueba con otros filtros.'
                      : 'Crea un formulario antes de construir la colección.'}
                  </span>
                </div>
              )}

              {!loadingOptions && !optionsError && availableOptions.map((option) => (
                <article className="lc-form-option" key={option.publicId}>
                  <div className="lc-form-option-main">
                    <div className="lc-form-option-title-row">
                      <strong>{option.title}</strong>
                      <span className={`lc-status lc-status-${statusClass(option.status)}`}>
                        {statusLabel(option.status)}
                      </span>
                    </div>
                    <div className="lc-form-meta">
                      <span>{modeLabel(option.modeCode)}</span>
                      <span>{option.passingScore}% mínimo</span>
                      <span>
                        {option.durationMinutes
                          ? `${option.durationMinutes} min`
                          : 'Sin límite de tiempo'}
                      </span>
                    </div>
                  </div>
                  <button
                    className="lc-add-button"
                    type="button"
                    onClick={() => addForm(option)}
                  >
                    <Icon name="plus" size={16} />
                    Agregar
                  </button>
                </article>
              ))}
            </div>
          </aside>

          <section className="lc-level-sequence" aria-label="Secuencia de niveles">
            <div className="lc-panel-heading">
              <div>
                <h3>Orden de niveles</h3>
                <p>Arrastra los niveles o usa las flechas para reordenarlos.</p>
              </div>
            </div>

            {levels.length === 0 ? (
              <div className="lc-empty-sequence">
                <div className="lc-empty-sequence-icon">
                  <Icon name="clipboard" size={26} />
                </div>
                <strong>Agrega el primer formulario</strong>
                <p>El primer formulario se convertirá automáticamente en el Nivel 1.</p>
              </div>
            ) : (
              <ol className="lc-level-list">
                {levels.map((level, index) => (
                  <li
                    className={`lc-level-item${draggedId === level.publicId ? ' dragging' : ''}`}
                    draggable
                    key={level.publicId}
                    onDragStart={() => setDraggedId(level.publicId)}
                    onDragEnd={() => setDraggedId(undefined)}
                    onDragOver={(event: DragEvent<HTMLElement>) => event.preventDefault()}
                    onDrop={(event: DragEvent<HTMLElement>) => handleDrop(event, level.publicId)}
                  >
                    <div className="lc-level-handle" aria-hidden="true">⋮⋮</div>
                    <div className="lc-level-number">
                      <span>Nivel</span>
                      <strong>{index + 1}</strong>
                    </div>
                    <div className="lc-level-content">
                      <div className="lc-level-title-row">
                        <strong>{level.title || 'Formulario sin título'}</strong>
                        <span className={`lc-status lc-status-${statusClass(level.status)}`}>
                          {statusLabel(level.status)}
                        </span>
                      </div>
                      <div className="lc-form-meta">
                        <span>{modeLabel(level.modeCode)}</span>
                        <span>{level.passingScore}% para aprobar</span>
                        <span>
                          {index === 0
                            ? 'Disponible al iniciar'
                            : 'Se desbloquea al aprobar el nivel anterior'}
                        </span>
                      </div>
                    </div>
                    <div className="lc-level-actions">
                      <button
                        aria-label={`Subir ${level.title || 'formulario'}`}
                        disabled={index === 0}
                        type="button"
                        onClick={() => moveForm(index, -1)}
                      >
                        ↑
                      </button>
                      <button
                        aria-label={`Bajar ${level.title || 'formulario'}`}
                        disabled={index === levels.length - 1}
                        type="button"
                        onClick={() => moveForm(index, 1)}
                      >
                        ↓
                      </button>
                      <button
                        aria-label={`Quitar ${level.title || 'formulario'}`}
                        className="lc-remove-level"
                        type="button"
                        onClick={() => removeForm(level.publicId)}
                      >
                        <Icon name="close" size={15} />
                      </button>
                    </div>
                  </li>
                ))}
              </ol>
            )}
          </section>
        </div>
      </section>

      {formError && (
        <div className="lc-form-error" role="alert">
          <Icon name="error" size={18} />
          <span>{formError}</span>
        </div>
      )}

      <footer className="lc-sticky-actions">
        <div>
          <strong>{levels.length}</strong>
          <span>{levels.length === 1 ? ' nivel configurado' : ' niveles configurados'}</span>
        </div>
        <div className="lc-action-buttons">
          <button className="secondary-button" disabled={saving} type="button" onClick={onCancel}>
            Cancelar
          </button>
          <button className="primary-button" disabled={saving} type="submit">
            {saving ? 'Guardando…' : submitLabel}
          </button>
        </div>
      </footer>
    </form>
  )
}
