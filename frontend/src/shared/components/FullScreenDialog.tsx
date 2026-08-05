import { useEffect, useId, useRef, type KeyboardEvent, type ReactNode } from 'react'
import { Icon } from './Icon'

interface FullScreenDialogProps {
  open: boolean
  title: string
  description?: string
  eyebrow?: string
  children: ReactNode
  footer?: ReactNode
  className?: string
  onClose: () => void
}

export function FullScreenDialog({
  open,
  title,
  description,
  eyebrow,
  children,
  footer,
  className = '',
  onClose
}: FullScreenDialogProps) {
  const titleId = useId()
  const descriptionId = useId()
  const dialogRef = useRef<HTMLElement>(null)
  const closeRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (!open) return
    const previousOverflow = document.body.style.overflow
    const previouslyFocused = document.activeElement instanceof HTMLElement ? document.activeElement : undefined
    document.body.style.overflow = 'hidden'
    window.requestAnimationFrame(() => closeRef.current?.focus())
    return () => {
      document.body.style.overflow = previousOverflow
      previouslyFocused?.focus()
    }
  }, [open])

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

  if (!open) return null

  return (
    <div className="form-fullscreen-backdrop" role="presentation">
      <section
        ref={dialogRef}
        className={`form-fullscreen-dialog ${className}`.trim()}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={description ? descriptionId : undefined}
        onKeyDown={handleKeyDown}
      >
        <header className="form-fullscreen-header">
          <div>
            {eyebrow && <span className="form-fullscreen-eyebrow">{eyebrow}</span>}
            <h2 id={titleId}>{title}</h2>
            {description && <p id={descriptionId}>{description}</p>}
          </div>
          <button ref={closeRef} className="form-fullscreen-close" type="button" aria-label="Cerrar" onClick={onClose}>
            <Icon name="close" size={20} />
          </button>
        </header>
        <div className="form-fullscreen-body">{children}</div>
        {footer && <footer className="form-fullscreen-footer">{footer}</footer>}
      </section>
    </div>
  )
}
