import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useAuth } from '../features/authentication/context/AuthContext'
import { searchOrganizations } from '../features/organizations/api/organizationApi'
import { permanentlyDeletePerson, searchPermanentDeletions } from '../features/talent-bank/api/talentBankApi'
import type { OrganizationSummary } from '../features/organizations/types/organizations'
import { ApiRequestError } from '../shared/api/apiClient'
import { ConfirmDialog } from '../shared/components/ConfirmDialog'
import { FilterToolbar } from '../shared/components/FilterToolbar'
import { ResourceSearchField, ResourceSelectField } from '../shared/components/ResourceFilters'
import { TableActions } from '../shared/components/TableActions'
import { TablePagination } from '../shared/components/TablePagination'
import { useDebouncedValue } from '../shared/hooks/useDebouncedValue'
import { useToast } from '../shared/components/ToastProvider'
import { parsePage, parsePageSize, type PageSize } from '../shared/types/pagination'
import type { PermanentDeletionCandidate, PermanentDeletionPage } from '../shared/types/talentBank'

export function PermanentDeletionsPage() {
  const { user } = useAuth(); const administrator = Boolean(user?.roles.includes('ADMINISTRATOR'))
  const toast = useToast(); const [params, setParams] = useSearchParams()
  const [query, setQuery] = useState(params.get('query') ?? ''); const debounced = useDebouncedValue(query, 300)
  const [organizations, setOrganizations] = useState<OrganizationSummary[]>([])
  const [data, setData] = useState<PermanentDeletionPage>(); const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string>(); const [candidate, setCandidate] = useState<PermanentDeletionCandidate>()
  const [deleting, setDeleting] = useState(false)
  const page = parsePage(params.get('page')); const size = parsePageSize(params.get('size'))
  const organization = params.get('organization') ?? ''; const module = (params.get('module') ?? 'ALL') as 'ALL' | 'COLLABORATOR' | 'TALENT_BANK'
  const hasFilters = useMemo(() => Boolean(query || organization || module !== 'ALL'), [query, organization, module])
  useEffect(() => { if (!administrator) return; const c = new AbortController(); searchOrganizations({ status: 'ACTIVE', page: 0, size: 100, signal: c.signal }).then((r) => setOrganizations(r.content.filter((o) => o.organizationType === 'CUSTOMER'))).catch(() => {}); return () => c.abort() }, [administrator])
  useEffect(() => { const current = params.get('query') ?? ''; if (current === debounced.trim()) return; const next = new URLSearchParams(params); next.delete('page'); if (debounced.trim()) next.set('query', debounced.trim()); else next.delete('query'); setParams(next, { replace: true }) }, [debounced, params, setParams])
  useEffect(() => { const c = new AbortController(); setLoading(true); setError(undefined); searchPermanentDeletions({ query: params.get('query') ?? '', module, organizationPublicId: administrator ? organization || undefined : undefined, page, size, signal: c.signal }).then(setData).catch((e) => { if (!c.signal.aborted) setError(e instanceof ApiRequestError ? e.message : 'No fue posible consultar los registros.') }).finally(() => { if (!c.signal.aborted) setLoading(false) }); return () => c.abort() }, [administrator, module, organization, page, params, size])
  function param(name: string, value: string) { const next = new URLSearchParams(params); next.delete('page'); if (value && value !== 'ALL') next.set(name, value); else next.delete(name); setParams(next) }
  function go(nextPage: number) { const next = new URLSearchParams(params); if (nextPage) next.set('page', String(nextPage)); else next.delete('page'); setParams(next) }
  function resize(nextSize: PageSize) { const next = new URLSearchParams(params); next.delete('page'); if (nextSize !== 10) next.set('size', String(nextSize)); else next.delete('size'); setParams(next) }
  async function confirmDelete() { if (!candidate || deleting) return; setDeleting(true); try { await permanentlyDeletePerson(candidate.publicId); toast.success('El registro y toda su información asociada fueron eliminados definitivamente.'); setCandidate(undefined); const refreshed = await searchPermanentDeletions({ query: params.get('query') ?? '', module, organizationPublicId: administrator ? organization || undefined : undefined, page, size }); setData(refreshed) } catch (e) { toast.error('No fue posible eliminar el registro.', e instanceof ApiRequestError ? e.message : undefined) } finally { setDeleting(false) } }
  return <main className="content-page resource-page ns-list-page permanent-deletions-page">
    <section className="editor-card permanent-deletion-warning"><strong>Eliminaciones definitivas</strong><p>Esta acción elimina físicamente el registro, su historial, su documentación y toda la información asociada.</p></section>
    <FilterToolbar hasActiveFilters={hasFilters} onClear={() => { setQuery(''); setParams(new URLSearchParams()) }}><ResourceSearchField value={query} onChange={setQuery} placeholder="Buscar persona" />{administrator && <ResourceSelectField label="Organización" value={organization} onChange={(v) => param('organization', v)}><option value="">Todas</option>{organizations.map((o) => <option key={o.publicId} value={o.publicId}>{o.name}</option>)}</ResourceSelectField>}<ResourceSelectField label="Módulo" value={module} onChange={(v) => param('module', v)}><option value="ALL">Todos</option><option value="COLLABORATOR">Colaboradores</option><option value="TALENT_BANK">Talent Bank</option></ResourceSelectField></FilterToolbar>
    {error && <div className="error-message">{error}</div>}<section className="ns-data-panel"><div className="ns-data-table-wrap"><table className="ns-data-table"><thead><tr><th>Persona</th>{administrator && <th>Organización</th>}<th>Estado</th><th>Módulo</th><th>Tipo</th><th>CV</th><th className="ns-actions-column">Acciones</th></tr></thead><tbody>{loading && <tr><td colSpan={administrator ? 7 : 6} className="ns-table-empty">Cargando…</td></tr>}{!loading && !data?.content.length && <tr><td colSpan={administrator ? 7 : 6} className="ns-table-empty">Sin registros.</td></tr>}{data?.content.map((item) => <tr key={item.publicId}><td className="ns-primary-cell"><strong>{item.displayName}</strong><small>{item.email}</small></td>{administrator && <td>{item.organizationName}</td>}<td>{item.status === 'DELETED' ? 'Eliminado lógico' : item.status === 'ACTIVE' ? 'Activo' : item.status === 'EXPIRED' ? 'Vencido' : 'Inactivo'}</td><td>{item.module === 'COLLABORATOR' ? 'Colaboradores' : 'Talent Bank'}</td><td>{item.talentType === 'ACADEMY' ? 'Academia' : item.talentType === 'PROSPECT' ? 'Prospecto' : item.talentType === 'BBVA_EXIT' ? 'Baja de BBVA' : 'Colaborador'}</td><td>{item.hasCv ? 'Sí' : 'No'}</td><td><TableActions><button type="button" className="table-action table-action-danger" onClick={() => setCandidate(item)}>Eliminar definitivamente</button></TableActions></td></tr>)}</tbody></table></div><TablePagination currentPage={page} pageSize={data?.size ?? size} totalElements={data?.totalElements ?? 0} totalPages={data?.totalPages ?? 0} isLoading={loading} onPageChange={go} onPageSizeChange={resize} /></section>
    <ConfirmDialog open={Boolean(candidate)} title="Eliminar definitivamente" description="Esta acción eliminará físicamente el registro, su historial, su documentación y toda la información asociada. La eliminación es definitiva. ¿Deseas continuar?" confirmLabel="Confirmar eliminación" tone="danger" busy={deleting} onCancel={() => setCandidate(undefined)} onConfirm={() => void confirmDelete()} />
  </main>
}
