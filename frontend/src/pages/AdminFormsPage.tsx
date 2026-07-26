import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { apiClient } from '../shared/api/apiClient'
import { FilterToolbar } from '../shared/components/FilterToolbar'
import { Icon } from '../shared/components/Icon'
import {
  ResourceSearchField,
  ResourceSelectField
} from '../shared/components/ResourceFilters'
import { TableActionLink, TableActions } from '../shared/components/TableActions'
import { useDebouncedValue } from '../shared/hooks/useDebouncedValue'
import type { FormStatus, FormSummary } from '../shared/types/forms'

const formStatusLabel: Record<FormStatus, string> = {
  DRAFT: 'Borrador',
  ACTIVE: 'Activo',
  DISABLED: 'Deshabilitado',
  CLOSED: 'Cerrado',
  ARCHIVED: 'Archivado'
}

function formatDate(value?: string) {
  if (!value) return 'Sin fecha'
  return new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium' }).format(new Date(value))
}

export function AdminFormsPage() {
  const [items, setItems] = useState<FormSummary[]>([])
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState('ACTIVE')
  const [mode, setMode] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const debouncedQuery = useDebouncedValue(query, 250)

  async function load(selectedStatus = status) {
    setLoading(true)
    setError('')
    try {
      const backendStatus = selectedStatus || 'ALL'
      setItems(await apiClient.get<FormSummary[]>(`/api/v1/admin/forms?status=${encodeURIComponent(backendStatus)}`))
    } catch {
      setError('No fue posible consultar los formularios.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load(status)
  }, [status])

  const filtered = useMemo(() => items.filter((item) => {
    const term = debouncedQuery.trim().toLocaleLowerCase('es-MX')
    const matchesText = !term || `${item.title} ${item.code}`
      .toLocaleLowerCase('es-MX')
      .includes(term)
    return matchesText && (!status || item.status === status) && (!mode || item.modeCode === mode)
  }), [items, debouncedQuery, status, mode])

  const hasFilters = Boolean(query || status !== 'ACTIVE' || mode)

  return (
    <main className="content-page resource-page ns-list-page">
      <header className="ns-page-header">
        <div>
          <p className="eyebrow">Evaluaciones</p>
          <h1>Formularios</h1>
          <p className="muted">Diseña evaluaciones y prácticas reutilizando preguntas.</p>
        </div>
        <Link className="primary-button button-link" to="/admin/forms/new">
          <Icon name="plus" size={16} /> Nuevo formulario
        </Link>
      </header>

      <FilterToolbar
        resultLabel={`${filtered.length} ${filtered.length === 1 ? 'formulario' : 'formularios'}`}
        hasActiveFilters={hasFilters}
        onClear={() => {
          setQuery('')
          setStatus('ACTIVE')
          setMode('')
        }}
      >
        <ResourceSearchField
          value={query}
          onChange={setQuery}
          placeholder="Buscar por nombre o código"
        />
        <ResourceSelectField label="Modalidad" value={mode} onChange={setMode}>
          <option value="">Todas</option>
          <option value="ASSESSMENT">Evaluación</option>
          <option value="PRACTICE">Práctica</option>
        </ResourceSelectField>
        <ResourceSelectField label="Estado" value={status} onChange={setStatus}>
          <option value="ACTIVE">Activo</option>
          <option value="">Todos</option>
          <option value="DRAFT">Borrador</option>
          <option value="DISABLED">Deshabilitado</option>
          <option value="CLOSED">Cerrado</option>
          <option value="ARCHIVED">Archivado</option>
        </ResourceSelectField>
      </FilterToolbar>

      {error && (
        <section className="inline-error-panel" role="alert">
          <div className="inline-error-icon"><Icon name="error" size={20} /></div>
          <div><strong>No fue posible cargar los formularios</strong><p>{error}</p></div>
          <button className="secondary-button compact-button" onClick={() => void load()}>Reintentar</button>
        </section>
      )}

      <section className="ns-data-panel" aria-busy={loading}>
        <div className="ns-data-table-wrap">
          <table className="ns-data-table">
            <thead>
              <tr>
                <th>Formulario</th>
                <th>Modalidad</th>
                <th>Aprobación</th>
                <th>Contenido</th>
                <th>Disponibilidad</th>
                <th>Estado</th>
                <th className="ns-actions-column">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {loading && <tr><td colSpan={7} className="ns-table-empty">Cargando formularios…</td></tr>}
              {!loading && filtered.length === 0 && (
                <tr>
                  <td colSpan={7} className="ns-table-empty">
                    <strong>{hasFilters ? 'No encontramos coincidencias' : 'Aún no hay formularios'}</strong>
                    <span>{hasFilters ? 'Ajusta o limpia los filtros.' : 'Crea el primer formulario para comenzar.'}</span>
                  </td>
                </tr>
              )}
              {!loading && filtered.map((form) => (
                <tr key={form.publicId}>
                  <td className="ns-primary-cell">
                    <strong>{form.title}</strong>
                    <small><code className="ns-code-label">{form.code}</code></small>
                  </td>
                  <td>{form.modeCode === 'PRACTICE' ? 'Práctica' : 'Evaluación'}</td>
                  <td><strong>{form.passingScore}%</strong></td>
                  <td>
                    <span>{form.sectionCount} {form.sectionCount === 1 ? 'sección' : 'secciones'}</span>
                    <small>{form.questionCount} {form.questionCount === 1 ? 'pregunta' : 'preguntas'}</small>
                  </td>
                  <td>
                    <span>{form.startsAt ? `Desde ${formatDate(form.startsAt)}` : 'Inicio inmediato'}</span>
                    <small>{form.endsAt ? `Hasta ${formatDate(form.endsAt)}` : 'Sin fecha de cierre'}</small>
                  </td>
                  <td>
                    <span className={`status-badge status-${form.status.toLowerCase()}`}>
                      {formStatusLabel[form.status]}
                    </span>
                  </td>
                  <td>
                    <TableActions>
                      <TableActionLink
                        to={`/admin/forms/${form.publicId}/edit`}
                        label="Editar"
                        icon="edit"
                        tone="primary"
                      />
                    </TableActions>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </main>
  )
}
