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
      <Icon name="search" size={18} />
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
      {value.length > 0 && (
        <button
          aria-label="Limpiar búsqueda"
          className="ns-resource-search-clear"
          disabled={disabled}
          type="button"
          onClick={() => onChange('')}
        >
          <Icon name="close" size={14} />
        </button>
      )}
    </label>
  )
}

interface ResourceSelectFieldProps {
  label: string
  value: string
  onChange: (value: string) => void
  children: ReactNode
  disabled?: boolean
  ariaLabel?: string
}

export function ResourceSelectField({
  label,
  value,
  onChange,
  children,
  disabled = false,
  ariaLabel
}: ResourceSelectFieldProps) {
  return (
    <label className="ns-resource-select">
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
