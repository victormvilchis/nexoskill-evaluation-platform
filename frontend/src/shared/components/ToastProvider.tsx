import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useRef,
  useState,
  type PropsWithChildren
} from 'react'
import { Icon, type IconName } from './Icon'

export type ToastTone = 'success' | 'error' | 'warning' | 'info'

interface ToastItem {
  id: number
  tone: ToastTone
  title: string
  message?: string
}

interface ToastContextValue {
  show: (tone: ToastTone, title: string, message?: string) => void
  success: (title: string, message?: string) => void
  error: (title: string, message?: string) => void
  warning: (title: string, message?: string) => void
  info: (title: string, message?: string) => void
}

const ToastContext = createContext<ToastContextValue | undefined>(undefined)

const toneIcon: Record<ToastTone, IconName> = {
  success: 'check',
  error: 'error',
  warning: 'warning',
  info: 'info'
}

export function ToastProvider({ children }: PropsWithChildren) {
  const [items, setItems] = useState<ToastItem[]>([])
  const sequence = useRef(0)

  const dismiss = useCallback((id: number) => {
    setItems((current) => current.filter((item) => item.id !== id))
  }, [])

  const show = useCallback(
    (tone: ToastTone, title: string, message?: string) => {
      const id = ++sequence.current
      setItems((current) => [...current.slice(-3), { id, tone, title, message }])
      window.setTimeout(() => dismiss(id), tone === 'error' ? 7000 : 4500)
    },
    [dismiss]
  )

  const value = useMemo<ToastContextValue>(
    () => ({
      show,
      success: (title, message) => show('success', title, message),
      error: (title, message) => show('error', title, message),
      warning: (title, message) => show('warning', title, message),
      info: (title, message) => show('info', title, message)
    }),
    [show]
  )

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="toast-viewport" aria-live="polite" aria-atomic="false">
        {items.map((item) => (
          <article className={`toast toast-${item.tone}`} key={item.id}>
            <div className="toast-icon"><Icon name={toneIcon[item.tone]} size={17} /></div>
            <div className="toast-content">
              <strong>{item.title}</strong>
              {item.message && <p>{item.message}</p>}
            </div>
            <button
              aria-label="Cerrar notificación"
              className="toast-close"
              type="button"
              onClick={() => dismiss(item.id)}
            >
              <Icon name="close" size={15} />
            </button>
          </article>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast() {
  const context = useContext(ToastContext)
  if (!context) throw new Error('useToast debe utilizarse dentro de ToastProvider')
  return context
}
