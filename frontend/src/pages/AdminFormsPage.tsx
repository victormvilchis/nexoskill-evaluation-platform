import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { apiClient } from '../shared/api/apiClient'
import { FilterToolbar } from '../shared/components/FilterToolbar'
import { useDebouncedValue } from '../shared/hooks/useDebouncedValue'
import type { FormSummary } from '../shared/types/forms'

export function AdminFormsPage() {
  const [items, setItems] = useState<FormSummary[]>([])
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState('')
  const [mode, setMode] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const debouncedQuery = useDebouncedValue(query, 250)

  async function load() {
    setLoading(true); setError('')
    try { setItems(await apiClient.get<FormSummary[]>('/api/v1/admin/forms')) }
    catch { setError('No fue posible consultar los formularios.') }
    finally { setLoading(false) }
  }
  useEffect(() => { void load() }, [])

  const filtered = useMemo(() => items.filter(item => {
    const term = debouncedQuery.trim().toLocaleLowerCase('es-MX')
    const matchesText = !term || `${item.title} ${item.code}`.toLocaleLowerCase('es-MX').includes(term)
    return matchesText && (!status || item.status === status) && (!mode || item.modeCode === mode)
  }), [items, debouncedQuery, status, mode])
  const hasFilters = Boolean(query || status || mode)

  return <main className="content-page resource-page">
    <header className="ns-page-header"><div><p className="eyebrow">Evaluaciones</p><h1>Formularios</h1><p className="muted">Diseña evaluaciones y prácticas reutilizando preguntas y colecciones.</p></div><Link className="primary-button button-link" to="/admin/forms/new">+ Nuevo formulario</Link></header>
    <FilterToolbar resultLabel={`${filtered.length} ${filtered.length === 1 ? 'formulario' : 'formularios'}`} hasActiveFilters={hasFilters} onClear={() => { setQuery(''); setStatus(''); setMode('') }}>
      <label className="ns-search-field"><span aria-hidden="true">⌕</span><input value={query} onChange={e => setQuery(e.target.value)} placeholder="Buscar por nombre o código" />{query && <button type="button" onClick={() => setQuery('')} aria-label="Limpiar búsqueda">×</button>}</label>
      <label className="ns-filter-field"><span>Modalidad</span><select value={mode} onChange={e => setMode(e.target.value)}><option value="">Todas</option><option value="ASSESSMENT">Evaluación</option><option value="PRACTICE">Práctica</option></select></label>
      <label className="ns-filter-field"><span>Estado</span><select value={status} onChange={e => setStatus(e.target.value)}><option value="">Todos</option><option value="DRAFT">Borrador</option><option value="ACTIVE">Activo</option><option value="DISABLED">Deshabilitado</option><option value="CLOSED">Cerrado</option><option value="ARCHIVED">Archivado</option></select></label>
    </FilterToolbar>
    {error && <div className="ns-inline-alert"><strong>No fue posible cargar los formularios</strong><span>{error}</span><button className="secondary-button" onClick={() => void load()}>Reintentar</button></div>}
    {loading ? <div className="ns-loading-card">Cargando formularios…</div> : filtered.length === 0 ? <section className="empty-state-card resource-empty-state"><h2>{hasFilters ? 'No encontramos coincidencias' : 'Aún no hay formularios'}</h2><p>{hasFilters ? 'Ajusta o limpia los filtros.' : 'Crea el primer formulario para comenzar.'}</p>{!hasFilters && <Link className="primary-button button-link" to="/admin/forms/new">Crear formulario</Link>}</section> : <div className="ns-form-card-grid">{filtered.map(form => <Link className="ns-form-card" key={form.publicId} to={`/admin/forms/${form.publicId}/edit`}><div className="ns-form-card-top"><span className={`status-badge status-${form.status.toLowerCase()}`}>{form.status}</span><span className="ns-mode-badge">{form.modeCode === 'PRACTICE' ? 'Práctica' : 'Evaluación'}</span></div><h2>{form.title}</h2><p>{form.code}</p><footer><span>Aprobación: <strong>{form.passingScore}%</strong></span><span>Editar →</span></footer></Link>)}</div>}
  </main>
}
