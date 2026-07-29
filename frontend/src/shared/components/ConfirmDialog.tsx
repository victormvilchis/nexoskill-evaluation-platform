import type { ReactNode } from 'react'
import { Icon } from './Icon'

interface ConfirmDialogProps {
  open: boolean
  title: string
  description: string
  confirmLabel: string
  cancelLabel?: string
  tone?: 'primary' | 'danger'
  busy?: boolean
  confirmDisabled?: boolean
  children?: ReactNode
  onConfirm: () => void
  onCancel: () => void
}

export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel,
  cancelLabel = 'Cancelar',
  tone = 'primary',
  busy = false,
  confirmDisabled = false,
  children,
  onConfirm,
  onCancel
}: ConfirmDialogProps) {
  if (!open) return null

  return (
    <div className="dialog-backdrop" role="presentation" onMouseDown={onCancel}>
      <section
        aria-labelledby="confirm-dialog-title"
        aria-modal="true"
        className="confirm-dialog"
        role="dialog"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className={`dialog-symbol dialog-symbol-${tone}`}>
          <Icon name={tone === 'danger' ? 'warning' : 'info'} size={20} />
        </div>
        <div>
          <h2 id="confirm-dialog-title">{title}</h2>
          <p>{description}</p>
        </div>
        {children && <div className="dialog-content">{children}</div>}
        <div className="dialog-actions">
          <button className="secondary-button" type="button" disabled={busy} onClick={onCancel}>
            {cancelLabel}
          </button>
          <button
            className={tone === 'danger' ? 'danger-button' : 'primary-button'}
            type="button"
            disabled={busy || confirmDisabled}
            onClick={onConfirm}
          >
            {busy ? 'Procesando…' : confirmLabel}
          </button>
        </div>
      </section>
    </div>
  )
}
