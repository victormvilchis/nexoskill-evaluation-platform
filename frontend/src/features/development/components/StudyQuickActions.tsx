import { useNavigate } from 'react-router-dom'
const actions = [
  ['QUICK', 'Práctica rápida', 'Practica algunas preguntas en pocos minutos.'],
  ['SIMULATOR', 'Simulador', 'Prepárate en una experiencia similar a una evaluación.'],
  ['REVIEW_ERRORS', 'Repasar errores', 'Refuerza preguntas donde anteriormente tuviste dificultades.'],
  ['TOPIC', 'Estudiar por tema', 'Elige qué contenido quieres reforzar.']
] as const
export function StudyQuickActions({ reviewErrors }: { reviewErrors?: number }) {
  const navigate = useNavigate()
  return <section className="development-section"><div className="development-section-heading"><div><p className="development-eyebrow">Estudiar</p><h2>¿Qué quieres hacer hoy?</h2></div></div>
    <div className="study-action-grid">{actions.map(([mode, title, description]) => <button key={mode} className="study-action-card" onClick={() => navigate(`/student/study?mode=${mode}`)}>
      <span className="study-action-icon" aria-hidden="true">{mode === 'QUICK' ? '↗' : mode === 'SIMULATOR' ? '◎' : mode === 'REVIEW_ERRORS' ? '↻' : '⌁'}</span>
      <strong>{title}</strong><span>{description}</span>{mode === 'REVIEW_ERRORS' && reviewErrors ? <small>{reviewErrors} preguntas por reforzar</small> : null}
    </button>)}</div>
  </section>
}
