import { useCallback, useEffect, useMemo, useState } from 'react'
import { useAuth } from '../features/authentication/context/AuthContext'
import {
  distributeContent,
  getPromotionPreview,
  listPromotions,
  promoteContent,
  publishPromotion,
  reviewGlobalContent,
  submitPromotion,
  type ContentResource,
  type ContentScope,
  type DistributionMode,
  type GlobalContentType,
  type PromotionPreview,
  type PromotionView,
  type ReviewPage
} from '../features/global-content/api/globalContentApi'
import { searchOrganizations } from '../features/organizations/api/organizationApi'
import type { OrganizationSummary } from '../features/organizations/types/organizations'
import { ApiRequestError } from '../shared/api/apiClient'
import { Icon } from '../shared/components/Icon'
import { useToast } from '../shared/components/ToastProvider'
import { useDebouncedValue } from '../shared/hooks/useDebouncedValue'

const TYPE_LABELS: Record<GlobalContentType, string> = {
  CATEGORY: 'Categoría',
  QUESTION: 'Pregunta',
  FORM: 'Formulario',
  COLLECTION: 'Colección',
  PATH: 'Path'
}

const STATUS_LABELS: Record<string, string> = {
  DRAFT: 'Borrador',
  UNDER_REVIEW: 'En revisión',
  PUBLISHED: 'Publicado',
  REJECTED: 'Rechazado',
  ARCHIVED: 'Archivado'
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('es-MX', {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(new Date(value))
}

function errorMessage(error: unknown) {
  return error instanceof ApiRequestError
    ? error.message
    : 'No fue posible completar la operación.'
}

export function GlobalContentGovernancePage() {
  const toast = useToast()
  const { user } = useAuth()
  const permissions = useMemo(() => new Set(user?.permissions ?? []), [user])
  const [query, setQuery] = useState('')
  const [organization, setOrganization] = useState('')
  const [contentType, setContentType] = useState<GlobalContentType | 'ALL'>('ALL')
  const [scope, setScope] = useState<ContentScope | 'ALL'>('GLOBAL')
  const [data, setData] = useState<ReviewPage | null>(null)
  const [organizations, setOrganizations] = useState<OrganizationSummary[]>([])
  const [promotions, setPromotions] = useState<PromotionView[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string>()
  const [page, setPage] = useState(0)
  const [reloadKey, setReloadKey] = useState(0)
  const [preview, setPreview] = useState<PromotionPreview | null>(null)
  const [promotionNotes, setPromotionNotes] = useState('')
  const [includeDependencies, setIncludeDependencies] = useState(true)
  const [savingPromotion, setSavingPromotion] = useState(false)
  const [selectedPromotionId, setSelectedPromotionId] = useState('')
  const [selectedOrganizations, setSelectedOrganizations] = useState<string[]>([])
  const [distributionMode, setDistributionMode] = useState<DistributionMode>('GLOBAL_REFERENCE')
  const [distributing, setDistributing] = useState(false)
  const debouncedQuery = useDebouncedValue(query, 300)

  const loadSupportingData = useCallback(async (signal?: AbortSignal) => {
    const [organizationPage, promotionItems] = await Promise.all([
      searchOrganizations({ status: 'ACTIVE', page: 0, size: 100, signal }),
      listPromotions(signal)
    ])
    setOrganizations(
      organizationPage.content.filter((item) => item.organizationType === 'CUSTOMER')
    )
    setPromotions(promotionItems)
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    void loadSupportingData(controller.signal).catch((requestError: unknown) => {
      if (!controller.signal.aborted) setError(errorMessage(requestError))
    })
    return () => controller.abort()
  }, [loadSupportingData, reloadKey])

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(undefined)
    reviewGlobalContent({
      query: debouncedQuery,
      organizationPublicId: organization || undefined,
      contentType,
      scope,
      page,
      size: 20,
      signal: controller.signal
    })
      .then(setData)
      .catch((requestError: unknown) => {
        if (!controller.signal.aborted) setError(errorMessage(requestError))
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [contentType, debouncedQuery, organization, page, reloadKey, scope])

  useEffect(() => setPage(0), [debouncedQuery, organization, contentType, scope])

  const publishedPromotions = promotions.filter((item) => item.status === 'PUBLISHED')
  const selectedPromotion = publishedPromotions.find((item) => item.publicId === selectedPromotionId)

  async function openPromotion(resource: ContentResource) {
    try {
      setPreview(await getPromotionPreview(resource.contentType, resource.publicId))
      setPromotionNotes('')
      setIncludeDependencies(true)
    } catch (requestError) {
      toast.error('No fue posible preparar la promoción', errorMessage(requestError))
    }
  }

  async function confirmPromotion() {
    if (!preview || savingPromotion) return
    setSavingPromotion(true)
    try {
      const created = await promoteContent({
        contentType: preview.source.contentType,
        sourcePublicId: preview.source.publicId,
        includeDependencies,
        duplicateResolution: 'CREATE_DISTINCT',
        notes: promotionNotes.trim() || undefined
      })
      toast.success('Contenido promovido', 'La copia global quedó registrada como borrador.')
      setPreview(null)
      setPromotions((current) => [created, ...current.filter((item) => item.publicId !== created.publicId)])
      setReloadKey((value) => value + 1)
    } catch (requestError) {
      toast.error('No fue posible promover el contenido', errorMessage(requestError))
    } finally {
      setSavingPromotion(false)
    }
  }

  async function changeEditorialStatus(item: PromotionView, action: 'review' | 'publish') {
    try {
      const updated = action === 'review'
        ? await submitPromotion(item.publicId)
        : await publishPromotion(item.publicId)
      setPromotions((current) => current.map((value) => value.publicId === updated.publicId ? updated : value))
      toast.success(
        action === 'review' ? 'Contenido enviado a revisión' : 'Contenido publicado',
        action === 'review'
          ? 'La copia global está lista para revisión editorial.'
          : 'El contenido ya puede habilitarse para organizaciones.'
      )
    } catch (requestError) {
      toast.error('No fue posible actualizar el estado editorial', errorMessage(requestError))
    }
  }

  function toggleOrganization(publicId: string) {
    setSelectedOrganizations((current) => current.includes(publicId)
      ? current.filter((value) => value !== publicId)
      : [...current, publicId])
  }

  async function confirmDistribution() {
    if (!selectedPromotion || selectedOrganizations.length === 0 || distributing) return
    setDistributing(true)
    try {
      const result = await distributeContent({
        contentType: selectedPromotion.contentType,
        globalContentPublicId: selectedPromotion.globalContentPublicId,
        globalVersion: selectedPromotion.globalVersion,
        organizationPublicIds: selectedOrganizations,
        distributionMode,
        accessMode: distributionMode === 'GLOBAL_REFERENCE' ? 'USE_DIRECT' : 'EDITABLE_COPY',
        cloningAllowed: distributionMode === 'GLOBAL_REFERENCE',
        organizationEditable: distributionMode === 'ORGANIZATION_COPY',
        updatePolicy: distributionMode === 'GLOBAL_REFERENCE' ? 'AUTOMATIC' : 'MANUAL'
      })
      toast.success(
        'Distribución finalizada',
        `${result.successfulOrganizations} exitosas, ${result.skippedOrganizations} omitidas y ${result.failedOrganizations} fallidas.`
      )
      setSelectedOrganizations([])
      setReloadKey((value) => value + 1)
    } catch (requestError) {
      toast.error('No fue posible distribuir el contenido', errorMessage(requestError))
    } finally {
      setDistributing(false)
    }
  }

  return (
    <main className="content-page resource-page global-governance-page">
      <header className="ns-page-header">
        <div>
          <p className="eyebrow">Administración global</p>
          <h1>Gobierno de contenido</h1>
          <p className="muted">
            Revisa contenido organizacional, promueve copias al catálogo maestro y controla su distribución.
          </p>
        </div>
      </header>

      {error && (
        <section className="inline-error-panel" role="alert">
          <div className="inline-error-icon"><Icon name="error" size={20} /></div>
          <div><strong>No fue posible cargar el gobierno de contenido</strong><p>{error}</p></div>
          <button className="secondary-button" type="button" onClick={() => setReloadKey((value) => value + 1)}>
            Reintentar
          </button>
        </section>
      )}

      <section className="governance-card">
        <div className="governance-card-heading">
          <div><h2>Explorar contenido</h2><p>La consulta transversal requiere un contexto y filtros explícitos.</p></div>
          <span className="resource-count">{data?.totalElements ?? 0} recursos</span>
        </div>
        <div className="governance-filters">
          <label className="field-control governance-search">
            <span>Buscar</span>
            <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Nombre o descripción" />
          </label>
          <label className="field-control">
            <span>Organización</span>
            <select
              value={organization}
              onChange={(event) => {
                const value = event.target.value
                setOrganization(value)
                setScope(value ? 'ORGANIZATION' : 'GLOBAL')
              }}
            >
              <option value="">Catálogo GLOBAL</option>
              {organizations.map((item) => <option key={item.publicId} value={item.publicId}>{item.name}</option>)}
            </select>
          </label>
          <label className="field-control">
            <span>Tipo</span>
            <select value={contentType} onChange={(event) => setContentType(event.target.value as GlobalContentType | 'ALL')}>
              <option value="ALL">Todos</option>
              {Object.entries(TYPE_LABELS).filter(([value]) => value !== 'PATH').map(([value, label]) => (
                <option key={value} value={value}>{label}</option>
              ))}
            </select>
          </label>
          <label className="field-control">
            <span>Alcance</span>
            <select value={scope} onChange={(event) => setScope(event.target.value as ContentScope | 'ALL')}>
              {organization ? (
                <>
                  <option value="ALL">Todos dentro del contexto</option>
                  <option value="ORGANIZATION">Organización seleccionada</option>
                </>
              ) : (
                <option value="GLOBAL">GLOBAL</option>
              )}
            </select>
          </label>
        </div>

        <div className="ns-data-table-wrap">
          <table className="ns-data-table governance-table">
            <thead><tr><th>Contenido</th><th>Origen</th><th>Alcance</th><th>Estado</th><th>Trazabilidad</th><th>Acción</th></tr></thead>
            <tbody>
              {loading && <tr><td colSpan={6} className="ns-table-empty">Cargando contenido…</td></tr>}
              {!loading && data?.content.length === 0 && (
                <tr><td colSpan={6} className="ns-table-empty"><strong>No hay contenido con estos filtros</strong><span>Ajusta la organización, el tipo o el alcance.</span></td></tr>
              )}
              {!loading && data?.content.map((resource) => (
                <tr key={`${resource.contentType}-${resource.publicId}`}>
                  <td><strong>{resource.name}</strong><small>{TYPE_LABELS[resource.contentType]} · v{resource.version}</small></td>
                  <td>{resource.ownerOrganizationName ?? 'GLOBAL'}</td>
                  <td><span className={`status-badge ${resource.scope === 'GLOBAL' ? 'active' : 'draft'}`}>{resource.scope}</span></td>
                  <td>{resource.status}</td>
                  <td><small>{resource.promoted ? 'Promovido' : 'Sin promover'} · {resource.distributed ? 'Distribuido' : 'Sin distribuir'}<br />{formatDate(resource.updatedAt)}</small></td>
                  <td>
                    {resource.scope === 'ORGANIZATION' && permissions.has('GLOBAL_CONTENT_PROMOTE') ? (
                      <button className="secondary-button compact-button" type="button" onClick={() => void openPromotion(resource)}>
                        Promover
                      </button>
                    ) : <span className="muted">Consulta</span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {(data?.totalPages ?? 0) > 1 && (
          <div className="table-pagination">
            <button className="secondary-button" type="button" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>Anterior</button>
            <span>Página {page + 1} de {data?.totalPages}</span>
            <button className="secondary-button" type="button" disabled={page + 1 >= (data?.totalPages ?? 0)} onClick={() => setPage((value) => value + 1)}>Siguiente</button>
          </div>
        )}
      </section>

      <section className="governance-grid">
        <article className="governance-card">
          <div className="governance-card-heading"><div><h2>Flujo editorial</h2><p>Las promociones son copias independientes del contenido original.</p></div></div>
          <div className="governance-list">
            {promotions.length === 0 && <p className="muted">Todavía no existen promociones.</p>}
            {promotions.slice(0, 10).map((item) => (
              <div className="governance-list-item" key={item.publicId}>
                <div><strong>{item.sourceContentName}</strong><small>{item.sourceOrganizationName} · {TYPE_LABELS[item.contentType]} · v{item.globalVersion}</small></div>
                <span className={`status-badge editorial-${item.status.toLowerCase()}`}>{STATUS_LABELS[item.status] ?? item.status}</span>
                <div className="governance-inline-actions">
                  {item.status === 'DRAFT' && permissions.has('GLOBAL_CONTENT_PROMOTE') && (
                    <button type="button" className="secondary-button compact-button" onClick={() => void changeEditorialStatus(item, 'review')}>Enviar a revisión</button>
                  )}
                  {item.status === 'UNDER_REVIEW' && permissions.has('GLOBAL_CONTENT_PUBLISH') && (
                    <button type="button" className="primary-button compact-button" onClick={() => void changeEditorialStatus(item, 'publish')}>Publicar</button>
                  )}
                </div>
              </div>
            ))}
          </div>
        </article>

        <article className="governance-card">
          <div className="governance-card-heading"><div><h2>Distribuir contenido publicado</h2><p>Crea una referencia de solo lectura o una copia personalizable.</p></div></div>
          <label className="field-control">
            <span>Contenido global</span>
            <select value={selectedPromotionId} onChange={(event) => setSelectedPromotionId(event.target.value)}>
              <option value="">Selecciona contenido</option>
              {publishedPromotions.map((item) => <option key={item.publicId} value={item.publicId}>{item.sourceContentName} · v{item.globalVersion}</option>)}
            </select>
          </label>
          <label className="field-control">
            <span>Modalidad</span>
            <select value={distributionMode} onChange={(event) => setDistributionMode(event.target.value as DistributionMode)}>
              <option value="GLOBAL_REFERENCE">Referencia global</option>
              <option value="ORGANIZATION_COPY">Copia organizacional</option>
            </select>
          </label>
          <fieldset className="organization-selector">
            <legend>Organizaciones destino</legend>
            {organizations.map((item) => (
              <label key={item.publicId}>
                <input type="checkbox" checked={selectedOrganizations.includes(item.publicId)} onChange={() => toggleOrganization(item.publicId)} />
                <span>{item.name}</span>
              </label>
            ))}
          </fieldset>
          <button className="primary-button" type="button" disabled={!selectedPromotion || selectedOrganizations.length === 0 || distributing} onClick={() => void confirmDistribution()}>
            {distributing ? 'Distribuyendo…' : 'Distribuir contenido'}
          </button>
        </article>
      </section>

      {preview && (
        <div className="governance-modal-backdrop" role="presentation">
          <section className="governance-modal" role="dialog" aria-modal="true" aria-labelledby="promotion-title">
            <header><div><p className="eyebrow">Promoción controlada</p><h2 id="promotion-title">Promover «{preview.source.name}»</h2></div><button className="icon-button" type="button" aria-label="Cerrar" onClick={() => setPreview(null)}><Icon name="close" /></button></header>
            <dl className="governance-summary"><div><dt>Organización</dt><dd>{preview.source.ownerOrganizationName}</dd></div><div><dt>Tipo</dt><dd>{TYPE_LABELS[preview.source.contentType]}</dd></div><div><dt>Versión origen</dt><dd>{preview.source.version}</dd></div><div><dt>Dependencias</dt><dd>{preview.dependencies.length}</dd></div></dl>
            {preview.warnings.length > 0 && <div className="inline-warning-panel"><Icon name="warning" size={18} /><div>{preview.warnings.map((warning) => <p key={warning}>{warning}</p>)}</div></div>}
            <label className="checkbox-row"><input type="checkbox" checked={includeDependencies} onChange={(event) => setIncludeDependencies(event.target.checked)} /><span>Promover también las dependencias organizacionales faltantes</span></label>
            <label className="field-control"><span>Notas de promoción</span><textarea rows={3} value={promotionNotes} onChange={(event) => setPromotionNotes(event.target.value)} maxLength={2000} /></label>
            <footer><button className="secondary-button" type="button" disabled={savingPromotion} onClick={() => setPreview(null)}>Cancelar</button><button className="primary-button" type="button" disabled={savingPromotion} onClick={() => void confirmPromotion()}>{savingPromotion ? 'Promoviendo…' : 'Crear copia global'}</button></footer>
          </section>
        </div>
      )}
    </main>
  )
}
