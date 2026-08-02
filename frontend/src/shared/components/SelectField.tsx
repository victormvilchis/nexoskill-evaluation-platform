import {
  Children,
  isValidElement,
  useCallback,
  useEffect,
  useId,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
  type KeyboardEvent as ReactKeyboardEvent,
  type ReactNode
} from 'react'
import { createPortal } from 'react-dom'
import { Icon } from './Icon'

export interface SelectOption {
  value: string
  label: ReactNode
  disabled?: boolean
}

interface SelectFieldProps {
  value: string
  onChange: (value: string) => void
  options?: SelectOption[]
  children?: ReactNode
  id?: string
  name?: string
  className?: string
  disabled?: boolean
  required?: boolean
  ariaInvalid?: boolean
  ariaLabel?: string
  placeholder?: string
}

interface NativeOptionProps {
  value?: string | number
  disabled?: boolean
  children?: ReactNode
}

function optionsFromChildren(children: ReactNode): SelectOption[] {
  const result: SelectOption[] = []
  Children.forEach(children, (child) => {
    if (!isValidElement<NativeOptionProps>(child)) return
    if (child.type === 'option') {
      result.push({
        value: String(child.props.value ?? ''),
        label: child.props.children,
        disabled: child.props.disabled
      })
      return
    }
    if (child.type === 'optgroup') {
      Children.forEach(child.props.children, (nested) => {
        if (!isValidElement<NativeOptionProps>(nested) || nested.type !== 'option') return
        result.push({
          value: String(nested.props.value ?? ''),
          label: nested.props.children,
          disabled: nested.props.disabled
        })
      })
    }
  })
  return result
}

function nextEnabledIndex(options: SelectOption[], start: number, direction: 1 | -1) {
  if (options.length === 0) return -1
  for (let offset = 1; offset <= options.length; offset += 1) {
    const candidate = (start + (offset * direction) + options.length) % options.length
    if (!options[candidate]?.disabled) return candidate
  }
  return -1
}

function edgeEnabledIndex(options: SelectOption[], direction: 1 | -1) {
  const start = direction === 1 ? -1 : options.length
  return nextEnabledIndex(options, start, direction)
}

