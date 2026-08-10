import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getDevelopmentHome } from '../features/development/api/developmentApi'
import type { HomeView } from '../features/development/types/development'
import { useStudentAuth } from '../features/students/context/StudentAuthContext'
import { CurrentFocus } from '../features/development/components/CurrentFocus'
import { PersonalHealthCheck, type HealthDimension } from '../features/development/components/PersonalHealthCheck'
import { StudyQuickActions } from '../features/development/components/StudyQuickActions'
import { PreparationBars } from '../features/development/components/PreparationBars'
import { RecentLearningActivity } from '../features/development/components/RecentLearningActivity'
import { DevelopmentStatus } from '../features/development/components/DevelopmentStatus'

export function StudentPortalPage() {
  const { student } = useStudentAuth()
  const navigate = useNavigate()
  const [home, setHome] = useState<HomeView | null>(null)
  const [error, setError] = useState<string | null>(null)
  useEffect(() => { let active = true; void getDevelopmentHome().then((value) => { if (active) setHome(value) }).catch((reason: unknown) => { if (active) setError(reason instanceof Error ? reason.message : 'No fue posible cargar Mi Desarrollo.') }); return () => { active = false } }, [])
  if (!student) return null
  if (error) return <section className="development-empty-state"><h1>No fue posible cargar Mi Desarrollo</h1><p>{error}</p><button className="development-primary" onClick={() => window.location.reload()}>Reintentar</button></section>
  if (!home) return <div className="development-loading" aria-label="Cargando Mi Desarrollo"><span/><span/><span/></div>
  const role = [home.profile.professionalProfile, home.profile.technologicalProfile].filter(Boolean).join(' · ')
  const tech = [home.profile.technology, home.profile.expertiseLevel].filter(Boolean).join(' · ')
  const nextEvaluation = home.evaluations.find((item) => item.status !== 'COMPLETED')
  const healthDimensions: HealthDimension[] = []
  if (home.certificationsEnabled) {
    const certificationPriority = ['PENDING_DEACTIVATION', 'EXPIRED', 'EXPIRING_SOON', 'IN_RULE'] as const
    const certificationLabels = { PENDING_DEACTIVATION: 'Pendiente de Baja', EXPIRED: 'Vencida', EXPIRING_SOON: 'Próxima a vencer', IN_RULE: 'En regla' }
    const certificationTone = { PENDING_DEACTIVATION: 'critical', EXPIRED: 'critical', EXPIRING_SOON: 'attention', IN_RULE: 'good' } as const
    const worst = certificationPriority.find((status) => home.certifications.some((item) => item.status === status))
    healthDimensions.push(worst
      ? { label: 'Certificaciones', value: certificationLabels[worst], tone: certificationTone[worst] }
      : { label: 'Certificaciones', value: 'Sin certificaciones aplicables', tone: 'neutral' })
  }
  const pendingEvaluations = home.evaluations.filter((item) => item.status === 'ASSIGNED' || item.status === 'IN_PROGRESS').length
  const pendingReview = home.evaluations.filter((item) => item.status === 'PENDING_REVIEW').length
  healthDimensions.push({ label: 'Evaluaciones', value: pendingEvaluations > 0 ? `${pendingEvaluations} ${pendingEvaluations === 1 ? 'pendiente' : 'pendientes'}` : pendingReview > 0 ? `${pendingReview} en revisión` : 'Al día', tone: pendingEvaluations > 0 ? 'attention' : 'good' })
  const learningInProgress = home.continueItem?.type === 'PRACTICE'
  const hasLearningActivity = home.preparation.length > 0 || home.recentActivity.some((item) => item.type.includes('PRACTICE'))
  healthDimensions.push({ label: 'Aprendizaje', value: learningInProgress ? 'En progreso' : hasLearningActivity ? 'Con actividad reciente' : 'Aún sin actividad', tone: learningInProgress || hasLearningActivity ? 'good' : 'neutral' })
  return <div className="development-home">
    <section className="development-intro"><div><p className="development-eyebrow">Mi Desarrollo</p><h1>Hola, {home.profile.firstName || student.firstName}</h1>{role && <p className="development-role">{role}</p>}{tech && <p className="development-tech">{tech}</p>}</div><div className="development-intro-note"><span>Hoy</span><strong>Avanza a tu ritmo.</strong><p>Tu información y actividades son personales.</p></div></section>
    <div className="development-priority-grid"><PersonalHealthCheck health={home.healthCheck} dimensions={healthDimensions}/><CurrentFocus focus={home.focus}/></div>
    {home.continueItem && <section className="development-continue"><div><p className="development-eyebrow">Continuar donde lo dejaste</p><h2>{home.continueItem.title}</h2><p>{home.continueItem.subtitle}</p>{home.continueItem.progressPercent != null && <div className="development-progress"><span style={{ width: `${home.continueItem.progressPercent}%` }}/></div>}</div><button className="development-primary" onClick={() => navigate(home.continueItem!.target)}>Continuar</button></section>}
    <StudyQuickActions />
    <div className="development-two-column">
      {home.certificationsEnabled && <section className="development-card"><div className="development-card-heading"><div><p className="development-eyebrow">Situación personal</p><h2>Mis certificaciones</h2></div><button className="development-link-button" onClick={() => navigate('/student/certifications')}>Ver todas</button></div>{home.certifications.length === 0 ? <p className="development-empty-inline">No tienes certificaciones aplicables registradas.</p> : <div className="development-list">{home.certifications.slice(0,4).map((item, index) => <div className="development-list-row" key={`${item.type}-${item.technology ?? ''}-${index}`}><div><strong>{item.label}{item.technology ? ` · ${item.technology}` : ''}</strong><small>{item.expirationDate ? `Vigencia: ${new Intl.DateTimeFormat('es-MX').format(new Date(`${item.expirationDate}T00:00:00`))}` : 'Sin vigencia aplicable'}</small></div><DevelopmentStatus value={item.status} label={item.statusLabel}/></div>)}</div>}</section>}
      <section className="development-card"><div className="development-card-heading"><div><p className="development-eyebrow">Siguiente paso</p><h2>Mis evaluaciones</h2></div><button className="development-link-button" onClick={() => navigate('/student/evaluations')}>Ver todas</button></div>{!nextEvaluation ? <p className="development-empty-inline">No tienes evaluaciones pendientes.</p> : <div className="development-evaluation-highlight"><span className="development-badge">{nextEvaluation.status === 'IN_PROGRESS' ? 'En progreso' : 'Pendiente'}</span><h3>{nextEvaluation.title}</h3><p>{nextEvaluation.questionCount} preguntas{nextEvaluation.durationMinutes ? ` · ${nextEvaluation.durationMinutes} min` : ''}</p><div className="development-actions"><button className="development-secondary" onClick={() => navigate('/student/study')}>Prepararme</button><button className="development-primary" onClick={() => navigate('/student/evaluations')}>{nextEvaluation.activeAttemptPublicId ? 'Continuar' : 'Comenzar'}</button></div></div>}</section>
    </div>
    <div className="development-two-column"><PreparationBars title="Mi preparación" items={home.preparation.slice(0,5)} empty="Aún no tienes suficiente actividad para calcular tu preparación. Realiza prácticas para construir esta vista."/><PreparationBars title="Te conviene reforzar" items={home.improvementAreas} empty="Todavía no hay evidencia suficiente para señalar áreas de refuerzo."/></div>
    {home.strengths.length > 0 && <PreparationBars title="Tus fortalezas" items={home.strengths} empty=""/>}
    {home.recommendations.length > 0 && <section className="development-section"><div className="development-section-heading"><div><p className="development-eyebrow">Siguiente acción</p><h2>Recomendaciones</h2></div></div><div className="recommendation-grid">{home.recommendations.map((item, index) => <article className={`recommendation-card ${item.severity.toLowerCase()}`} key={`${item.type}-${index}`}><strong>{item.title}</strong><p>{item.description}</p><button className="development-link-button" onClick={() => navigate(item.target)}>{item.actionLabel} →</button></article>)}</div></section>}
    <RecentLearningActivity items={home.recentActivity}/>
  </div>
}
