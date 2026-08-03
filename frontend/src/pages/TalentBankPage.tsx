import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useAuth } from '../features/authentication/context/AuthContext'
import { searchOrganizations } from '../features/organizations/api/organizationApi'
import { getTalentCatalogs, searchTalents } from '../features/talent-bank/api/talentBankApi'
import type { OrganizationSummary } from '../features/organizations/types/organizations'
import { ApiRequestError } from '../shared/api/apiClient'
import { FilterToolbar } from '../shared/components/FilterToolbar'
import { Icon } from '../shared/components/Icon'
import { ResourceSearchField, ResourceSelectField } from '../shared/components/ResourceFilters'
import { TableActionLink, TableActions } from '../shared/components/TableActions'
import { TablePagination } from '../shared/components/TablePagination'
import { useDebouncedValue } from '../shared/hooks/useDebouncedValue'
import { parsePage, parsePageSize, type PageSize } from '../shared/types/pagination'
import type { TalentCatalogs, TalentPage, TalentProfileCode, TalentType } from '../shared/types/talentBank'

const TYPES: Array<{ value: TalentType | 'ALL'; label: string }> = [
  { value: 'ALL', label: 'Todos los tipos' },
  { value: 'ACADEMY', label: 'Academia' },
  { value: 'PROSPECT', label: 'Prospecto' },
  { value: 'BBVA_EXIT', label: 'Baja de BBVA' }
]
const TYPE_LABELS: Record<TalentType, string> = {
  ACADEMY: 'Academia', PROSPECT: 'Prospecto', BBVA_EXIT: 'Baja de BBVA'
}

