import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { getCatalogItems } from '../features/catalogs/api/catalogApi'
import type { CatalogItem } from '../features/catalogs/types/catalogs'
import { useAuth } from '../features/authentication/context/AuthContext'
import { searchOrganizations } from '../features/organizations/api/organizationApi'
import type { OrganizationSummary } from '../features/organizations/types/organizations'
import {
  changeQuestionStatus,
  cloneQuestionToGlobal,
  deleteQuestion,
  duplicateQuestion,
  getQuestionCatalogs,
  getQuestionClonePreview,
  getQuestionCreationYears,
  restoreQuestion,
  searchQuestions
} from '../features/questions/api/questionApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { ConfirmDialog } from '../shared/components/ConfirmDialog'
import { FilterToolbar } from '../shared/components/FilterToolbar'
import { Icon } from '../shared/components/Icon'
import { ResourceSearchField, ResourceSelectField } from '../shared/components/ResourceFilters'
import { TableActionButton, TableActionLink, TableActions } from '../shared/components/TableActions'
import { useToast } from '../shared/components/ToastProvider'
import { useDebouncedValue } from '../shared/hooks/useDebouncedValue'
import type {
  CloneToGlobalPreview,
  QuestionCatalogs,
  QuestionPage,
  QuestionStatus,
  QuestionSummary
} from '../shared/types/questions'

const statusLabel: Record<QuestionStatus, string> = {
  ACTIVE: 'Activa',
  ARCHIVED: 'Archivada',
  DELETED: 'Eliminada'
}

type PendingAction =
  | { type: 'ARCHIVE' | 'ACTIVATE' | 'DELETE' | 'RESTORE'; question: QuestionSummary }
  | null

interface CloneState {
  question: QuestionSummary
  preview?: CloneToGlobalPreview
  loading: boolean
  busy: boolean
  error?: string
  includeDependencies: boolean
  notes: string
}

function formatDate(value?: string) {
  if (!value) return 'Sin actualización'
  return new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium' }).format(new Date(value))
}

function parsePage(value: string | null) {
  const parsed = Number(value ?? '0')
  return Number.isInteger(parsed) && parsed >= 0 ? parsed : 0
}

