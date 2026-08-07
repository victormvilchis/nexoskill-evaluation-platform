import type { ReactNode } from 'react'
import { Icon } from './Icon'
import { SelectField } from './SelectField'

interface ResourceSearchFieldProps {
  value: string
  onChange: (value: string) => void
  placeholder: string
  ariaLabel?: string
  disabled?: boolean
}

export function ResourceSearchField({
  value,
  onChange,
  placeholder,
  ariaLabel = placeholder,
  disabled = false
}: ResourceSearchFieldProps) {
  return (
    <label className="ns-resource-search">
      <Icon name="search" size={17} />
      <input
        aria-label={ariaLabel}
        autoComplete="off"
        disabled={disabled}
        placeholder={placeholder}
        spellCheck={false}
        type="text"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  )
}

type ResourceSelectWidth = 'compact' | 'medium' | 'wide'

interface ResourceSelectFieldProps {
  label: string
  value: string
  onChange: (value: string) => void
  children: ReactNode
  disabled?: boolean
  ariaLabel?: string
  width?: ResourceSelectWidth
}

function normalizedLabel(value: string) {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .trim()
    .toLowerCase()
}

function inferredWidth(label: string): ResourceSelectWidth {
  const normalized = normalizedLabel(label)
  if (['estado', 'alcance', 'modulo', 'tipo', 'ano de creacion'].includes(normalized)) return 'compact'
  if (['organizacion', 'categoria', 'tecnologia'].includes(normalized)) return 'wide'
  return 'medium'
}

export function ResourceSelectField({
  label,
  value,
  onChange,
  children,
  disabled = false,
  ariaLabel,
  width
}: ResourceSelectFieldProps) {
  const resolvedWidth = width ?? inferredWidth(label)
  return (
    <label className={`ns-resource-select ns-resource-select--${resolvedWidth}`}>
      <span>{label}</span>
      <div className="ns-resource-select-control">
        <SelectField
          ariaLabel={ariaLabel ?? label}
          disabled={disabled}
          value={value}
          onChange={onChange}
        >
          {children}
        </SelectField>
      </div>
    </label>
  )
}
