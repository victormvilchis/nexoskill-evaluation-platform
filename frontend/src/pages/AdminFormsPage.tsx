import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useAuth } from '../features/authentication/context/AuthContext'
import { cloneForm, getForm, getFormOrganizations } from '../features/forms/api/formApi'
import { apiRequest, ApiRequestError } from '../shared/api/apiClient'
import { ConfirmDialog } from '../shared/components/ConfirmDialog'
import { FilterToolbar } from '../shared/components/FilterToolbar'
import { Icon } from '../shared/components/Icon'
import { ResourceSearchField, ResourceSelectField } from '../shared/components/ResourceFilters'
import { SelectField } from '../shared/components/SelectField'
import { TableActionButton, TableActionLink, TableActions } from '../shared/components/TableActions'
import { TablePagination } from '../shared/components/TablePagination'
import { useToast } from '../shared/components/ToastProvider'
import { useDebouncedValue } from '../shared/hooks/useDebouncedValue'
import type { FormContentScope, FormOrganizationOption, FormStatus, FormSummary } from '../shared/types/forms'
import { normalizePagedResponse, parsePage, parsePageSize, type PagedResponse, type PageSize } from '../shared/types/pagination'

const formStatusLabel: Record<FormStatus, string> = {
  DRAFT: 'Borrador', ACTIVE: 'Activo', DISABLED: 'Deshabilitado', CLOSED: 'Cerrado', ARCHIVED: 'Archivado'
}

function formatDate(value?: string) {
  if (!value) return 'Sin fecha'
  return new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium' }).format(new Date(value))
}

