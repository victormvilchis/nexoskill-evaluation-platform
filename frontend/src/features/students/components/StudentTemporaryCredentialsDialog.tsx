import { useEffect, useId, useRef, type KeyboardEvent } from 'react'
import { Icon } from '../../../shared/components/Icon'
import { useToast } from '../../../shared/components/ToastProvider'
import type { StudentTemporaryCredentials } from '../../../shared/types/students'

interface Props {
  title: string
  credentials: StudentTemporaryCredentials
  onClose: () => void
}

export function StudentTemporaryCredentialsDialog({ title, credentials, onClose }: Props) {
  const toast = useToast()
  const titleId = useId()
  const descriptionId = useId()
  const dialogRef = useRef<HTMLElement>(null)
  const closeRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    const previousOverflow = document.body.style.overflow
    const previouslyFocused = document.activeElement instanceof HTMLElement
      ? document.activeElement
      : undefined

    document.body.style.overflow = 'hidden'
    window.requestAnimationFrame(() => closeRef.current?.focus())

    return () => {
      document.body.style.overflow = previousOverflow
      previouslyFocused?.focus()
    }
  }, [])

  function handleKeyDown(event: KeyboardEvent<HTMLElement>) {
    if (event.key === 'Escape') {
      event.preventDefault()
      onClose()
      return
    }

    if (event.key !== 'Tab') return
    const focusable = dialogRef.current?.querySelectorAll<HTMLElement>(
      'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
    )
    if (!focusable?.length) return

    const first = focusable.item(0)
    const last = focusable.item(focusable.length - 1)
    if (!first || !last) return
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault()
      first.focus()
    }
  }

  async function copy(value: string, success: string) {
    try {
      await navigator.clipboard.writeText(value)
      toast.success(success)
    } catch {
      toast.warning('No fue posible copiar automáticamente', 'Selecciona el texto y cópialo manualmente.')
    }
  }

  const userAndPassword = `Correo: ${credentials.email}\nContraseña temporal: ${credentials.temporaryPassword}`
  const codeLine = credentials.studentCode ? `Código: ${credentials.studentCode}\n` : ''
  const complete = `Organización: ${credentials.organizationLogin}\n${codeLine}${userAndPassword}`

  return (
    <div className="dialog-backdrop" role="presentation">
      <section
        ref={dialogRef}
        className="confirm-dialog student-credentials-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={descriptionId}
        onKeyDown={handleKeyDown}
      >
        <header className="dialog-header">
          <div className="dialog-symbol dialog-symbol-primary">
            <Icon name="lock" size={20} />
          </div>
          <div className="dialog-heading-copy">
            <p className="eyebrow">Visualización única</p>
            <h2 id={titleId}>{title}</h2>
            <p id={descriptionId}>La contraseña temporal se mostrará una sola vez. Compártela de forma segura. El colaborador deberá cambiarla durante su primer inicio de sesión.</p>
          </div>
          <button ref={closeRef} aria-label="Cerrar diálogo" className="dialog-close-button" type="button" onClick={onClose}>
            <Icon name="close" size={18} />
          </button>
        </header>

        <div className="dialog-content temporary-credentials">
          {credentials.studentCode && (
            <div className="temporary-credential-row">
              <div className="temporary-credential-copy"><span>Código a nivel organización</span><code>{credentials.studentCode}</code></div>
              <button className="secondary-button temporary-credential-copy-button" type="button"
                onClick={() => void copy(credentials.studentCode!, 'Código copiado.')}><Icon name="copy" size={16} /> Copiar</button>
            </div>
          )}
          <div className="temporary-credential-row">
            <div className="temporary-credential-copy"><span>Organización</span><code>{credentials.organizationLogin}</code></div>
            <button className="secondary-button temporary-credential-copy-button" type="button"
              onClick={() => void copy(credentials.organizationLogin, 'Organización copiada.')}><Icon name="copy" size={16} /> Copiar</button>
          </div>
          <div className="temporary-credential-row">
            <div className="temporary-credential-copy"><span>Correo</span><code>{credentials.email}</code></div>
            <button className="secondary-button temporary-credential-copy-button" type="button"
              onClick={() => void copy(credentials.email, 'Correo copiado.')}><Icon name="copy" size={16} /> Copiar</button>
          </div>
          <div className="temporary-credential-row">
            <div className="temporary-credential-copy"><span>Contraseña temporal</span><code>{credentials.temporaryPassword}</code></div>
            <button className="secondary-button temporary-credential-copy-button" type="button"
              onClick={() => void copy(credentials.temporaryPassword, 'Contraseña copiada.')}><Icon name="copy" size={16} /> Copiar</button>
          </div>
        </div>

        <footer className="dialog-actions student-credentials-actions">
          <button className="secondary-button" type="button"
            onClick={() => void copy(userAndPassword, 'Usuario y contraseña copiados.')}>Copiar usuario y contraseña</button>
          <button className="secondary-button" type="button"
            onClick={() => void copy(complete, 'Credenciales copiadas.')}>Copiar todos los datos</button>
          <button className="primary-button" type="button" onClick={onClose}>Cerrar</button>
        </footer>
      </section>
    </div>
  )
}