export function AdminQuestionsPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()
  const [searchParams, setSearchParams] = useSearchParams()
  const permissions = useMemo(() => new Set(user?.permissions ?? []), [user])
  const globalAdministrator = Boolean(user?.roles.includes('ADMINISTRATOR'))

  const query = searchParams.get('q') ?? ''
  const organizationPublicId = globalAdministrator ? searchParams.get('organization') ?? '' : ''
  const categoryPublicId = searchParams.get('category') ?? ''
  const rawStatus = searchParams.get('status') ?? 'ACTIVE'
  const status = (rawStatus === 'ALL' ? '' : rawStatus) as QuestionStatus | ''
  const difficultyCode = searchParams.get('difficulty') ?? ''
  const creationYear = searchParams.get('year') ?? ''
  const page = parsePage(searchParams.get('page'))
  const debouncedQuery = useDebouncedValue(query, 300)

  const [data, setData] = useState<QuestionPage>()
  const [catalogs, setCatalogs] = useState<QuestionCatalogs>()
  const [categories, setCategories] = useState<Array<{ id: string; name: string; organizationName?: string }>>([])
  const [organizations, setOrganizations] = useState<OrganizationSummary[]>([])
  const [years, setYears] = useState<number[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string>()
  const [reloadKey, setReloadKey] = useState(0)
  const [busyId, setBusyId] = useState<string>()
  const [pendingAction, setPendingAction] = useState<PendingAction>(null)
  const [cloneState, setCloneState] = useState<CloneState>()
  const reload = useCallback(() => setReloadKey((value) => value + 1), [])

  const updateFilter = useCallback((key: string, value: string) => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current)
      if (value) next.set(key, value)
      else next.delete(key)
      next.delete('page')
      return next
    }, { replace: true })
  }, [setSearchParams])

  useEffect(() => {
    const controller = new AbortController()
    Promise.all([
      getQuestionCatalogs(controller.signal),
      getQuestionCreationYears(controller.signal),
      globalAdministrator
        ? searchOrganizations({ status: 'ALL', size: 100, signal: controller.signal }).then((result) => result.content)
        : Promise.resolve([] as OrganizationSummary[])
    ])
      .then(([catalogResponse, yearResponse, organizationResponse]) => {
        setCatalogs(catalogResponse)
        setYears(yearResponse)
        setOrganizations(organizationResponse.filter((organization) => organization.status !== 'DELETED'))
        if (!globalAdministrator) {
          setCategories(catalogResponse.categories.map((category) => ({ id: category.publicId, name: category.name })))
        }
      })
      .catch(() => undefined)
    return () => controller.abort()
  }, [globalAdministrator])

  useEffect(() => {
    if (!globalAdministrator) return
    const controller = new AbortController()
    getCatalogItems('CATEGORIES', {
      status: 'ACTIVE',
      organizationPublicId: organizationPublicId || undefined,
      signal: controller.signal
    })
      .then((values: CatalogItem[]) => {
        setCategories(values.map((category) => ({
          id: category.id,
          name: category.name,
          organizationName: category.organizationName
        })))
        if (categoryPublicId && !values.some((category) => category.id === categoryPublicId)) {
          updateFilter('category', '')
        }
      })
      .catch(() => setCategories([]))
    return () => controller.abort()
  }, [globalAdministrator, organizationPublicId, updateFilter])

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(undefined)
    searchQuestions({
      query: debouncedQuery.trim() || undefined,
      status,
      organizationPublicId: globalAdministrator ? organizationPublicId || undefined : undefined,
      categoryPublicId: categoryPublicId || undefined,
      difficultyCode: difficultyCode || undefined,
      createdYear: creationYear ? Number(creationYear) : undefined,
      page,
      size: 20,
      signal: controller.signal
    })
      .then(setData)
      .catch((requestError: unknown) => {
        if (!controller.signal.aborted) {
          setError(requestError instanceof ApiRequestError
            ? requestError.message
            : 'No fue posible consultar las preguntas.')
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [categoryPublicId, creationYear, debouncedQuery, difficultyCode, globalAdministrator,
    organizationPublicId, page, reloadKey, status])

  const questions = data?.content ?? []
  const hasFilters = Boolean(query || organizationPublicId || categoryPublicId || status !== 'ACTIVE' || difficultyCode || creationYear)

  function clearFilters() {
    const next = new URLSearchParams({ status: 'ACTIVE' })
    setSearchParams(next, { replace: true })
  }

  function setPage(nextPage: number) {
    setSearchParams((current) => {
      const next = new URLSearchParams(current)
      if (nextPage > 0) next.set('page', String(nextPage))
      else next.delete('page')
      return next
    }, { replace: true })
  }

  async function duplicate(question: QuestionSummary) {
    setBusyId(question.publicId)
    try {
      const copy = await duplicateQuestion(question.publicId)
      toast.success('Pregunta duplicada', 'La copia quedó disponible para editarse.')
      navigate(`/admin/questions/${copy.publicId}/edit`)
    } catch (requestError) {
      toast.error('No fue posible duplicar la pregunta', requestError instanceof ApiRequestError ? requestError.message : undefined)
    } finally {
      setBusyId(undefined)
    }
  }

  async function openClone(question: QuestionSummary) {
    setCloneState({ question, loading: true, busy: false, includeDependencies: true, notes: '' })
    try {
      const preview = await getQuestionClonePreview(question.publicId)
      setCloneState((current) => current ? { ...current, preview, loading: false } : current)
    } catch (requestError) {
      setCloneState((current) => current ? {
        ...current,
        loading: false,
        error: requestError instanceof ApiRequestError ? requestError.message : 'No fue posible preparar la clonación.'
      } : current)
    }
  }

  async function executeClone() {
    if (!cloneState || cloneState.busy || !cloneState.preview?.promotable) return
    setCloneState((current) => current ? { ...current, busy: true, error: undefined } : current)
    try {
      const result = await cloneQuestionToGlobal(cloneState.question.publicId, {
        includeDependencies: cloneState.includeDependencies,
        notes: cloneState.notes.trim() || undefined
      })
      toast.success('Pregunta clonada a GLOBAL', 'El contenido original permaneció sin cambios.')
      setCloneState(undefined)
      reload()
      navigate(`/admin/questions/${result.globalQuestionPublicId}`)
    } catch (requestError) {
      setCloneState((current) => current ? {
        ...current,
        busy: false,
        error: requestError instanceof ApiRequestError ? requestError.message : 'No fue posible clonar la pregunta al catálogo global.'
      } : current)
    }
  }

  async function executePendingAction() {
    if (!pendingAction) return
    const { question, type } = pendingAction
    setBusyId(question.publicId)
    try {
      if (type === 'DELETE') {
        await deleteQuestion(question.publicId, question.entityVersion, 'Eliminación administrativa')
        toast.success('Pregunta eliminada')
      } else if (type === 'RESTORE') {
        await restoreQuestion(question.publicId, question.entityVersion)
        toast.success('Pregunta restaurada como archivada')
      } else {
        await changeQuestionStatus(question.publicId, type === 'ACTIVATE' ? 'ACTIVE' : 'ARCHIVED', question.entityVersion)
        toast.success(type === 'ACTIVATE' ? 'Pregunta reactivada' : 'Pregunta archivada')
      }
      setPendingAction(null)
      reload()
    } catch (requestError) {
      const message = requestError instanceof ApiRequestError ? requestError.message : 'No fue posible completar la operación.'
      if (requestError instanceof ApiRequestError && requestError.status === 409) {
        toast.warning('La pregunta cambió mientras trabajabas', message)
        reload()
      } else toast.error('Operación no completada', message)
    } finally {
      setBusyId(undefined)
    }
  }

  const dialogTitle = pendingAction?.type === 'DELETE' ? 'Eliminar pregunta'
    : pendingAction?.type === 'RESTORE' ? 'Restaurar pregunta'
      : pendingAction?.type === 'ARCHIVE' ? 'Archivar pregunta' : 'Reactivar pregunta'
  const dialogDescription = pendingAction?.type === 'DELETE'
    ? 'La pregunta dejará de aparecer en el banco normal. El registro y su historial permanecerán almacenados.'
    : pendingAction?.type === 'RESTORE' ? 'La pregunta volverá como archivada.'
      : pendingAction?.type === 'ARCHIVE' ? 'La pregunta dejará de estar disponible para contenido nuevo.'
        : 'La pregunta volverá a estar disponible.'

  return (
    <main className="content-page resource-page ns-list-page question-bank-page">
      <header className="ns-page-header">
        <div>
          <p className="eyebrow">Banco de Preguntas</p>
          <h1>{globalAdministrator ? 'Banco de Preguntas Global' : 'Banco de Preguntas'}</h1>
          <p className="muted">{globalAdministrator
            ? 'Consulta el contenido global y organizacional con filtros compactos y contexto explícito.'
            : 'Consulta y administra únicamente las preguntas propias de tu organización.'}</p>
        </div>
        {permissions.has('QUESTION_CREATE') && <Link className="primary-button button-link" to="/admin/questions/new"><Icon name="plus" size={16} /> Nueva pregunta</Link>}
      </header>

      <FilterToolbar resultLabel={`${data?.totalElements ?? 0} ${data?.totalElements === 1 ? 'pregunta' : 'preguntas'}`} hasActiveFilters={hasFilters} onClear={clearFilters}>
        <ResourceSearchField value={query} onChange={(value) => updateFilter('q', value)} placeholder="Buscar por texto de la pregunta" />
        {globalAdministrator && <ResourceSelectField label="Organización" value={organizationPublicId} onChange={(value) => updateFilter('organization', value)}><option value="">Todas las organizaciones</option>{organizations.map((organization) => <option value={organization.publicId} key={organization.publicId}>{organization.name}</option>)}</ResourceSelectField>}
        <ResourceSelectField label="Categoría" value={categoryPublicId} onChange={(value) => updateFilter('category', value)}><option value="">Todas las categorías</option>{categories.map((category) => <option value={category.id} key={category.id}>{category.name}{globalAdministrator && category.organizationName ? ` · ${category.organizationName}` : ''}</option>)}</ResourceSelectField>
        <ResourceSelectField label="Estado" value={status} onChange={(value) => updateFilter('status', value || 'ALL')}><option value="ACTIVE">Activas</option><option value="ARCHIVED">Archivadas</option><option value="DELETED">Eliminadas</option><option value="">Todas</option></ResourceSelectField>
        <ResourceSelectField label="Dificultad" value={difficultyCode} onChange={(value) => updateFilter('difficulty', value)}><option value="">Todas las dificultades</option>{catalogs?.difficulties.map((difficulty) => <option value={difficulty.code} key={difficulty.code}>{difficulty.name}</option>)}</ResourceSelectField>
        <ResourceSelectField label="Año de creación" value={creationYear} onChange={(value) => updateFilter('year', value)}><option value="">Todos los años</option>{years.map((year) => <option value={year} key={year}>{year}</option>)}</ResourceSelectField>
      </FilterToolbar>

      {error && <section className="inline-error-panel" role="alert"><div className="inline-error-icon"><Icon name="error" size={20} /></div><div><strong>No fue posible cargar el banco de preguntas</strong><p>{error}</p></div><button className="secondary-button compact-button" type="button" onClick={reload}>Reintentar</button></section>}

      <section className="ns-data-panel" aria-busy={loading}>
        <div className="ns-data-table-wrap">
          <table className="ns-data-table ns-question-table question-governance-table">
            <thead><tr><th>Pregunta</th>{globalAdministrator && <th>Organización</th>}<th>Categoría</th><th>Dificultad</th><th>Estado</th><th>Creación</th><th>Actualización</th><th className="ns-actions-column">Acciones</th></tr></thead>
            <tbody>
              {loading && <tr><td colSpan={globalAdministrator ? 8 : 7} className="ns-table-empty">Consultando preguntas…</td></tr>}
              {!loading && !error && questions.length === 0 && <tr><td colSpan={globalAdministrator ? 8 : 7} className="ns-table-empty"><strong>No encontramos preguntas</strong><span>Ajusta los filtros o crea contenido nuevo.</span></td></tr>}
              {!loading && questions.map((question) => (
                <tr className={question.status === 'DELETED' ? 'ns-row-muted' : ''} key={question.publicId}>
                  <td className="ns-primary-cell question-statement-cell"><strong>{question.statement}</strong><small>{question.typeName}{question.technology?.name ? ` · ${question.technology.name}` : ''}</small></td>
                  {globalAdministrator && <td><strong>{question.ownership.organizationName ?? 'GLOBAL'}</strong><small>{question.ownership.scope}</small></td>}
                  <td>{question.categories.length ? question.categories.map((category) => category.name).join(', ') : 'Sin categoría'}</td>
                  <td>{question.difficultyName ?? 'Sin dificultad'}</td>
                  <td><span className={`status-badge status-${question.status.toLowerCase()}`}>{statusLabel[question.status]}</span></td>
                  <td>{formatDate(question.createdAt)}</td>
                  <td>{formatDate(question.updatedAt ?? question.createdAt)}</td>
                  <td><TableActions>
                    <TableActionLink to={`/admin/questions/${question.publicId}`} label="Ver" icon="eye" />
                    {permissions.has('QUESTION_UPDATE') && question.status !== 'DELETED' && <TableActionLink to={`/admin/questions/${question.publicId}/edit`} label="Editar" icon="edit" tone="primary" />}
                    {permissions.has('QUESTION_DUPLICATE') && question.status !== 'DELETED' && <TableActionButton label="Duplicar" icon="copy" disabled={busyId === question.publicId} onClick={() => void duplicate(question)} />}
                    {globalAdministrator && permissions.has('GLOBAL_CONTENT_PROMOTE') && question.ownership.scope === 'ORGANIZATION' && question.status !== 'DELETED' && <TableActionButton label="Clonar a GLOBAL" icon="copy" tone="primary" onClick={() => void openClone(question)} />}
                    {question.status === 'ACTIVE' && permissions.has('QUESTION_ARCHIVE') && <TableActionButton label="Archivar" icon="archive" onClick={() => setPendingAction({ type: 'ARCHIVE', question })} />}
                    {question.status === 'ARCHIVED' && permissions.has('QUESTION_UPDATE') && <TableActionButton label="Reactivar" icon="restore" onClick={() => setPendingAction({ type: 'ACTIVATE', question })} />}
                    {question.status !== 'DELETED' && permissions.has('QUESTION_ARCHIVE') && <TableActionButton label="Eliminar" icon="trash" tone="danger" onClick={() => setPendingAction({ type: 'DELETE', question })} />}
                    {question.status === 'DELETED' && permissions.has('QUESTION_UPDATE') && <TableActionButton label="Restaurar" icon="restore" onClick={() => setPendingAction({ type: 'RESTORE', question })} />}
                  </TableActions></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="pagination-controls"><button className="secondary-button" disabled={loading || page === 0} type="button" onClick={() => setPage(Math.max(0, page - 1))}>Anterior</button><span>Página {(data?.page ?? 0) + 1} de {Math.max(data?.totalPages ?? 1, 1)}</span><button className="secondary-button" disabled={loading || !data || page + 1 >= data.totalPages} type="button" onClick={() => setPage(page + 1)}>Siguiente</button></div>
      </section>

      <ConfirmDialog open={pendingAction !== null} title={dialogTitle} description={dialogDescription} confirmLabel={pendingAction?.type === 'DELETE' ? 'Eliminar' : pendingAction?.type === 'RESTORE' ? 'Restaurar' : pendingAction?.type === 'ARCHIVE' ? 'Archivar' : 'Reactivar'} tone={pendingAction?.type === 'DELETE' || pendingAction?.type === 'ARCHIVE' ? 'danger' : 'primary'} onCancel={() => setPendingAction(null)} onConfirm={() => void executePendingAction()} />

      {cloneState && <div className="ns-modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.currentTarget === event.target && !cloneState.busy) setCloneState(undefined) }}><section className="ns-modal-card question-clone-dialog" role="dialog" aria-modal="true" aria-labelledby="clone-title"><header><div><p className="eyebrow">Banco de Preguntas Global</p><h2 id="clone-title">Clonar a GLOBAL</h2></div><button className="icon-button" type="button" disabled={cloneState.busy} onClick={() => setCloneState(undefined)}><Icon name="close" /></button></header>{cloneState.loading && <p className="muted">Analizando pregunta, dependencias y posibles duplicados…</p>}{cloneState.error && <div className="inline-error-panel"><div className="inline-error-icon"><Icon name="error" /></div><div><strong>No fue posible preparar la clonación</strong><p>{cloneState.error}</p></div></div>}{cloneState.preview && <><div className="clone-preview-summary"><div><span>Organización de origen</span><strong>{cloneState.question.ownership.organizationName}</strong></div><div><span>Versión</span><strong>{cloneState.question.entityVersion}</strong></div><div><span>Dependencias</span><strong>{cloneState.preview.dependencies.length}</strong></div><div><span>Posibles duplicados</span><strong>{cloneState.preview.possibleDuplicates.length}</strong></div></div><p className="clone-source-statement">{cloneState.question.statement}</p>{cloneState.preview.warnings.length > 0 && <ul className="clone-warning-list">{cloneState.preview.warnings.map((warning) => <li key={warning}>{warning}</li>)}</ul>}<label className="org-check-row"><input type="checkbox" checked={cloneState.includeDependencies} onChange={(event) => setCloneState((current) => current ? { ...current, includeDependencies: event.target.checked } : current)} />Clonar también las dependencias necesarias</label><label className="ns-field"><span>Notas de clonación</span><textarea rows={3} maxLength={1000} value={cloneState.notes} onChange={(event) => setCloneState((current) => current ? { ...current, notes: event.target.value } : current)} /></label><p className="muted">Se creará un registro GLOBAL nuevo. La pregunta original permanecerá en su organización sin cambios.</p></>}<footer><button className="secondary-button" type="button" disabled={cloneState.busy} onClick={() => setCloneState(undefined)}>Cancelar</button><button className="primary-button" type="button" disabled={cloneState.busy || cloneState.loading || !cloneState.preview?.promotable} onClick={() => void executeClone()}>{cloneState.busy ? 'Clonando…' : 'Confirmar clonación'}</button></footer></section></div>}
    </main>
  )
}