export function SelectField({
  value,
  onChange,
  options: explicitOptions,
  children,
  id,
  name,
  className = '',
  disabled = false,
  required = false,
  ariaInvalid = false,
  ariaLabel = 'Seleccionar opción',
  placeholder = 'Seleccionar'
}: SelectFieldProps) {
  const generatedId = useId()
  const controlId = id ?? `vt-select-${generatedId.replace(/:/g, '')}`
  const triggerRef = useRef<HTMLButtonElement>(null)
  const popoverRef = useRef<HTMLDivElement>(null)
  const [open, setOpen] = useState(false)
  const [activeIndex, setActiveIndex] = useState(-1)
  const [position, setPosition] = useState<CSSProperties>({})

  const options = useMemo(
    () => explicitOptions ?? optionsFromChildren(children),
    [children, explicitOptions]
  )
  const selectedIndex = options.findIndex((option) => option.value === value)
  const selectedOption = selectedIndex >= 0 ? options[selectedIndex] : undefined
  const displayLabel = selectedOption?.label ?? placeholder
  const placeholderSelected = !selectedOption || (required && selectedOption.value === '')

  const focusOption = useCallback((index: number) => {
    if (index < 0) return
    popoverRef.current?.querySelector<HTMLButtonElement>(`[data-option-index=\"${index}\"]`)?.focus()
  }, [])

  const closeAndFocus = useCallback((restoreFocus: boolean) => {
    setOpen(false)
    if (restoreFocus) window.requestAnimationFrame(() => triggerRef.current?.focus())
  }, [])

  useEffect(() => {
    if (disabled) setOpen(false)
  }, [disabled])

  useEffect(() => {
    if (!open) return
    const initialIndex = selectedIndex >= 0 && !options[selectedIndex]?.disabled
      ? selectedIndex
      : edgeEnabledIndex(options, 1)
    setActiveIndex(initialIndex)
    window.requestAnimationFrame(() => focusOption(initialIndex))
  }, [focusOption, open, options, selectedIndex])

  useEffect(() => {
    if (!open) return

    const updatePosition = () => {
      const trigger = triggerRef.current
      if (!trigger) return
      const rect = trigger.getBoundingClientRect()
      const viewportPadding = 12
      const availableWidth = Math.max(220, window.innerWidth - viewportPadding * 2)
      const width = Math.min(Math.max(rect.width, Math.min(320, availableWidth)), availableWidth)
      const left = Math.max(viewportPadding, Math.min(rect.left, window.innerWidth - width - viewportPadding))
      const estimatedHeight = Math.min(360, Math.max(94, options.length * 48 + 16))
      const below = window.innerHeight - rect.bottom - viewportPadding
      const above = rect.top - viewportPadding
      const openAbove = below < Math.min(estimatedHeight, 230) && above > below
      const viewportHeight = Math.max(96, window.innerHeight - viewportPadding * 2)
      const availableSpace = openAbove ? above - 8 : below - 8
      const maxHeight = Math.min(360, viewportHeight, Math.max(96, availableSpace))
      const top = openAbove
        ? Math.max(viewportPadding, rect.top - Math.min(estimatedHeight, maxHeight) - 8)
        : Math.max(viewportPadding, Math.min(rect.bottom + 8, window.innerHeight - viewportPadding - Math.min(estimatedHeight, maxHeight)))
      setPosition({ width, left, top, maxHeight })
    }

    const handlePointerDown = (event: PointerEvent) => {
      const target = event.target as Node
      if (triggerRef.current?.contains(target) || popoverRef.current?.contains(target)) return
      closeAndFocus(false)
    }

    const handleEscape = (event: globalThis.KeyboardEvent) => {
      if (event.key !== 'Escape') return
      event.preventDefault()
      closeAndFocus(true)
    }

    updatePosition()
    window.addEventListener('resize', updatePosition)
    window.addEventListener('scroll', updatePosition, true)
    document.addEventListener('pointerdown', handlePointerDown)
    document.addEventListener('keydown', handleEscape)
    return () => {
      window.removeEventListener('resize', updatePosition)
      window.removeEventListener('scroll', updatePosition, true)
      document.removeEventListener('pointerdown', handlePointerDown)
      document.removeEventListener('keydown', handleEscape)
    }
  }, [closeAndFocus, open, options.length])

  function choose(option: SelectOption, index: number) {
    if (option.disabled) return
    if (option.value !== value) onChange(option.value)
    setActiveIndex(index)
    closeAndFocus(true)
  }

  function handleTriggerKeyDown(event: ReactKeyboardEvent<HTMLButtonElement>) {
    if (disabled) return
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp' || event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      setOpen(true)
    }
  }

  function handleListKeyDown(event: ReactKeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Tab') {
      setOpen(false)
      return
    }
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      const option = options[activeIndex]
      if (option) choose(option, activeIndex)
      return
    }
    let next = activeIndex
    if (event.key === 'ArrowDown') next = nextEnabledIndex(options, activeIndex, 1)
    else if (event.key === 'ArrowUp') next = nextEnabledIndex(options, activeIndex, -1)
    else if (event.key === 'Home') next = edgeEnabledIndex(options, 1)
    else if (event.key === 'End') next = edgeEnabledIndex(options, -1)
    else return
    event.preventDefault()
    if (next >= 0) {
      setActiveIndex(next)
      focusOption(next)
    }
  }

  return (
    <div className={`vt-select-field${className ? ` ${className}` : ''}`}>
      <button
        ref={triggerRef}
        id={controlId}
        type="button"
        className="vt-select-trigger"
        aria-label={ariaLabel}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-invalid={ariaInvalid || undefined}
        aria-required={required || undefined}
        disabled={disabled}
        onClick={() => setOpen((current) => !current)}
        onKeyDown={handleTriggerKeyDown}
      >
        <span className={placeholderSelected ? 'vt-select-placeholder' : 'vt-select-value'}>{displayLabel}</span>
        <Icon name="chevronDown" size={16} />
      </button>
      <input
        className="vt-select-validation-proxy"
        tabIndex={-1}
        aria-hidden="true"
        name={name}
        value={value}
        required={required}
        disabled={disabled}
        onChange={() => undefined}
        onFocus={() => triggerRef.current?.focus()}
        onInvalid={(event) => {
          event.preventDefault()
          triggerRef.current?.focus()
        }}
      />
      {open && createPortal(
        <div
          ref={popoverRef}
          className="vt-select-popover"
          role="listbox"
          aria-label={ariaLabel}
          aria-activedescendant={activeIndex >= 0 ? `${controlId}-option-${activeIndex}` : undefined}
          style={position}
          onKeyDown={handleListKeyDown}
        >
          {options.map((option, index) => (
            <button
              id={`${controlId}-option-${index}`}
              data-option-index={index}
              key={`${option.value}-${index}`}
              type="button"
              role="option"
              aria-selected={option.value === value}
              disabled={option.disabled}
              className={[
                'vt-select-option',
                option.value === value ? 'selected' : '',
                index === activeIndex ? 'active' : ''
              ].filter(Boolean).join(' ')}
              tabIndex={index === activeIndex ? 0 : -1}
              onFocus={() => setActiveIndex(index)}
              onMouseMove={() => !option.disabled && setActiveIndex(index)}
              onClick={() => choose(option, index)}
            >
              <span>{option.label}</span>
              {option.value === value && <Icon name="check" size={16} />}
            </button>
          ))}
          {options.length === 0 && <p className="vt-select-empty">No hay opciones disponibles.</p>}
        </div>,
        document.body
      )}
    </div>
  )
}

