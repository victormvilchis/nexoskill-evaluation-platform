import { useNavigate } from 'react-router-dom'
import type { CurrentFocus as FocusType } from '../types/development'
export function CurrentFocus({ focus }: { focus: FocusType }) {
  const navigate = useNavigate()
  return <section className={`development-focus development-focus-${focus.severity.toLowerCase()}`}>
    <p className="development-eyebrow">Tu foco ahora</p>
    <h2>{focus.title}</h2><p>{focus.description}</p>
    <div className="development-actions">{focus.actions.map((action, index) => <button key={`${action.action}-${index}`} className={index === 0 ? 'development-primary' : 'development-secondary'} onClick={() => navigate(action.target)}>{action.label}</button>)}</div>
  </section>
}
