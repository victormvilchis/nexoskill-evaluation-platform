import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { searchOrganizations } from '../features/organizations/api/organizationApi'
import type { OrganizationStatus, OrganizationSummary } from '../features/organizations/types/organizations'
import { useDebouncedValue } from '../shared/hooks/useDebouncedValue'
import { TableActionLink, TableActions } from '../shared/components/TableActions'

const statusLabel: Record<OrganizationStatus, string> = {
  ACTIVE: 'Activa', INACTIVE: 'Inactiva', SUSPENDED: 'Suspendida', EXPIRED: 'Vencida', DELETED: 'Eliminada'
}

export function AdminOrganizationsPage() {
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState<OrganizationStatus | 'ALL'>('ALL')
  const [items, setItems] = useState<OrganizationSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const debouncedQuery = useDebouncedValue(query, 300)

  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    searchOrganizations({ query: debouncedQuery, status })
      .then(result => { if (active) setItems(result.content) })
      .catch(() => { if (active) setError('No fue posible consultar las organizaciones.') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [debouncedQuery, status])

  return <main className="ns-resource-page org-page">
    <header className="ns-page-header">
      <div><span className="eyebrow">ADMINISTRACIÓN</span><h1>Organizaciones</h1><p>Administra tenants, vigencia, contenido y configuración comercial.</p></div>
      <Link className="primary-button" to="/admin/organizations/new">+ Nueva organización</Link>
    </header>

    <section className="ns-filter-toolbar">
      <label className="ns-search-control"><span className="sr-only">Buscar</span><input value={query} onChange={e => setQuery(e.target.value)} placeholder="Buscar por nombre o código" />{query && <button type="button" onClick={() => setQuery('')}>×</button>}</label>
      <label className="ns-filter-control"><span>Estado</span><select value={status} onChange={e => setStatus(e.target.value as OrganizationStatus | 'ALL')}><option value="ALL">Todos</option><option value="ACTIVE">Activas</option><option value="INACTIVE">Inactivas</option><option value="SUSPENDED">Suspendidas</option><option value="EXPIRED">Vencidas</option></select></label>
      <strong>{items.length} organizaciones</strong>
    </section>

    {error && <section className="state-card error-state">{error}</section>}
    {loading ? <section className="state-card">Consultando organizaciones…</section> :
      <section className="ns-table-card"><div className="ns-table-scroll"><table className="ns-resource-table"><thead><tr><th>Organización</th><th>Modalidad</th><th>Estado</th><th>Vigencia</th><th>Actualización</th><th>Acciones</th></tr></thead><tbody>{items.map(item => <tr key={item.publicId}><td><strong>{item.name}</strong><small>{item.code}</small></td><td>{item.contentMode === 'GLOBAL_CATALOG' ? 'Catálogo global' : item.contentMode === 'CLEAN' ? 'En limpio' : 'Personalizada'}</td><td><span className={`status-badge status-${item.status.toLowerCase()}`}>{statusLabel[item.status]}</span></td><td>{item.expiresOn ?? 'Sin vencimiento'}</td><td>{new Date(item.updatedAt).toLocaleDateString('es-MX')}</td><td><TableActions><TableActionLink to={`/admin/organizations/${item.publicId}`} label="Ver" icon="view"/><TableActionLink to={`/admin/organizations/${item.publicId}/edit`} label="Editar" icon="edit" tone="primary"/></TableActions></td></tr>)}</tbody></table></div></section>}
  </main>
}
