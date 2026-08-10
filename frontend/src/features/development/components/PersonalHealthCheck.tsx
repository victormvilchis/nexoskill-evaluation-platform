import type { HealthCheck } from '../types/development'
import { DevelopmentStatus } from './DevelopmentStatus'

export interface HealthDimension {
  label: string
  value: string
  tone?: 'good' | 'attention' | 'critical' | 'neutral'
}

export function PersonalHealthCheck({ health, dimensions }: { health: HealthCheck; dimensions: HealthDimension[] }) {
  return <section className={`development-health development-health-${health.level.toLowerCase()}`}>
    <div><p className="development-eyebrow">Mi Health Check</p><h2>{health.label}</h2><p>{health.description}</p></div>
    {dimensions.length > 0 && <div className="development-health-dimensions" aria-label="Resumen de mi situación">
      {dimensions.map((item) => <div key={item.label} className={`health-dimension ${item.tone ?? 'neutral'}`}><span>{item.label}</span><strong>{item.value}</strong></div>)}
    </div>}
    <DevelopmentStatus value={health.level} label={health.label} />
  </section>
}
