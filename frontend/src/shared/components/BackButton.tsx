import { useNavigate } from 'react-router-dom'
import { Icon } from './Icon'

type BackButtonProps = {
  fallback: string
  label?: string
  className?: string
}

export function BackButton({ fallback, label = 'Regresar', className = '' }: BackButtonProps) {
  const navigate = useNavigate()

  function goBack() {
    const historyIndex = window.history.state?.idx
    if (typeof historyIndex === 'number' && historyIndex > 0) navigate(-1)
    else navigate(fallback)
  }

  return (
    <button
      aria-label={label}
      className={`ns-back-button ${className}`.trim()}
      type="button"
      onClick={goBack}
    >
      <Icon name="chevronLeft" size={17} />
      <span>{label}</span>
    </button>
  )
}
