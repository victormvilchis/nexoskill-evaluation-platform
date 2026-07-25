import {
  Children,
  useCallback,
  useEffect,
  useId,
  useLayoutEffect,
  useRef,
  useState,
  type ButtonHTMLAttributes,
  type KeyboardEvent,
  type PropsWithChildren
} from 'react'
import { createPortal } from 'react-dom'
import { Link } from 'react-router-dom'
import { Icon, type IconName } from './Icon'

interface MenuPosition {
  left: number
  top: number
  width: number
}

const MENU_WIDTH = 208
const VIEWPORT_MARGIN = 10
const MENU_GAP = 6

function menuItems(menu: HTMLElement | null): HTMLElement[] {
  if (!menu) return []
  return Array.from(
    menu.querySelectorAll<HTMLElement>(
      '[role="menuitem"]:not([aria-disabled="true"]):not(:disabled)'
    )
  )
}

export function TableActions({ children }: PropsWithChildren) {
  const actionCount = Children.count(children)
  const menuId = useId()
  const triggerRef = useRef<HTMLButtonElement>(null)
  const menuRef = useRef<HTMLDivElement>(null)
  const [open, setOpen] = useState(false)
  const [position, setPosition] = useState<MenuPosition>({
    left: VIEWPORT_MARGIN,
    top: VIEWPORT_MARGIN,
    width: MENU_WIDTH
  })

  const closeMenu = useCallback((restoreFocus = false) => {
    setOpen(false)
    if (restoreFocus) {
      window.requestAnimationFrame(() => triggerRef.current?.focus())
    }
  }, [])

  const updatePosition = useCallback(() => {
    const trigger = triggerRef.current
    if (!trigger) return

    const triggerRect = trigger.getBoundingClientRect()
    const menuHeight = menuRef.current?.getBoundingClientRect().height ?? 0
    const width = Math.min(
      MENU_WIDTH,
      Math.max(160, window.innerWidth - VIEWPORT_MARGIN * 2)
    )

    const maximumLeft = Math.max(
      VIEWPORT_MARGIN,
      window.innerWidth - width - VIEWPORT_MARGIN
    )
    const left = Math.min(
      maximumLeft,
      Math.max(VIEWPORT_MARGIN, triggerRect.right - width)
    )

    const spaceBelow = window.innerHeight - triggerRect.bottom - VIEWPORT_MARGIN
    const spaceAbove = triggerRect.top - VIEWPORT_MARGIN
    const shouldOpenAbove = menuHeight > 0 && menuHeight > spaceBelow && spaceAbove > spaceBelow

    const preferredTop = shouldOpenAbove
      ? triggerRect.top - menuHeight - MENU_GAP
      : triggerRect.bottom + MENU_GAP
    const maximumTop = Math.max(
      VIEWPORT_MARGIN,
      window.innerHeight - menuHeight - VIEWPORT_MARGIN
    )

    setPosition({
      left,
      top: Math.min(maximumTop, Math.max(VIEWPORT_MARGIN, preferredTop)),
      width
    })
  }, [])

  useLayoutEffect(() => {
    if (!open) return

    updatePosition()
    const animationFrame = window.requestAnimationFrame(() => {
      updatePosition()
      menuItems(menuRef.current)[0]?.focus()
    })

    return () => window.cancelAnimationFrame(animationFrame)
  }, [open, updatePosition])

  useEffect(() => {
    if (!open) return

    const handlePointerDown = (event: PointerEvent) => {
      const target = event.target as Node
      if (triggerRef.current?.contains(target) || menuRef.current?.contains(target)) return
      closeMenu()
    }
    const handleViewportChange = () => updatePosition()

    document.addEventListener('pointerdown', handlePointerDown)
    window.addEventListener('resize', handleViewportChange)
    window.addEventListener('scroll', handleViewportChange, true)

    return () => {
      document.removeEventListener('pointerdown', handlePointerDown)
      window.removeEventListener('resize', handleViewportChange)
      window.removeEventListener('scroll', handleViewportChange, true)
    }
  }, [closeMenu, open, updatePosition])

  function handleTriggerKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    if (event.key === 'ArrowDown' || event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      setOpen(true)
    }
  }

  function handleMenuKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape') {
      event.preventDefault()
      closeMenu(true)
      return
    }

    const items = menuItems(menuRef.current)
    if (items.length === 0) return
    const currentIndex = items.indexOf(document.activeElement as HTMLElement)

    if (event.key === 'ArrowDown') {
      event.preventDefault()
      items[(currentIndex + 1 + items.length) % items.length]?.focus()
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      items[(currentIndex - 1 + items.length) % items.length]?.focus()
    } else if (event.key === 'Home') {
      event.preventDefault()
      items[0]?.focus()
    } else if (event.key === 'End') {
      event.preventDefault()
      items[items.length - 1]?.focus()
    } else if (event.key === 'Tab') {
      closeMenu()
    }
  }

  if (actionCount === 0) return null

  return (
    <div className="ns-table-actions">
      <button
        ref={triggerRef}
        aria-controls={menuId}
        aria-expanded={open}
        aria-haspopup="menu"
        className={`ns-table-actions-trigger${open ? ' is-open' : ''}`}
        type="button"
        onClick={() => setOpen((current) => !current)}
        onKeyDown={handleTriggerKeyDown}
      >
        <span>Acciones</span>
        <Icon name="chevronDown" size={14} />
      </button>

      {open && createPortal(
        <div
          ref={menuRef}
          aria-label="Acciones disponibles"
          className="ns-table-actions-menu"
          id={menuId}
          role="menu"
          style={{
            left: position.left,
            top: position.top,
            width: position.width
          }}
          onClick={() => closeMenu()}
          onKeyDown={handleMenuKeyDown}
        >
          {children}
        </div>,
        document.body
      )}
    </div>
  )
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
      className={`ns-table-action ns-table-action-${tone}`}
      role="menuitem"
      tabIndex={-1}
      to={to}
    >
      <Icon name={icon} size={16} />
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
  disabled,
  type = 'button',
  ...props
}: TableActionButtonProps) {
  return (
    <button
      aria-disabled={disabled ? 'true' : undefined}
      className={`ns-table-action ns-table-action-${tone} ${className}`.trim()}
      disabled={disabled}
      role="menuitem"
      tabIndex={-1}
      type={type}
      {...props}
    >
      <Icon name={icon} size={16} />
      <span>{label}</span>
    </button>
  )
}
