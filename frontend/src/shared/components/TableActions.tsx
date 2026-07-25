import type { ButtonHTMLAttributes, PropsWithChildren } from 'react'
import { Link } from 'react-router-dom'
import { Icon, type IconName } from './Icon'

export function TableActions({ children }: PropsWithChildren) {
  return <div className="ns-table-actions">{children}</div>
}

interface TableActionLinkProps {
  to: string
  label: string
  icon: IconName
  tone?: 'default' | 'primary' | 'danger'
}

export function TableActionLink({
  to,
  label,
  icon,
  tone = 'default'
}: TableActionLinkProps) {
  return (
    <Link
      aria-label={label}
      className={`ns-table-action ns-table-action-${tone}`}
      title={label}
      to={to}
    >
      <Icon name={icon} size={15} />
      <span>{label}</span>
    </Link>
  )
}

interface TableActionButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  label: string
  icon: IconName
  tone?: 'default' | 'primary' | 'danger'
}

export function TableActionButton({
  label,
  icon,
  tone = 'default',
  className = '',
  type = 'button',
  ...props
}: TableActionButtonProps) {
  return (
    <button
      aria-label={label}
      className={`ns-table-action ns-table-action-${tone} ${className}`.trim()}
      title={label}
      type={type}
      {...props}
    >
      <Icon name={icon} size={15} />
      <span>{label}</span>
    </button>
  )
}
