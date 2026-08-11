import { useCallback, useEffect, useRef, useState, type ClipboardEvent, type DragEvent } from 'react'
import { uploadQuestionMedia } from '../api/questionApi'
import { ApiRequestError, isPlatformRequestFailure } from '../../../shared/api/apiClient'
import { Icon } from '../../../shared/components/Icon'
import { useToast } from '../../../shared/components/ToastProvider'
import type { QuestionMedia } from '../../../shared/types/questions'

interface MediaUploadFieldProps {
  label: string
  value?: QuestionMedia
  onChange: (media?: QuestionMedia) => void
  compact?: boolean
  capturePagePaste?: boolean
}

const ACCEPTED_TYPES = new Set(['image/png', 'image/jpeg', 'image/webp', 'image/gif'])
const MAX_FILE_SIZE = 5 * 1024 * 1024

function clipboardImage(data: DataTransfer | null): File | null {
  if (!data) return null
  for (let index = 0; index < data.items.length; index += 1) {
    const item = data.items[index]
    if (item?.kind === 'file' && ACCEPTED_TYPES.has(item.type)) {
      return item.getAsFile()
    }
  }
  return null
}

export function MediaUploadField({
  label,
  value,
  onChange,
  compact = false,
  capturePagePaste = false
}: MediaUploadFieldProps) {
  const toast = useToast()
  const inputRef = useRef<HTMLInputElement | null>(null)
  const [busy, setBusy] = useState(false)
  const [dragging, setDragging] = useState(false)

  const upload = useCallback(async (file?: File) => {
    if (!file || busy) return
    if (!ACCEPTED_TYPES.has(file.type)) {
      toast.error('Formato de imagen no compatible', 'Usa PNG, JPEG, WEBP o GIF.')
      return
    }
    if (file.size > MAX_FILE_SIZE) {
      toast.error('La imagen es demasiado grande', 'El tamaño máximo permitido es 5 MB.')
      return
    }
    setBusy(true)
    try {
      onChange(await uploadQuestionMedia(file))
      toast.success('Imagen cargada')
    } catch (error) {
      if (!isPlatformRequestFailure(error)) {
        toast.error('No fue posible cargar la imagen', error instanceof ApiRequestError ? error.message : undefined)
      }
    } finally {
      setBusy(false)
      if (inputRef.current) inputRef.current.value = ''
    }
  }, [busy, onChange, toast])

  function handleDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault()
    setDragging(false)
    void upload(event.dataTransfer.files[0])
  }

  function handlePaste(event: ClipboardEvent<HTMLDivElement>) {
    const image = clipboardImage(event.clipboardData)
    if (!image) return
    event.preventDefault()
    void upload(image)
  }

  useEffect(() => {
    if (!capturePagePaste) return

    function handlePagePaste(event: globalThis.ClipboardEvent) {
      if (event.defaultPrevented) return
      const target = event.target
      if (target instanceof HTMLInputElement
        || target instanceof HTMLTextAreaElement
        || target instanceof HTMLSelectElement
        || (target instanceof HTMLElement && target.isContentEditable)) {
        return
      }

      const image = clipboardImage(event.clipboardData)
      if (!image) return
      event.preventDefault()
      void upload(image)
    }

    document.addEventListener('paste', handlePagePaste)
    return () => document.removeEventListener('paste', handlePagePaste)
  }, [capturePagePaste, upload])

  return (
    <div
      className={`media-upload-field media-upload-enhanced ${compact ? 'compact' : ''} ${dragging ? 'dragging' : ''}`}
      onDragEnter={(event) => { event.preventDefault(); setDragging(true) }}
      onDragLeave={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setDragging(false)
      }}
      onDragOver={(event) => event.preventDefault()}
      onDrop={handleDrop}
      onPaste={handlePaste}
    >
      <div className="media-upload-label-row">
        <label>{label}</label>
        {!value && <small>Arrastra, selecciona o pega una imagen · Ctrl + V</small>}
      </div>
      {value ? (
        <div className="media-preview-enhanced">
          <img src={value.url} alt={value.originalName} />
          <div className="media-preview-copy">
            <strong>{value.originalName}</strong>
            <small>{Math.ceil(value.size / 1024)} KB</small>
          </div>
          <button
            aria-label="Reemplazar imagen"
            className="icon-button"
            type="button"
            onClick={() => inputRef.current?.click()}
          >
            <Icon name="edit" size={15} />
          </button>
          <button
            aria-label="Quitar imagen"
            className="icon-button danger-icon-button"
            type="button"
            onClick={() => onChange(undefined)}
          >
            <Icon name="close" size={15} />
          </button>
        </div>
      ) : (
        <div
          className={`media-drop-zone ${dragging ? 'dragging' : ''} ${busy ? 'busy' : ''}`}
          role="button"
          tabIndex={0}
          onClick={() => !busy && inputRef.current?.click()}
          onKeyDown={(event) => {
            if (event.key === 'Enter' || event.key === ' ') inputRef.current?.click()
          }}
        >
          <span className="media-drop-icon"><Icon name="image" size={20} /></span>
          <span className="media-drop-copy">
            <strong>{busy ? 'Cargando imagen…' : 'Agregar imagen'}</strong>
            <small>Haz clic, arrastra aquí o presiona Ctrl + V</small>
          </span>
        </div>
      )}
      <input
        ref={inputRef}
        className="visually-hidden-input"
        type="file"
        accept="image/png,image/jpeg,image/webp,image/gif"
        disabled={busy}
        onChange={(event) => void upload(event.target.files?.[0])}
      />
    </div>
  )
}
