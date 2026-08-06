import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useAuth } from '../features/authentication/context/AuthContext'
import { searchOrganizations } from '../features/organizations/api/organizationApi'
import { getTalentCatalogs, permanentlyDeletePerson, searchTalents } from '../features/talent-bank/api/talentBankApi'
import type { OrganizationSummary } from '../features/organizations/types/organizations'
import { ApiRequestError } from '../shared/api/apiClient'
import { ConfirmDialog } from '../shared/components/ConfirmDialog'
import { FilterToolbar } from '../shared/components/FilterToolbar'
import { Icon } from '../shared/components/Icon'
import { ResourceSearchField, ResourceSelectField } from '../shared/components/ResourceFilters'
import { TableActionButton, TableActionLink, TableActions } from '../shared/components/TableActions'
import { TablePagination } from '../shared/components/TablePagination'
import { useToast } from '../shared/components/ToastProvider'
import { useDebouncedValue } from '../shared/hooks/useDebouncedValue'
import { parsePage, parsePageSize, type PageSize } from '../shared/types/pagination'
import type { TalentCatalogs, TalentPage, TalentSummary, TalentType } from '../shared/types/talentBank'
import { formatPersonName } from '../shared/utils/personNames'

const TYPES: Array<{ value: TalentType | 'ALL'; label: string }> = [
  { value: 'ALL', label: 'Todos los tipos' },
  { value: 'ACADEMY', label: 'Academia' },
  { value: 'PROSPECT', label: 'Prospecto' },
  { value: 'BBVA_EXIT', label: 'Baja' }
]
const TYPE_LABELS: Record<TalentType, string> = {
  ACADEMY: 'Academia', PROSPECT: 'Prospecto', BBVA_EXIT: 'Baja'
}

export function TalentBankPage() {
  const toast = useToast()
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
  const [deleteCandidate, setDeleteCandidate] = useState<TalentSummary>()
  const [deleting, setDeleting] = useState(false)
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

  async function confirmPermanentDeletion() {
    if (!deleteCandidate || deleting) return
    setDeleting(true)
    try {
      await permanentlyDeletePerson(deleteCandidate.publicId)
      setData((current) => current ? {
        ...current,
        content: current.content.filter((item) => item.publicId !== deleteCandidate.publicId),
        totalElements: Math.max(0, current.totalElements - 1)
      } : current)
      toast.success('El registro y toda su información asociada fueron eliminados definitivamente.')
      setDeleteCandidate(undefined)
    } catch (requestError) {
      toast.error('No fue posible eliminar el registro.', requestError instanceof ApiRequestError ? requestError.message : undefined)
    } finally {
      setDeleting(false)
    }
  }

  return <main className="content-page resource-page ns-list-page talent-bank-page">
    {permissions.has('TALENT_CREATE') && <div className="ns-list-action-bar">
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
        <thead><tr><th>Persona</th>{administrator && <th>Organización</th>}<th>Tipo</th><th>Perfil</th><th>Tecnología</th><th>CV</th><th className="ns-actions-column">Acciones</th></tr></thead>
        <tbody>
          {loading && <tr><td colSpan={administrator ? 7 : 6} className="ns-table-empty">Cargando talentos…</td></tr>}
          {!loading && !error && !data?.content.length && <tr><td colSpan={administrator ? 7 : 6} className="ns-table-empty">No se encontraron talentos.</td></tr>}
          {!loading && data?.content.map((talent) => <tr key={talent.publicId}>
            <td className="ns-primary-cell"><span className="ns-person-name">{formatPersonName(talent.displayName)}</span><small>{talent.email}</small></td>
            {administrator && <td>{talent.organization.name}<small className="ns-cell-secondary">{talent.organization.code}</small></td>}
            <td><span className={`status-badge talent-type-${talent.talentType.toLowerCase()}`}>{TYPE_LABELS[talent.talentType]}</span></td>
            <td>{talent.profileCode ?? 'N/A'}</td><td>{talent.talentType === 'BBVA_EXIT' ? (talent.currentTechnologyExpertise || 'N/A') : (talent.technology?.name ?? 'N/A')}</td>
            <td>{talent.hasCv ? 'Disponible' : 'N/A'}</td>
            <td className="ns-actions-column"><TableActions>
              <TableActionLink icon="eye" label="Ver" to={`/admin/talent-bank/${talent.publicId}`} />
              {permissions.has('TALENT_UPDATE') && <TableActionLink icon="edit" label="Editar" to={`/admin/talent-bank/${talent.publicId}/edit`} />}
              {permissions.has('TALENT_CONVERT') && <TableActionLink icon="chevronRight" label="Convertir a colaborador" to={`/admin/talent-bank/${talent.publicId}/convert`} tone="primary" />}
              {permissions.has('TALENT_DELETE') && <TableActionButton icon="trash" label="Eliminar definitivamente" tone="danger" onClick={() => setDeleteCandidate(talent)} />}
            </TableActions></td>
          </tr>)}
        </tbody>
      </table></div>
      <TablePagination currentPage={page} pageSize={data?.size ?? size} totalElements={data?.totalElements ?? 0}
        totalPages={data?.totalPages ?? 0} isLoading={loading} onPageChange={changePage} onPageSizeChange={changePageSize} />
    </section>
    <ConfirmDialog open={Boolean(deleteCandidate)} title="Eliminar definitivamente"
      description="Esta acción eliminará de forma permanente el registro, su historial, sus documentos y toda la información asociada. La información no podrá continuar consultándose en la plataforma. ¿Deseas continuar?"
      confirmLabel="Eliminar definitivamente" tone="danger" busy={deleting}
      onCancel={() => setDeleteCandidate(undefined)} onConfirm={() => void confirmPermanentDeletion()}>
      {deleteCandidate && <div className="permanent-deletion-warning"><span className="ns-person-name">{formatPersonName(deleteCandidate.displayName)}</span><p>{deleteCandidate.organization.name} · {TYPE_LABELS[deleteCandidate.talentType]}</p></div>}
    </ConfirmDialog>
  </main>
}
