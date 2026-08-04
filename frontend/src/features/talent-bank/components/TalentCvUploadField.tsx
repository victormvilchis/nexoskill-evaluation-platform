import { useId, useRef, useState, type DragEvent } from 'react'
import { Icon } from '../../../shared/components/Icon'
import type { TalentCvMetadata } from '../../../shared/types/talentBank'

const ACCEPTED_EXTENSIONS = ['pdf', 'doc', 'docx', 'ppt', 'pptx']
const MAX_BYTES = 15 * 1024 * 1024

interface TalentCvUploadFieldProps {
  file?: File
  currentCv?: TalentCvMetadata | null
  disabled?: boolean
  error?: string
  onChange: (file?: File) => void
  onViewCurrent?: () => void | Promise<void>
  onDownloadCurrent?: () => void | Promise<void>
}

function formatBytes(value: number) {
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / (1024 * 1024)).toFixed(1)} MB`
}

export function TalentCvUploadField({ file, currentCv, disabled = false, error, onChange,
  onViewCurrent, onDownloadCurrent }: TalentCvUploadFieldProps) {
  const inputId = useId()
  const inputRef = useRef<HTMLInputElement>(null)
  const [localError, setLocalError] = useState<string>()
  const [dragging, setDragging] = useState(false)

  function select(next?: File) {
    setLocalError(undefined)
    if (!next) { onChange(undefined); return }
    const extension = next.name.split('.').pop()?.toLowerCase() ?? ''
    if (!ACCEPTED_EXTENSIONS.includes(extension)) {
      setLocalError('El CV debe estar en formato PDF, Word o PowerPoint.')
      onChange(undefined)
      return
    }
    if (next.size > MAX_BYTES) {
      setLocalError('El CV no puede superar 15 MB.')
      onChange(undefined)
      return
    }
    onChange(next)
  }

  function drop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault()
    setDragging(false)
    if (!disabled) select(event.dataTransfer.files?.[0])
  }

  return (
    <div className="talent-cv-upload-field">
      {currentCv ? (
        <div className="talent-cv-current" aria-label="CV actual">
          <div>
            <span className="talent-cv-current-label">Documento actual</span>
            <strong>{currentCv.fileName}</strong>
            <small>{currentCv.contentType || currentCv.extension.toUpperCase()} · {formatBytes(currentCv.fileSize)}</small>
          </div>
          <div className="talent-cv-actions">
            {onViewCurrent && <button className="secondary-button compact-button" type="button" disabled={disabled}
              onClick={() => void onViewCurrent()}>Ver CV</button>}
            {onDownloadCurrent && <button className="secondary-button compact-button" type="button" disabled={disabled}
              onClick={() => void onDownloadCurrent()}>Descargar CV</button>}
          </div>
        </div>
      ) : <p className="muted talent-cv-empty">No hay un CV registrado actualmente.</p>}

      <input ref={inputRef} id={inputId} type="file" accept=".pdf,.doc,.docx,.ppt,.pptx" disabled={disabled} hidden
        onChange={(event) => select(event.target.files?.[0])} />
      <div className={`talent-cv-dropzone${dragging ? ' is-dragging' : ''}${disabled ? ' is-disabled' : ''}`}
        role="button" tabIndex={disabled ? -1 : 0} aria-disabled={disabled} aria-describedby={`${inputId}-help`}
        onClick={() => !disabled && inputRef.current?.click()}
        onKeyDown={(event) => { if (!disabled && (event.key === 'Enter' || event.key === ' ')) { event.preventDefault(); inputRef.current?.click() } }}
        onDragEnter={(event) => { event.preventDefault(); if (!disabled) setDragging(true) }}
        onDragOver={(event) => event.preventDefault()} onDragLeave={() => setDragging(false)} onDrop={drop}>
        <span className="talent-cv-dropzone-icon"><Icon name="clipboard" size={24} /></span>
        <div><strong>{file ? file.name : currentCv ? 'Sustituir o actualizar el CV' : 'Seleccionar currículum vitae'}</strong><span>{file ? 'El nuevo archivo está listo para guardarse.' : 'Arrastra el archivo aquí o selecciónalo desde tu equipo.'}</span></div>
        <span className="secondary-button talent-cv-select-button">{file ? 'Cambiar archivo' : currentCv ? 'Seleccionar nuevo CV' : 'Seleccionar archivo'}</span>
      </div>
      <small id={`${inputId}-help`}>Formatos permitidos: PDF, Word y PowerPoint. Tamaño máximo: 15 MB.</small>
      {(error || localError) && <small className="field-error" role="alert">{error ?? localError}</small>}
    </div>
  )
}
