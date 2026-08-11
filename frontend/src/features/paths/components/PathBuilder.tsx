import { useEffect, useMemo, useState, type DragEvent, type FormEvent } from 'react'
import { searchPathCollectionOptions } from '../api/pathApi'
import { ApiRequestError } from '../../../shared/api/apiClient'
import { FullScreenDialog } from '../../../shared/components/FullScreenDialog'
import { Icon } from '../../../shared/components/Icon'
import { useDebouncedValue } from '../../../shared/hooks/useDebouncedValue'
import type { PathCollectionOption, PathDetail, PathPayload } from '../../../shared/types/paths'

interface PathBuilderProps {
  initial?: PathDetail
  saving: boolean
  readOnly?: boolean
  submitLabel: string
  onSubmit: (payload: PathPayload) => Promise<void>
  onCancel: () => void
}

function toOption(detail: PathDetail): PathCollectionOption[] {
  return detail.collections.map((item) => ({
    publicId: item.collectionPublicId,
    name: item.name,
    description: item.description,
    status: item.status,
    contentScope: item.contentScope,
    organizationPublicId: item.organizationPublicId,
    organizationName: item.organizationName,
    formCount: item.formCount
  }))
}

export function PathBuilder({ initial, saving, readOnly = false, submitLabel, onSubmit, onCancel }: PathBuilderProps) {
  const [name, setName] = useState(initial?.name ?? '')
  const [description, setDescription] = useState(initial?.description ?? '')
  const [collections, setCollections] = useState<PathCollectionOption[]>(initial ? toOption(initial) : [])
  const [selectorOpen, setSelectorOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [options, setOptions] = useState<PathCollectionOption[]>([])
  const [loadingOptions, setLoadingOptions] = useState(false)
  const [optionsError, setOptionsError] = useState<string>()
  const [formError, setFormError] = useState<string>()
  const [draggedId, setDraggedId] = useState<string>()
  const debouncedQuery = useDebouncedValue(query, 250)

  useEffect(() => {
    if (!selectorOpen) return
    const controller = new AbortController()
    setLoadingOptions(true)
    setOptionsError(undefined)
    searchPathCollectionOptions(debouncedQuery, controller.signal)
      .then((items) => setOptions(Array.isArray(items) ? items : []))
      .catch((error: unknown) => {
        if (controller.signal.aborted) return
        setOptionsError(error instanceof ApiRequestError ? error.message : 'No fue posible consultar las Colecciones.')
      })
      .finally(() => { if (!controller.signal.aborted) setLoadingOptions(false) })
    return () => controller.abort()
  }, [debouncedQuery, selectorOpen])

  const selectedIds = useMemo(() => new Set(collections.map((item) => item.publicId)), [collections])
  const available = options.filter((item) => !selectedIds.has(item.publicId))

  function addCollection(item: PathCollectionOption) {
    setCollections((current) => current.some((value) => value.publicId === item.publicId) ? current : [...current, item])
    setFormError(undefined)
  }

  function removeCollection(publicId: string) {
    setCollections((current) => current.filter((item) => item.publicId !== publicId))
  }

  function move(index: number, direction: -1 | 1) {
    const target = index + direction
    if (target < 0 || target >= collections.length) return
    setCollections((current) => {
      const next = [...current]
      const source = next[index]
      const destination = next[target]
      if (!source || !destination) return current
      next[index] = destination
      next[target] = source
      return next
    })
  }

  function dropOn(targetId: string) {
    if (!draggedId || draggedId === targetId) return
    setCollections((current) => {
      const sourceIndex = current.findIndex((item) => item.publicId === draggedId)
      const targetIndex = current.findIndex((item) => item.publicId === targetId)
      if (sourceIndex < 0 || targetIndex < 0) return current
      const next = [...current]
      const [moved] = next.splice(sourceIndex, 1)
      if (!moved) return current
      next.splice(targetIndex, 0, moved)
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
    if (readOnly || saving) return
    const normalizedName = name.trim()
    if (normalizedName.length < 3) {
      setFormError('Escribe un nombre de al menos 3 caracteres.')
      return
    }
    setFormError(undefined)
    await onSubmit({
      name: normalizedName,
      description: description.trim() || undefined,
      collectionPublicIds: collections.map((item) => item.publicId),
      version: initial?.version
    })
  }

  return (
    <>
      <form className="path-builder" onSubmit={(event) => void submit(event)}>
        <section className="ns-card path-editor-card">
          <div className="ns-card-heading"><div><span className="ns-step">1</span><h2>Información general</h2></div></div>
          <div className="foundation-form-grid">
            <label className="form-field ns-field-span-6"><span>Nombre del Path</span>
              <input autoFocus={!readOnly} maxLength={200} value={name} disabled={readOnly || saving}
                placeholder="Ej. APX JR" onChange={(event) => setName(event.target.value)} />
            </label>
            <label className="form-field ns-field-span-12"><span>Descripción <small>(opcional)</small></span>
              <textarea rows={3} maxLength={2000} value={description} disabled={readOnly || saving}
                placeholder="Describe el objetivo de esta ruta de desarrollo." onChange={(event) => setDescription(event.target.value)} />
            </label>
          </div>
        </section>

        <section className="ns-card path-editor-card">
          <div className="ns-card-heading path-heading-actions">
            <div><span className="ns-step">2</span><h2>Contenido del Path</h2></div>
            {!readOnly && <button className="secondary-button" type="button" disabled={saving} onClick={() => setSelectorOpen(true)}><Icon name="plus" size={15} /> Agregar Colección</button>}
          </div>
          <p className="muted">Las Colecciones se relacionan al Path sin copiar sus Formularios ni Preguntas. Arrastra o utiliza las flechas para definir el recorrido.</p>

          {collections.length === 0 ? (
            <div className="path-empty-admin"><Icon name="collections" size={24} /><strong>Este Path todavía no tiene Colecciones</strong><span>Puedes guardarlo vacío y agregar contenido posteriormente.</span></div>
          ) : (
            <ol className="path-collection-list">
              {collections.map((item, index) => (
                <li key={item.publicId} draggable={!readOnly} className={draggedId === item.publicId ? 'dragging' : ''}
                  onDragStart={() => setDraggedId(item.publicId)} onDragEnd={() => setDraggedId(undefined)}
                  onDragOver={(event) => { if (!readOnly) event.preventDefault() }} onDrop={(event) => !readOnly && handleDrop(event, item.publicId)}>
                  <span className="path-order">{index + 1}</span>
                  <div className="path-collection-copy"><strong>{item.name}</strong><small>{item.formCount} {item.formCount === 1 ? 'Formulario' : 'Formularios'} · {item.organizationName}</small>{item.description && <small>{item.description}</small>}</div>
                  {!readOnly && <div className="path-order-actions">
                    <button type="button" className="icon-button" aria-label="Subir Colección" disabled={index === 0 || saving} onClick={() => move(index, -1)}><Icon name="arrowUp" size={16} /></button>
                    <button type="button" className="icon-button" aria-label="Bajar Colección" disabled={index === collections.length - 1 || saving} onClick={() => move(index, 1)}><Icon name="arrowDown" size={16} /></button>
                    <button type="button" className="icon-button danger-icon-button" aria-label="Retirar Colección" disabled={saving} onClick={() => removeCollection(item.publicId)}><Icon name="close" size={16} /></button>
                  </div>}
                </li>
              ))}
            </ol>
          )}
        </section>

        {formError && <div className="inline-error-panel" role="alert"><Icon name="error" size={18} /><p>{formError}</p></div>}
        <div className="form-actions path-form-actions">
          <button className="secondary-button" type="button" disabled={saving} onClick={onCancel}>{readOnly ? 'Regresar' : 'Cancelar'}</button>
          {!readOnly && <button className="primary-button" type="submit" disabled={saving}>{saving ? 'Guardando…' : submitLabel}</button>}
        </div>
      </form>

      <FullScreenDialog open={selectorOpen} title="Agregar Colección" eyebrow="Contenido del Path"
        description="Selecciona una Colección existente. Su contenido continuará administrándose desde Colecciones y Formularios."
        onClose={() => setSelectorOpen(false)} footer={<button className="secondary-button" type="button" onClick={() => setSelectorOpen(false)}>Cerrar</button>}>
        <div className="path-collection-selector">
          <label className="path-selector-search"><Icon name="search" size={18} /><input autoFocus value={query} placeholder="Buscar Colección por nombre, código o descripción" onChange={(event) => setQuery(event.target.value)} /></label>
          {loadingOptions && <div className="path-selector-state">Consultando Colecciones…</div>}
          {optionsError && !loadingOptions && <div className="inline-error-panel" role="alert"><Icon name="error" size={18} /><p>{optionsError}</p></div>}
          {!loadingOptions && !optionsError && available.length === 0 && <div className="path-selector-state"><strong>No hay Colecciones disponibles</strong><span>{query ? 'Prueba con otro criterio de búsqueda.' : 'Las Colecciones activas y permitidas para este alcance aparecerán aquí.'}</span></div>}
          {!loadingOptions && !optionsError && available.map((item) => (
            <article className="path-selector-option" key={item.publicId}><div><strong>{item.name}</strong><small>{item.formCount} {item.formCount === 1 ? 'Formulario' : 'Formularios'} · {item.organizationName}</small>{item.description && <p>{item.description}</p>}</div><button className="secondary-button" type="button" onClick={() => addCollection(item)}><Icon name="plus" size={14} /> Agregar</button></article>
          ))}
        </div>
      </FullScreenDialog>
    </>
  )
}