function operationId() {
  return typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

interface CloneState {
  source: FormSummary
  title: string
  targetScope: FormContentScope
  organizationPublicId: string
  operationId: string
}

export function AdminFormsPage() {
  const { user } = useAuth()
  const toast = useToast()
  const canCreate = user?.permissions.includes('FORM_CREATE') ?? false
  const canUpdate = user?.permissions.includes('FORM_UPDATE') ?? false
  const globalAdministrator = Boolean(user?.roles.includes('ADMINISTRATOR'))
  const [searchParams, setSearchParams] = useSearchParams()
  const [query, setQuery] = useState(searchParams.get('query') ?? '')
  const [status, setStatus] = useState(searchParams.get('status') ?? 'ACTIVE')
  const [mode, setMode] = useState(searchParams.get('mode') ?? '')
  const [data, setData] = useState<PagedResponse<FormSummary>>({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 })
  const [organizations, setOrganizations] = useState<FormOrganizationOption[]>([])
  const [organizationsLoading, setOrganizationsLoading] = useState(false)
  const [organizationsLoaded, setOrganizationsLoaded] = useState(false)
  const [organizationsError, setOrganizationsError] = useState('')
  const [cloneState, setCloneState] = useState<CloneState | null>(null)
  const [cloneBusy, setCloneBusy] = useState(false)
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

  async function loadOrganizationsForClone(force = false) {
    if (!globalAdministrator || organizationsLoading || (organizationsLoaded && !force)) return
    setOrganizationsLoading(true)
    setOrganizationsError('')
    try {
      const available = await getFormOrganizations()
      setOrganizations(available)
      setOrganizationsLoaded(true)
    } catch (requestError) {
      setOrganizationsError(requestError instanceof ApiRequestError
        ? requestError.message
        : 'No fue posible cargar las organizaciones para clonación.')
    } finally {
      setOrganizationsLoading(false)
    }
  }

  function openClone(source: FormSummary) {
    setCloneState({
      source,
      title: `Copia de ${source.title}`,
      targetScope: globalAdministrator ? source.contentScope : 'ORGANIZATION',
      organizationPublicId: globalAdministrator && source.contentScope === 'ORGANIZATION'
        ? source.ownerOrganizationPublicId
        : '',
      operationId: operationId()
    })
    if (globalAdministrator) void loadOrganizationsForClone()
  }

  async function confirmClone() {
    if (!cloneState || cloneBusy) return
    if (cloneState.title.trim().length < 3) {
      toast.warning('Indica un título de al menos 3 caracteres para la copia.')
      return
    }
    if (globalAdministrator && cloneState.targetScope === 'ORGANIZATION' && !cloneState.organizationPublicId) {
      toast.warning('Selecciona la organización de destino.')
      return
    }

    setCloneBusy(true)
    try {
      const cloned = await cloneForm(cloneState.source.publicId, {
        title: cloneState.title.trim(),
        targetScope: cloneState.targetScope,
        organizationPublicId: cloneState.targetScope === 'ORGANIZATION'
          ? cloneState.organizationPublicId || undefined
          : undefined,
        operationId: cloneState.operationId
      })
      await getForm(cloned.publicId)
      toast.success('Formulario clonado', 'El formulario y toda su configuración se clonaron correctamente.')
      setCloneState(null)
      setReloadKey(value => value + 1)
    } catch (requestError) {
      toast.error(requestError instanceof ApiRequestError
        ? requestError.message
        : 'No fue posible clonar el formulario.')
    } finally {
      setCloneBusy(false)
    }
  }

  return (
    <main className="content-page resource-page ns-list-page">
      {canCreate && <div className="ns-list-action-bar" aria-label="Acciones de formularios"><Link className="primary-button button-link ns-create-button" to="/admin/forms/new"><Icon name="plus" size={15} /> Nuevo formulario</Link></div>}

      <FilterToolbar hasActiveFilters={hasFilters} onClear={() => { setQuery(''); setStatus('ACTIVE'); setMode(''); updateUrl({ query: undefined, status: undefined, mode: undefined, page: undefined }) }}>
        <ResourceSearchField value={query} onChange={setQuery} placeholder="Buscar por nombre o código" />
        <ResourceSelectField label="Modalidad" value={mode} onChange={setMode}><option value="">Todas</option><option value="ASSESSMENT">Evaluación</option><option value="PRACTICE">Práctica</option></ResourceSelectField>
        <ResourceSelectField label="Estado" value={status} onChange={setStatus}><option value="ACTIVE">Activo</option><option value="ALL">Todos</option><option value="DRAFT">Borrador</option><option value="DISABLED">Deshabilitado</option><option value="CLOSED">Cerrado</option><option value="ARCHIVED">Archivado</option></ResourceSelectField>
      </FilterToolbar>

      {error && <section className="inline-error-panel" role="alert"><div className="inline-error-icon"><Icon name="error" size={20} /></div><div><strong>No fue posible cargar los formularios</strong><p>{error}</p></div><button className="secondary-button compact-button" type="button" onClick={() => setReloadKey(value => value + 1)}>Reintentar</button></section>}

      <section className="ns-data-panel" aria-busy={loading}>
        <div className="ns-data-table-wrap"><table className="ns-data-table">
          <thead><tr><th>Formulario</th><th>Alcance</th><th>Modalidad</th><th>Aprobación</th><th>Contenido</th><th>Disponibilidad</th><th>Estado</th><th className="ns-actions-column">Acciones</th></tr></thead>
          <tbody>
            {loading && <tr><td colSpan={8} className="ns-table-empty">Cargando formularios…</td></tr>}
            {!loading && !error && data.content.length === 0 && <tr><td colSpan={8} className="ns-table-empty"><strong>{hasFilters ? 'No encontramos coincidencias' : 'Aún no hay formularios'}</strong><span>{hasFilters ? 'Ajusta o limpia los filtros.' : 'Crea el primer formulario para comenzar.'}</span></td></tr>}
            {!loading && !error && data.content.map((form) => <tr key={form.publicId}>
              <td className="ns-primary-cell"><strong>{form.title}</strong><small><code className="ns-code-label">{form.code}</code></small></td>
              <td><strong>{form.contentScope === 'GLOBAL' ? 'Global' : 'Organizacional'}</strong><small>{form.contentScope === 'GLOBAL' ? 'Catálogo global' : form.ownerOrganizationName}</small></td>
              <td>{form.modeCode === 'PRACTICE' ? 'Práctica' : 'Evaluación'}</td>
              <td><strong>{form.passingScore}%</strong></td>
              <td>{form.contentMode === 'MANUAL'
                ? <><span>Preguntas manuales</span><small>{form.questionCount} {form.questionCount === 1 ? 'pregunta' : 'preguntas'}</small></>
                : <><span>Pool aleatorio</span><small>{form.poolCount} {form.poolCount === 1 ? 'categoría' : 'categorías'}</small></>}</td>
              <td><span>{form.startsAt ? `Desde ${formatDate(form.startsAt)}` : 'Inicio inmediato'}</span><small>{form.endsAt ? `Hasta ${formatDate(form.endsAt)}` : 'Sin fecha de cierre'}</small></td>
              <td><span className={`status-badge status-${form.status.toLowerCase()}`}>{formStatusLabel[form.status]}</span></td>
              <td><TableActions>
                <TableActionLink to={`/admin/forms/${form.publicId}`} label="Ver" icon="eye" />
                {canUpdate && (globalAdministrator || form.contentScope === 'ORGANIZATION') && <TableActionLink to={`/admin/forms/${form.publicId}/edit`} label="Editar" icon="edit" tone="primary" />}
                {canCreate && <TableActionButton label="Clonar" icon="copy" disabled={cloneBusy} onClick={() => openClone(form)} />}
              </TableActions></td>
            </tr>)}
          </tbody>
        </table></div>
        <TablePagination currentPage={page} pageSize={data.size} totalElements={data.totalElements} totalPages={data.totalPages} isLoading={loading} onPageChange={nextPage => updateUrl({ page: nextPage })} onPageSizeChange={(nextSize: PageSize) => updateUrl({ size: nextSize, page: undefined })} />
      </section>

      <ConfirmDialog
        open={Boolean(cloneState)}
        title="Clonar formulario"
        description="Se creará un formulario nuevo e independiente. El original no será modificado."
        confirmLabel="Clonar formulario"
        busy={cloneBusy}
        confirmDisabled={!cloneState?.title.trim()
          || Boolean(globalAdministrator && cloneState?.targetScope === 'ORGANIZATION'
            && (!cloneState.organizationPublicId || organizationsLoading || Boolean(organizationsError)))}
        onConfirm={() => void confirmClone()}
        onCancel={() => { if (!cloneBusy) setCloneState(null) }}
      >
        {cloneState && <div className="form-clone-fields">
          <label className="ns-field"><span>Título de la copia <b>*</b></span><input value={cloneState.title} maxLength={200} onChange={event => setCloneState(current => current ? { ...current, title: event.target.value } : current)} /></label>
          {globalAdministrator && <label className="ns-field"><span>Destino <b>*</b></span><SelectField value={cloneState.targetScope} onChange={value => setCloneState(current => current ? { ...current, targetScope: value as FormContentScope, organizationPublicId: value === 'GLOBAL' ? '' : current.organizationPublicId } : current)} ariaLabel="Destino de la clonación" options={[{ value: 'GLOBAL', label: 'Global' }, { value: 'ORGANIZATION', label: 'Organizacional' }]} /></label>}
          {globalAdministrator && cloneState.targetScope === 'ORGANIZATION' && <div className="ns-field">
            <span>Organización <b>*</b></span>
            <SelectField value={cloneState.organizationPublicId} disabled={organizationsLoading || Boolean(organizationsError)} onChange={value => setCloneState(current => current ? { ...current, organizationPublicId: value } : current)} ariaLabel="Organización de destino" placeholder={organizationsLoading ? 'Cargando organizaciones…' : 'Seleccionar organización'} options={[{ value: '', label: organizationsLoading ? 'Cargando organizaciones…' : 'Seleccionar organización' }, ...organizations.map(organization => ({ value: organization.publicId, label: `${organization.name} · ${organization.code}` }))]} />
            {organizationsError && <div className="form-option-load-error" role="alert"><span>{organizationsError}</span><button className="secondary-button compact-button" type="button" onClick={() => void loadOrganizationsForClone(true)}>Reintentar</button></div>}
          </div>}
          {!globalAdministrator && <div className="form-static-field"><strong>Destino organizacional</strong><small>La copia permanecerá dentro de tu organización.</small></div>}
          <p className="muted">Antes de crear la copia se validará que todas las preguntas o categorías sean compatibles con el destino.</p>
        </div>}
      </ConfirmDialog>
    </main>
  )
}
