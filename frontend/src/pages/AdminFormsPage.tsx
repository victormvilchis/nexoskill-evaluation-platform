import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { apiRequest, ApiRequestError } from '../shared/api/apiClient'
import { FilterToolbar } from '../shared/components/FilterToolbar'
import { Icon } from '../shared/components/Icon'
import { ResourceSearchField, ResourceSelectField } from '../shared/components/ResourceFilters'
import { TableActionLink, TableActions } from '../shared/components/TableActions'
import { TablePagination } from '../shared/components/TablePagination'
import { useDebouncedValue } from '../shared/hooks/useDebouncedValue'
import type { FormStatus, FormSummary } from '../shared/types/forms'
import { normalizePagedResponse, parsePage, parsePageSize, type PagedResponse, type PageSize } from '../shared/types/pagination'

const formStatusLabel: Record<FormStatus, string> = {
  DRAFT: 'Borrador', ACTIVE: 'Activo', DISABLED: 'Deshabilitado', CLOSED: 'Cerrado', ARCHIVED: 'Archivado'
}

function formatDate(value?: string) {
  if (!value) return 'Sin fecha'
  return new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium' }).format(new Date(value))
}

export function AdminFormsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [query, setQuery] = useState(searchParams.get('query') ?? '')
  const [status, setStatus] = useState(searchParams.get('status') ?? 'ACTIVE')
  const [mode, setMode] = useState(searchParams.get('mode') ?? '')
  const [data, setData] = useState<PagedResponse<FormSummary>>({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [reloadKey, setReloadKey] = useState(0)
  const debouncedQuery = useDebouncedValue(query, 300)
  const page = parsePage(searchParams.get('page'))
  const size = parsePageSize(searchParams.get('size'))
  const sort = searchParams.get('sort') ?? 'updatedAt'
  const direction = searchParams.get('direction') === 'ASC' ? 'ASC' : 'DESC'

  function updateUrl(patch: Record<string, string | number | undefined>, resetPage = false) {
    setSearchParams((current) => {
      const next = new URLSearchParams(current)
      Object.entries(patch).forEach(([key, value]) => {
        if (value === undefined || value === '' || (key === 'page' && Number(value) === 0) || (key === 'size' && Number(value) === 10)) next.delete(key)
        else next.set(key, String(value))
      })
      if (resetPage) next.delete('page')
      return next
    }, { replace: true })
  }

  useEffect(() => {
    const currentQuery = searchParams.get('query') ?? ''
    const currentStatus = searchParams.get('status') ?? 'ACTIVE'
    const currentMode = searchParams.get('mode') ?? ''
    if (currentQuery === debouncedQuery.trim() && currentStatus === status && currentMode === mode) return
    updateUrl({ query: debouncedQuery || undefined, status: status === 'ACTIVE' ? undefined : status, mode: mode || undefined }, true)
  }, [debouncedQuery, mode, searchParams, status])

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError('')
    const params = new URLSearchParams({ page: String(page), size: String(size), sort, direction })
    if (debouncedQuery.trim()) params.set('query', debouncedQuery.trim())
    if (status) params.set('status', status)
    if (mode) params.set('mode', mode)
    apiRequest<PagedResponse<FormSummary>>(`/admin/forms?${params.toString()}`, { signal: controller.signal })
      .then((response) => {
        const normalized = normalizePagedResponse(response)
        setData(normalized)
        if (normalized.totalPages > 0 && page >= normalized.totalPages) updateUrl({ page: normalized.totalPages - 1 })
      })
      .catch((requestError: unknown) => {
        if (controller.signal.aborted) return
        setError(requestError instanceof ApiRequestError ? requestError.message : 'No fue posible consultar los formularios.')
      })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [debouncedQuery, status, mode, page, size, sort, direction, reloadKey])

  const hasFilters = useMemo(() => Boolean(query || status !== 'ACTIVE' || mode), [query, status, mode])

  return (
    <main className="content-page resource-page ns-list-page">
      <header className="ns-page-header"><div><p className="eyebrow">Evaluaciones</p><h1>Formularios</h1><p className="muted">Diseña evaluaciones y prácticas reutilizando preguntas.</p></div><Link className="primary-button button-link" to="/admin/forms/new"><Icon name="plus" size={16} /> Nuevo formulario</Link></header>

      <FilterToolbar hasActiveFilters={hasFilters} onClear={() => { setQuery(''); setStatus('ACTIVE'); setMode(''); updateUrl({ query: undefined, status: undefined, mode: undefined, page: undefined }) }}>
        <ResourceSearchField value={query} onChange={setQuery} placeholder="Buscar por nombre o código" />
        <ResourceSelectField label="Modalidad" value={mode} onChange={setMode}><option value="">Todas</option><option value="ASSESSMENT">Evaluación</option><option value="PRACTICE">Práctica</option></ResourceSelectField>
        <ResourceSelectField label="Estado" value={status} onChange={setStatus}><option value="ACTIVE">Activo</option><option value="ALL">Todos</option><option value="DRAFT">Borrador</option><option value="DISABLED">Deshabilitado</option><option value="CLOSED">Cerrado</option><option value="ARCHIVED">Archivado</option></ResourceSelectField>
      </FilterToolbar>

      {error && <section className="inline-error-panel" role="alert"><div className="inline-error-icon"><Icon name="error" size={20} /></div><div><strong>No fue posible cargar los formularios</strong><p>{error}</p></div><button className="secondary-button compact-button" type="button" onClick={() => setReloadKey((value) => value + 1)}>Reintentar</button></section>}

      <section className="ns-data-panel" aria-busy={loading}>
        <div className="ns-data-table-wrap"><table className="ns-data-table">
          <thead><tr><th>Formulario</th><th>Modalidad</th><th>Aprobación</th><th>Contenido</th><th>Disponibilidad</th><th>Estado</th><th className="ns-actions-column">Acciones</th></tr></thead>
          <tbody>
            {loading && <tr><td colSpan={7} className="ns-table-empty">Cargando formularios…</td></tr>}
            {!loading && !error && data.content.length === 0 && <tr><td colSpan={7} className="ns-table-empty"><strong>{hasFilters ? 'No encontramos coincidencias' : 'Aún no hay formularios'}</strong><span>{hasFilters ? 'Ajusta o limpia los filtros.' : 'Crea el primer formulario para comenzar.'}</span></td></tr>}
            {!loading && !error && data.content.map((form) => <tr key={form.publicId}>
              <td className="ns-primary-cell"><strong>{form.title}</strong><small><code className="ns-code-label">{form.code}</code></small></td>
              <td>{form.modeCode === 'PRACTICE' ? 'Práctica' : 'Evaluación'}</td><td><strong>{form.passingScore}%</strong></td>
              <td><span>{form.sectionCount} {form.sectionCount === 1 ? 'sección' : 'secciones'}</span><small>{form.questionCount} {form.questionCount === 1 ? 'pregunta' : 'preguntas'}</small></td>
              <td><span>{form.startsAt ? `Desde ${formatDate(form.startsAt)}` : 'Inicio inmediato'}</span><small>{form.endsAt ? `Hasta ${formatDate(form.endsAt)}` : 'Sin fecha de cierre'}</small></td>
              <td><span className={`status-badge status-${form.status.toLowerCase()}`}>{formStatusLabel[form.status]}</span></td>
              <td><TableActions><TableActionLink to={`/admin/forms/${form.publicId}/edit`} label="Editar" icon="edit" tone="primary" /></TableActions></td>
            </tr>)}
          </tbody>
        </table></div>
        <TablePagination currentPage={data.page} pageSize={data.size} totalElements={data.totalElements} totalPages={data.totalPages} isLoading={loading} onPageChange={(nextPage) => updateUrl({ page: nextPage })} onPageSizeChange={(nextSize: PageSize) => updateUrl({ size: nextSize, page: undefined })} />
      </section>
    </main>
  )
}