export function TalentBankPage() {
  const { user } = useAuth()
  const administrator = Boolean(user?.roles.includes('ADMINISTRATOR'))
  const permissions = useMemo(() => new Set(user?.permissions ?? []), [user])
  const [params, setParams] = useSearchParams()
  const [query, setQuery] = useState(params.get('query') ?? '')
  const debouncedQuery = useDebouncedValue(query, 300)
  const [organizations, setOrganizations] = useState<OrganizationSummary[]>([])
  const [catalogs, setCatalogs] = useState<TalentCatalogs>()
  const [data, setData] = useState<TalentPage>()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string>()
  const page = parsePage(params.get('page'))
  const size = parsePageSize(params.get('size'))
  const organization = params.get('organization') ?? ''
  const type = (params.get('type') ?? 'ALL') as TalentType | 'ALL'
  const profile = params.get('profile') ?? ''
  const technology = params.get('technology') ?? ''

  useEffect(() => {
    if (!administrator) return
    const controller = new AbortController()
    searchOrganizations({ status: 'ACTIVE', page: 0, size: 100, signal: controller.signal })
      .then((result) => setOrganizations(result.content.filter((item) => item.organizationType === 'CUSTOMER')))
      .catch(() => { if (!controller.signal.aborted) setOrganizations([]) })
    return () => controller.abort()
  }, [administrator])

  useEffect(() => {
    const controller = new AbortController()
    if (administrator && !organization) { setCatalogs(undefined); return () => controller.abort() }
    getTalentCatalogs(administrator ? organization : undefined, controller.signal)
      .then(setCatalogs)
      .catch(() => { if (!controller.signal.aborted) setCatalogs(undefined) })
    return () => controller.abort()
  }, [administrator, organization])

  useEffect(() => {
    const current = params.get('query') ?? ''
    if (current === debouncedQuery.trim()) return
    const next = new URLSearchParams(params)
    next.delete('page')
    if (debouncedQuery.trim()) next.set('query', debouncedQuery.trim()); else next.delete('query')
    setParams(next, { replace: true })
  }, [debouncedQuery, params, setParams])

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true); setError(undefined)
    searchTalents({
      query: params.get('query') ?? '', organizationPublicId: administrator ? organization || undefined : undefined,
      type, profileCode: profile || undefined, technologyPublicId: technology || undefined,
      page, size, sort: params.get('sort') ?? 'updatedAt',
      direction: params.get('direction') === 'ASC' ? 'ASC' : 'DESC', signal: controller.signal
    }).then((result) => {
      setData({ ...result, content: result.content ?? [] })
      if (result.totalPages > 0 && page >= result.totalPages) changePage(result.totalPages - 1)
    }).catch((requestError) => {
      if (!controller.signal.aborted) setError(requestError instanceof ApiRequestError
        ? requestError.message : 'No fue posible consultar Talent Bank.')
    }).finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [administrator, organization, page, params, profile, size, technology, type])

  function updateParam(name: string, value: string) {
    const next = new URLSearchParams(params); next.delete('page')
    if (value && value !== 'ALL') next.set(name, value); else next.delete(name)
    if (name === 'organization') { next.delete('profile'); next.delete('technology') }
    setParams(next)
  }
  function changePage(nextPage: number) {
    const next = new URLSearchParams(params)
    if (nextPage > 0) next.set('page', String(nextPage)); else next.delete('page')
    setParams(next)
  }
  function changePageSize(nextSize: PageSize) {
    const next = new URLSearchParams(params); next.delete('page')
    if (nextSize === 10) next.delete('size'); else next.set('size', String(nextSize))
    setParams(next)
  }
  function clearFilters() { setQuery(''); setParams(new URLSearchParams()) }

  return <main className="content-page resource-page ns-list-page talent-bank-page">
    {permissions.has('STUDENT_CREATE') && <div className="ns-list-action-bar">
      <Link className="primary-button button-link ns-create-button" to="/admin/talent-bank/new">
        <Icon name="plus" size={15} /> Registrar talento
      </Link>
    </div>}
    <FilterToolbar hasActiveFilters={Boolean(query || organization || type !== 'ALL' || profile || technology)} onClear={clearFilters}>
      <ResourceSearchField value={query} onChange={setQuery} placeholder="Buscar por nombre, correo o código" />
      {administrator && <ResourceSelectField label="Organización" value={organization} onChange={(value) => updateParam('organization', value)}>
        <option value="">Todas las organizaciones</option>
        {organizations.map((item) => <option key={item.publicId} value={item.publicId}>{item.name} · {item.code}</option>)}
      </ResourceSelectField>}
      <ResourceSelectField label="Tipo" value={type} onChange={(value) => updateParam('type', value)}>
        {TYPES.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
      </ResourceSelectField>
      <ResourceSelectField label="Perfil" value={profile} onChange={(value) => updateParam('profile', value)}>
        <option value="">Todos los perfiles</option>
        {(catalogs?.profiles ?? ['TR', 'JR', 'STD', 'SR']).map((item) => <option key={item} value={item}>{item}</option>)}
      </ResourceSelectField>
      <ResourceSelectField label="Tecnología" value={technology} onChange={(value) => updateParam('technology', value)} disabled={administrator && !organization}>
        <option value="">Todas las tecnologías</option>
        {catalogs?.technologies.map((item) => <option key={item.publicId} value={item.publicId}>{item.name}</option>)}
      </ResourceSelectField>
    </FilterToolbar>
    {error && <div className="error-message" role="alert">{error}</div>}
    <section className="ns-data-panel" aria-busy={loading}>
      <div className="ns-data-table-wrap"><table className="ns-data-table">
        <thead><tr><th>Persona</th>{administrator && <th>Organización</th>}<th>Tipo</th><th>Perfil</th><th>Tecnología</th><th>CV</th><th>Vigencia</th><th className="ns-actions-column">Acciones</th></tr></thead>
        <tbody>
          {loading && <tr><td colSpan={administrator ? 8 : 7} className="ns-table-empty">Cargando talentos…</td></tr>}
          {!loading && !error && !data?.content.length && <tr><td colSpan={administrator ? 8 : 7} className="ns-table-empty">No se encontraron talentos.</td></tr>}
          {!loading && data?.content.map((talent) => <tr key={talent.publicId}>
            <td className="ns-primary-cell"><strong>{talent.displayName}</strong><small>{talent.email}</small></td>
            {administrator && <td>{talent.organization.name}<small className="ns-cell-secondary">{talent.organization.code}</small></td>}
            <td><span className={`status-badge talent-type-${talent.talentType.toLowerCase()}`}>{TYPE_LABELS[talent.talentType]}</span></td>
            <td>{talent.profileCode ?? 'N/A'}</td><td>{talent.technology?.name ?? 'N/A'}</td>
            <td>{talent.hasCv ? 'Disponible' : 'Sin CV'}</td>
            <td>{talent.expiresAt ? new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium' }).format(new Date(`${talent.expiresAt}T12:00:00`)) : 'N/A'}</td>
            <td className="ns-actions-column"><TableActions>
              <TableActionLink icon="eye" label="Ver" to={`/admin/talent-bank/${talent.publicId}`} />
              {permissions.has('STUDENT_UPDATE') && <TableActionLink icon="edit" label="Editar" to={`/admin/talent-bank/${talent.publicId}/edit`} />}
              {permissions.has('STUDENT_CREATE') && talent.talentType !== 'BBVA_EXIT' && <TableActionLink icon="chevronRight" label="Convertir a colaborador" to={`/admin/talent-bank/${talent.publicId}/convert`} tone="primary" />}
            </TableActions></td>
          </tr>)}
        </tbody>
      </table></div>
      <TablePagination currentPage={page} pageSize={data?.size ?? size} totalElements={data?.totalElements ?? 0}
        totalPages={data?.totalPages ?? 0} isLoading={loading} onPageChange={changePage} onPageSizeChange={changePageSize} />
    </section>
  </main>
}
