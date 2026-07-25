import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { searchQuestions } from '../features/questions/api/questionApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { Icon } from '../shared/components/Icon'
import type { QuestionPage, QuestionStatus } from '../shared/types/questions'

const statusLabel: Record<QuestionStatus, string> = {
  ACTIVE: 'Activa',
  ARCHIVED: 'Archivada',
  DELETED: 'Eliminada'
}

export function AdminQuestionsPage() {
  const [data, setData] = useState<QuestionPage>()
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState<QuestionStatus | ''>('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string>()
  const [reloadKey, setReloadKey] = useState(0)
  const reload = useCallback(() => setReloadKey((value) => value + 1), [])

  useEffect(() => {
    const controller = new AbortController()
    const timer = window.setTimeout(() => {
      setLoading(true); setError(undefined)
      searchQuestions({ query: query.trim() || undefined, status, size: 50, signal: controller.signal })
        .then(setData)
        .catch((requestError: unknown) => {
          if (!controller.signal.aborted) setError(requestError instanceof ApiRequestError
            ? requestError.message : 'No fue posible consultar las preguntas.')
        })
        .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    }, 250)
    return () => { window.clearTimeout(timer); controller.abort() }
  }, [query, status, reloadKey])

  const questions = data?.content ?? []
  return <main className="content-page resource-page">
    <div className="page-heading resource-heading">
      <div><p className="eyebrow">Contenido</p><h1>Banco de preguntas</h1>
        <p className="muted">Preguntas reutilizables organizadas por categorías y colecciones.</p></div>
      <div className="heading-actions resource-heading-actions">
        <Link className="secondary-button button-link" to="/admin/question-collections"><Icon name="collections" size={16}/>Colecciones</Link>
        <Link className="primary-button button-link" to="/admin/questions/new"><Icon name="plus" size={16}/>Nueva pregunta</Link>
      </div>
    </div>
    <section className="resource-toolbar" aria-label="Filtros de preguntas">
      <label className="resource-search"><Icon name="search" size={18}/><input value={query}
        onChange={(event)=>setQuery(event.target.value)} placeholder="Buscar por enunciado o categoría"/>
        {query&&<button aria-label="Limpiar búsqueda" className="resource-search-clear" type="button" onClick={()=>setQuery('')}><Icon name="close" size={15}/></button>}</label>
      <label className="compact-filter"><span>Estado</span><select value={status} onChange={(event)=>setStatus(event.target.value as QuestionStatus|'')}>
        <option value="">Disponibles</option><option value="ACTIVE">Activas</option><option value="ARCHIVED">Archivadas</option><option value="DELETED">Eliminadas</option>
      </select></label>
      <span className="resource-total"><strong>{data?.totalElements??0}</strong>{data?.totalElements===1?' pregunta':' preguntas'}</span>
    </section>
    {error&&<section className="inline-error-panel" role="alert"><div className="inline-error-icon"><Icon name="error" size={20}/></div>
      <div><strong>No fue posible cargar el banco de preguntas</strong><p>{error}</p></div>
      <button className="secondary-button compact-button" onClick={reload}>Reintentar</button></section>}
    {loading&&!data?<div className="resource-card-grid">{Array.from({length:6},(_,index)=><div className="resource-card resource-card-skeleton" key={index}/>)}</div>:
      <div className="resource-card-grid">{questions.map(item=><Link className={`resource-card question-resource-card ${item.status==='DELETED'?'resource-card-deleted':''}`}
        to={`/admin/questions/${item.publicId}`} key={item.publicId}>
        <div className="resource-card-header"><span className={`status-badge status-${item.status.toLowerCase()}`}>{statusLabel[item.status]}</span><small>{item.typeName}</small></div>
        <h2>{item.statement}</h2><div className="chip-row">{item.categories.map(category=><span className="category-chip" key={category.publicId}>{category.name}</span>)}</div>
        <footer><span>{item.difficultyName}</span>{item.hasMedia&&<span><Icon name="image" size={14}/> Multimedia</span>}</footer></Link>)}</div>}
    {!loading&&!error&&questions.length===0&&<section className="empty-state-card resource-empty-state"><Icon name="questions" size={26}/>
      <h2>{status==='DELETED'?'No hay preguntas eliminadas':query?'No encontramos coincidencias':'Aún no hay preguntas'}</h2>
      <p>{query?'Prueba con otro término de búsqueda.':'Crea la primera pregunta para comenzar a construir tu banco.'}</p></section>}
  </main>
}
