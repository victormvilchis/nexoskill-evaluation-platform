import type { ReactNode } from 'react'

type FormActionsProps = {
  children: ReactNode
  className?: string
  sticky?: boolean
}

export function FormActions({ children, className = '', sticky = false }: FormActionsProps) {
  return (
    <footer className={`ns-form-actions${sticky ? ' ns-form-actions--sticky' : ''} ${className}`.trim()}>
      {children}
    </footer>
  )
}
