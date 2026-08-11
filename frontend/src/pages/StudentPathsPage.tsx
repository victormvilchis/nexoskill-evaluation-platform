import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { getMyPath, getMyPaths } from '../features/development/api/developmentApi'
import type { PathCard, PathStage } from '../features/development/types/development'
import { useToast } from '../shared/components/ToastProvider'

const STAGE_LABELS: Record<PathStage['state'], string> = {
  COMPLETED: 'Completada',
  IN_PROGRESS: 'En progreso',
  PENDING: 'Pendiente',
  EMPTY: 'Sin evaluaciones',
  UNAVAILABLE: 'No disponible'
}

function stageSymbol(state: PathStage['state']) {
  if (state === 'COMPLETED') return '✓'
  if (state === 'IN_PROGRESS') return '●'
  return '○'
}

function PathSummaryCard({ path, onOpen }: { path: PathCard; onOpen: () => void }) {
  return <article className={`student-path-card ${path.status.toLowerCase()}`}>
    <div className="student-path-card-top">
      <div><span className={`development-badge ${path.status.toLowerCase()}`}>{path.status === 'COMPLETED' ? 'Completado' : path.status === 'IN_PROGRESS' ? 'En progreso' : path.status === 'EMPTY' ? 'Sin contenido evaluable' : 'Asignado'}</span><h2>{path.name}</h2></div>
      <button className="development-link-button" type="button" onClick={onOpen}>Ver recorrido →</button>
    </div>
    {path.description && <p>{path.description}</p>}
    <div className="student-path-progress-summary"><strong>{path.completedStages} de {path.totalStages} etapas completadas</strong>{path.nextStageTitle && <span>Siguiente: {path.nextStageTitle}</span>}</div>
    {path.progressPercent != null && <div className="development-progress" aria-label={`${path.progressPercent}% completado`}><span style={{ width: `${path.progressPercent}%` }} /></div>}
    {path.nextEvaluationAssignmentPublicId && <button className="development-primary" type="button" onClick={() => onOpen()}>Continuar Path</button>}
  </article>
}

export function StudentPathsPage() {
  const navigate = useNavigate()
  const toast = useToast()
  const { assignmentPublicId } = useParams()
  const [items, setItems] = useState<PathCard[]>([])
  const [detail, setDetail] = useState<PathCard | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string>()

  const load = useCallback(async () => {
    setLoading(true)
    setError(undefined)
    try {
      if (assignmentPublicId) {
        setDetail(await getMyPath(assignmentPublicId))
      } else {
        const response = await getMyPaths()
        setItems(Array.isArray(response) ? response : [])
      }
    } catch (reason) {
      const message = reason instanceof Error ? reason.message : 'No fue posible cargar tus Paths.'
      setError(message)
      toast.error('No fue posible cargar tus Paths', message)
    } finally {
      setLoading(false)
    }
  }, [assignmentPublicId, toast])

  useEffect(() => { void load() }, [load])

  if (loading) return <div className="development-loading" aria-label="Cargando Mis Paths"><span/><span/><span/></div>

  if (error) return <section className="development-empty-state" role="alert"><h1>No fue posible cargar tus Paths</h1><p>{error}</p><div className="development-actions"><button className="development-secondary" type="button" onClick={() => navigate('/student')}>Volver a Mi Desarrollo</button><button className="development-primary" type="button" onClick={() => void load()}>Reintentar</button></div></section>

  if (assignmentPublicId && detail) {
    return <div className="development-page student-path-detail">
      <header className="development-page-heading"><button className="development-link-button" type="button" onClick={() => navigate('/student/paths')}>← Mis Paths</button><p className="development-eyebrow">Ruta de desarrollo</p><h1>{detail.name}</h1>{detail.description && <p>{detail.description}</p>}<div className="student-path-progress-summary"><strong>{detail.completedStages} de {detail.totalStages} etapas completadas</strong>{detail.nextStageTitle && <span>Siguiente: {detail.nextStageTitle}</span>}</div>{detail.progressPercent != null && <div className="development-progress"><span style={{ width: `${detail.progressPercent}%` }} /></div>}</header>
      {detail.stages.length === 0 ? <section className="development-empty-state path-empty"><span className="path-empty-icon" aria-hidden="true">⌁</span><h2>Este Path todavía no tiene Colecciones</h2><p>La asignación es válida, pero todavía no existe contenido que recorrer.</p></section> : <section className="student-path-route" aria-label={`Recorrido de ${detail.name}`}>{detail.stages.map((stage, index) => <article className={`student-path-stage ${stage.state.toLowerCase()}`} key={stage.collectionPublicId}><div className="student-path-stage-marker" aria-hidden="true"><span>{stageSymbol(stage.state)}</span>{index < detail.stages.length - 1 && <i />}</div><div className="student-path-stage-content"><div><small>Etapa {stage.order}</small><h2>{stage.title}</h2><span className={`development-badge ${stage.state.toLowerCase()}`}>{STAGE_LABELS[stage.state]}</span></div><p>{stage.totalForms === 0 ? 'Esta Colección no contiene evaluaciones activas.' : `${stage.completedForms} de ${stage.totalForms} ${stage.totalForms === 1 ? 'evaluación completada' : 'evaluaciones completadas'}`}</p>{stage.nextEvaluationAssignmentPublicId && <button className="development-primary" type="button" onClick={() => navigate('/student/evaluations')}>Continuar</button>}</div></article>)}</section>}
    </div>
  }

  return <div className="development-page"><header className="development-page-heading"><p className="development-eyebrow">Ruta de desarrollo</p><h1>Mis Paths</h1><p>Consulta las rutas que te fueron asignadas y avanza utilizando las evaluaciones reales de sus Colecciones.</p></header>{items.length === 0 ? <section className="development-empty-state path-empty"><span className="path-empty-icon" aria-hidden="true">⌁</span><h2>Actualmente no tienes Paths asignados.</h2><p>Esto es un estado normal. Puedes continuar utilizando tus demás actividades de Mi Desarrollo.</p><button className="development-primary" type="button" onClick={() => navigate('/student/study')}>Continuar estudiando</button></section> : <div className="student-path-grid">{items.map((path) => <PathSummaryCard key={path.assignmentPublicId} path={path} onOpen={() => navigate(`/student/paths/${path.assignmentPublicId}`)} />)}</div>}</div>
}
