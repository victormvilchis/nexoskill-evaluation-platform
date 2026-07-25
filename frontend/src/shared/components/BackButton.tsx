import { useNavigate } from 'react-router-dom'

type BackButtonProps = {
  fallback: string
  label?: string
  className?: string
}

export function BackButton({ fallback, label = 'Volver', className = '' }: BackButtonProps) {
  const navigate = useNavigate()
  function goBack() {
    if (window.history.length > 1) navigate(-1)
    else navigate(fallback)
  }
  return (
    <button className={`ns-back-button ${className}`.trim()} type="button" onClick={goBack}>
      <span aria-hidden="true">←</span>
      <span>{label}</span>
    </button>
  )
}
