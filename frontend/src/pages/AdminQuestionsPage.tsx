import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { getCatalogItems } from '../features/catalogs/api/catalogApi'
import type { CatalogItem } from '../features/catalogs/types/catalogs'
import { useAuth } from '../features/authentication/context/AuthContext'
import { getAllOrganizations } from '../features/organizations/api/organizationApi'
import type { OrganizationSummary } from '../features/organizations/types/organizations'
import {
  changeQuestionStatus,
  cloneQuestionToGlobal,
  createQuestionOrganizationCopy,
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
import { SelectField } from '../shared/components/SelectField'
import { TableActionButton, TableActionLink, TableActions } from '../shared/components/TableActions'
import { TablePagination } from '../shared/components/TablePagination'
import { parsePage, parsePageSize, type PageSize } from '../shared/types/pagination'
import { useToast } from '../shared/components/ToastProvider'
import { useDebouncedValue } from '../shared/hooks/useDebouncedValue'
import type {
  CloneToGlobalPreview,
  ContentScope,
  QuestionCatalogs,
  QuestionPage,
  QuestionStatus,
  QuestionSummary
} from '../shared/types/questions'

const statusLabel: Record<QuestionStatus, string> = {
  ACTIVE: 'Activa',
  ARCHIVED: 'Inactiva',
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

interface DuplicateState {
  question: QuestionSummary
  targetScope: ContentScope
  organizationPublicId: string
  busy: boolean
  error?: string
}

function formatDate(value?: string) {
  if (!value) return 'Sin actualización'
  return new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium' }).format(new Date(value))
}

export function AdminQuestionsPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()
  const [searchParams, setSearchParams] = useSearchParams()
  const permissions = useMemo(() => new Set(user?.permissions ?? []), [user])
  const globalAdministrator = Boolean(user?.roles.includes('ADMINISTRATOR'))
  const organizationalRole = !globalAdministrator
  const query = searchParams.get('q') ?? ''
  const scope = globalAdministrator ? (searchParams.get('scope') ?? '') as ContentScope | '' : ''
  const organizationPublicId = globalAdministrator ? searchParams.get('organization') ?? '' : ''
  const categoryPublicId = searchParams.get('category') ?? ''
  const rawStatus = searchParams.get('status') ?? 'ACTIVE'
  const status = (rawStatus === 'ALL' ? '' : rawStatus) as QuestionStatus | ''
  const difficultyCode = searchParams.get('difficulty') ?? ''
  const creationYear = searchParams.get('year') ?? ''
  const page = parsePage(searchParams.get('page'))
  const size = parsePageSize(searchParams.get('size'))
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
  const [duplicateState, setDuplicateState] = useState<DuplicateState>()
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

  const updateScopeFilter = useCallback((value: string) => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current)
      if (value) next.set('scope', value)
      else next.delete('scope')
      if (value === 'GLOBAL') next.delete('organization')
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
        ? getAllOrganizations({ status: 'ALL', sort: 'name', direction: 'ASC', signal: controller.signal })
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
  }, [categoryPublicId, globalAdministrator, organizationPublicId, updateFilter])

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(undefined)
    searchQuestions({
      query: debouncedQuery.trim() || undefined,
      status,
      scope: globalAdministrator ? scope || undefined : undefined,
      organizationPublicId: globalAdministrator ? organizationPublicId || undefined : undefined,
      categoryPublicId: categoryPublicId || undefined,
      difficultyCode: difficultyCode || undefined,
      createdYear: creationYear ? Number(creationYear) : undefined,
      page,
      size,
      signal: controller.signal
    })
      .then((response) => {
        setData(response)
        if (response.totalPages > 0 && page >= response.totalPages) {
          setPage(response.totalPages - 1)
        }
      })
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
    organizationPublicId, page, reloadKey, scope, size, status])

  const questions = data?.content ?? []
  const hasFilters = Boolean(query || scope || organizationPublicId || categoryPublicId || status !== 'ACTIVE' || difficultyCode || creationYear)
  const canManageQuestion = useCallback((question: QuestionSummary) =>
    globalAdministrator || question.ownership.scope === 'ORGANIZATION', [globalAdministrator])

  function clearFilters() {
    setSearchParams(new URLSearchParams({ status: 'ACTIVE' }), { replace: true })
  }

  function setPage(nextPage: number) {
    setSearchParams((current) => {
      const next = new URLSearchParams(current)
      if (nextPage > 0) next.set('page', String(nextPage))
      else next.delete('page')
      return next
    }, { replace: true })
  }

  function setPageSize(nextSize: PageSize) {
    setSearchParams((current) => {
      const next = new URLSearchParams(current)
      next.delete('page')
      if (nextSize === 10) next.delete('size')
      else next.set('size', String(nextSize))
      return next
    }, { replace: true })
  }

  function requestDuplicate(question: QuestionSummary) {
    if (globalAdministrator && question.ownership.scope === 'GLOBAL') {
      setDuplicateState({ question, targetScope: 'GLOBAL', organizationPublicId: '', busy: false })
      return
    }
    void duplicateWithinCurrentScope(question)
  }

  async function duplicateWithinCurrentScope(question: QuestionSummary) {
    if (busyId === question.publicId) return
    setBusyId(question.publicId)
    try {
      const copy = await duplicateQuestion(question.publicId)
      toast.success('Pregunta duplicada', 'La copia se creó correctamente dentro de tu organización.')
      navigate(`/admin/questions/${copy.publicId}/edit`)
    } catch (requestError) {
      toast.error('No fue posible duplicar la pregunta', requestError instanceof ApiRequestError
        ? requestError.message
        : 'No fue posible duplicar la pregunta dentro del contexto actual.')
    } finally {
      setBusyId(undefined)
    }
  }

  async function executeDuplicate() {
    if (!duplicateState || duplicateState.busy) return
    if (duplicateState.targetScope === 'ORGANIZATION' && !duplicateState.organizationPublicId) {
      setDuplicateState((current) => current ? {
        ...current,
        error: 'Selecciona la organización propietaria de la nueva pregunta.'
      } : current)
      return
    }
    setDuplicateState((current) => current ? { ...current, busy: true, error: undefined } : current)
    try {
      await duplicateQuestion(duplicateState.question.publicId, {
        targetScope: duplicateState.targetScope,
        organizationPublicId: duplicateState.targetScope === 'ORGANIZATION'
          ? duplicateState.organizationPublicId
          : undefined
      })
      toast.success(
        duplicateState.targetScope === 'GLOBAL'
          ? 'La pregunta global se duplicó correctamente.'
          : 'La pregunta se duplicó correctamente para la organización seleccionada.'
      )
      setDuplicateState(undefined)
      reload()
    } catch (requestError) {
      setDuplicateState((current) => current ? {
        ...current,
        busy: false,
        error: requestError instanceof ApiRequestError
          ? requestError.message
          : 'No fue posible duplicar la pregunta.'
      } : current)
    }
  }

  async function createOrganizationCopy(question: QuestionSummary) {
    setBusyId(question.publicId)
    try {
      const copy = await createQuestionOrganizationCopy(question.publicId)
      toast.success('Copia organizacional creada', 'La pregunta quedó disponible para editarse en tu organización.')
      navigate(`/admin/questions/${copy.publicId}/edit`)
    } catch (requestError) {
      toast.error('No fue posible crear la copia organizacional',
        requestError instanceof ApiRequestError ? requestError.message : undefined)
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
      toast.success('Pregunta clonada a GLOBAL', 'La pregunta organizacional original permaneció sin cambios.')
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
        await deleteQuestion(question.publicId, question.entityVersion, 'Eliminación física desde el Banco de preguntas')
        toast.success('Pregunta eliminada', 'La pregunta fue eliminada definitivamente y retirada de todos los formularios.')
      } else if (type === 'RESTORE') {
        await restoreQuestion(question.publicId, question.entityVersion)
        toast.success('Pregunta restaurada como inactiva')
      } else {
        await changeQuestionStatus(question.publicId, type === 'ACTIVATE' ? 'ACTIVE' : 'ARCHIVED', question.entityVersion)
        if (type === 'ACTIVATE') toast.success('Pregunta activada')
        else toast.success('Pregunta inactivada', 'La pregunta fue inactivada y retirada de los formularios donde estaba siendo utilizada.')
      }
      setPendingAction(null)
      reload()
    } catch (requestError) {
      const message = requestError instanceof ApiRequestError ? requestError.message : 'No fue posible completar la operación.'
      if (requestError instanceof ApiRequestError && requestError.status === 409) {
        toast.warning('La pregunta cambió mientras trabajabas', message)
        reload()
      } else {
        const title = type === 'DELETE' ? 'No fue posible eliminar la pregunta'
          : type === 'RESTORE' ? 'No fue posible restaurar la pregunta'
            : type === 'ARCHIVE' ? 'No fue posible inactivar la pregunta'
              : 'No fue posible activar la pregunta'
        toast.error(title, message)
      }
    } finally {
      setBusyId(undefined)
    }
  }

  const dialogTitle = pendingAction?.type === 'DELETE' ? 'Eliminar pregunta'
    : pendingAction?.type === 'RESTORE' ? 'Restaurar pregunta'
      : pendingAction?.type === 'ARCHIVE' ? 'Inactivar pregunta' : 'Activar pregunta'
  const dialogDescription = pendingAction?.type === 'DELETE'
    ? 'Esta acción eliminará permanentemente la pregunta y la retirará de todos los formularios donde actualmente se encuentra utilizada. La pregunta dejará de existir en el Banco de preguntas. ¿Deseas continuar?'
    : pendingAction?.type === 'RESTORE'
      ? 'La pregunta se restaurará como inactiva. Después podrás activarla cuando su configuración sea válida.'
      : pendingAction?.type === 'ARCHIVE'
        ? 'La pregunta dejará de estar disponible y será retirada de todos los formularios donde actualmente se encuentra utilizada. La pregunta continuará registrada en el Banco de preguntas como inactiva. ¿Deseas continuar?'
        : 'La pregunta volverá a estar disponible para configuraciones nuevas.'

  return (
    <main className="content-page resource-page ns-list-page question-bank-page">
      {permissions.has('QUESTION_CREATE') && (
        <div className="ns-list-action-bar" aria-label="Acciones del banco de preguntas">
          <Link className="primary-button button-link ns-create-button" to="/admin/questions/new">
            <Icon name="plus" size={15} /> Crear pregunta
          </Link>
        </div>
      )}

      <FilterToolbar hasActiveFilters={hasFilters} onClear={clearFilters}>
        <ResourceSearchField value={query} onChange={(value) => updateFilter('q', value)} placeholder="Buscar por texto de la pregunta" />
        {globalAdministrator && (
          <ResourceSelectField label="Alcance" value={scope} onChange={updateScopeFilter}>
            <option value="">Todos los alcances</option>
            <option value="GLOBAL">Global</option>
            <option value="ORGANIZATION">Organizacional</option>
          </ResourceSelectField>
        )}
        {globalAdministrator && scope !== 'GLOBAL' && (
          <ResourceSelectField label="Organización" value={organizationPublicId} onChange={(value) => updateFilter('organization', value)}>
            <option value="">Todas las organizaciones</option>
            {organizations.map((organization) => <option value={organization.publicId} key={organization.publicId}>{organization.name}</option>)}
          </ResourceSelectField>
        )}
        <ResourceSelectField label="Categoría" value={categoryPublicId} onChange={(value) => updateFilter('category', value)}>
          <option value="">Todas las categorías</option>
          {categories.map((category) => (
            <option value={category.id} key={category.id}>
              {category.name}{globalAdministrator && category.organizationName ? ` · ${category.organizationName}` : ''}
            </option>
          ))}
        </ResourceSelectField>
        <ResourceSelectField label="Estado" value={status} onChange={(value) => updateFilter('status', value || 'ALL')}>
          <option value="ACTIVE">Activas</option>
          <option value="ARCHIVED">Inactivas</option>
          <option value="DELETED">Eliminadas</option>
          <option value="">Todas</option>
        </ResourceSelectField>
        <ResourceSelectField label="Dificultad" value={difficultyCode} onChange={(value) => updateFilter('difficulty', value)}>
          <option value="">Todas las dificultades</option>
          {catalogs?.difficulties.map((difficulty) => <option value={difficulty.code} key={difficulty.code}>{difficulty.name}</option>)}
        </ResourceSelectField>
        <ResourceSelectField label="Año de creación" value={creationYear} onChange={(value) => updateFilter('year', value)}>
          <option value="">Todos los años</option>
          {years.map((year) => <option value={year} key={year}>{year}</option>)}
        </ResourceSelectField>
      </FilterToolbar>

      {error && (
        <section className="inline-error-panel" role="alert">
          <div className="inline-error-icon"><Icon name="error" size={20} /></div>
          <div><strong>No fue posible cargar el banco de preguntas</strong><p>{error}</p></div>
          <button className="secondary-button compact-button" type="button" onClick={reload}>Reintentar</button>
        </section>
      )}

      <section className="ns-data-panel" aria-busy={loading}>
        <div className="ns-data-table-wrap">
          <table className="ns-data-table ns-question-table question-governance-table">
            <thead>
              <tr>
                <th>Pregunta</th>
                {globalAdministrator && <th>Organización</th>}
                <th>Categoría</th>
                <th>Dificultad</th>
                <th>Creación</th>
                <th>Actualización</th>
                <th>Estado</th>
                <th className="ns-actions-column">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {loading && <tr><td colSpan={globalAdministrator ? 8 : 7} className="ns-table-empty">Consultando preguntas…</td></tr>}
              {!loading && !error && questions.length === 0 && (
                <tr><td colSpan={globalAdministrator ? 8 : 7} className="ns-table-empty"><strong>No encontramos preguntas</strong><span>Ajusta los filtros o crea contenido nuevo.</span></td></tr>
              )}
              {!loading && questions.map((question) => (
                <tr className={question.status === 'DELETED' ? 'ns-row-muted' : ''} key={question.publicId}>
                  <td className="ns-primary-cell question-statement-cell">
                    <strong>{question.statement}</strong>
                    <small>{question.typeName}{question.technology?.name ? ` · ${question.technology.name}` : ''}</small>
                    {question.tags.length > 0 && (
                      <span className="question-tag-summary" aria-label="Etiquetas temáticas">
                        {question.tags.slice(0, 3).map((tag) => (
                          <span className="question-tag-chip question-tag-chip--readonly" key={tag.publicId}>#{tag.slug}</span>
                        ))}
                        {question.tags.length > 3 && <span className="question-tag-more">+{question.tags.length - 3}</span>}
                      </span>
                    )}
                  </td>
                  {globalAdministrator && (
                    <td>
                      <strong>{question.ownership.organizationName ?? 'GLOBAL'}</strong>
                      <small>{question.ownership.scope === 'GLOBAL' ? 'Global' : 'Organizacional'}</small>
                    </td>
                  )}
                  <td>{question.categories.length ? question.categories.map((category) => category.name).join(', ') : 'Sin categoría'}</td>
                  <td>{question.difficultyName ?? 'Sin dificultad'}</td>
                  <td>{formatDate(question.createdAt)}</td>
                  <td>{formatDate(question.updatedAt ?? question.createdAt)}</td>
                  <td><span className={`status-badge status-${question.status.toLowerCase()}`}>{statusLabel[question.status]}</span></td>
                  <td>
                    <TableActions>
                      <TableActionLink to={`/admin/questions/${question.publicId}`} label="Ver" icon="eye" />
                      {organizationalRole && question.ownership.scope === 'GLOBAL' && question.status === 'ACTIVE' && permissions.has('QUESTION_CREATE') && (
                        <TableActionButton label="Crear copia para mi organización" icon="copy" disabled={busyId === question.publicId} onClick={() => void createOrganizationCopy(question)} />
                      )}
                      {canManageQuestion(question) && permissions.has('QUESTION_UPDATE') && question.status !== 'DELETED' && (
                        <TableActionLink to={`/admin/questions/${question.publicId}/edit`} label="Editar" icon="edit" tone="primary" />
                      )}
                      {canManageQuestion(question) && permissions.has('QUESTION_DUPLICATE') && question.status !== 'DELETED'
                        && (!globalAdministrator || question.ownership.scope === 'GLOBAL') && (
                        <TableActionButton label="Duplicar" icon="copy" disabled={busyId === question.publicId} onClick={() => requestDuplicate(question)} />
                      )}
                      {canManageQuestion(question) && permissions.has('QUESTION_ARCHIVE') && question.status === 'ACTIVE' && (
                        <TableActionButton label="Inactivar" icon="archive" disabled={busyId === question.publicId} onClick={() => setPendingAction({ type: 'ARCHIVE', question })} />
                      )}
                      {canManageQuestion(question) && permissions.has('QUESTION_UPDATE') && question.status === 'ARCHIVED' && (
                        <TableActionButton label="Activar" icon="check" disabled={busyId === question.publicId} onClick={() => setPendingAction({ type: 'ACTIVATE', question })} />
                      )}
                      {canManageQuestion(question) && permissions.has('QUESTION_ARCHIVE') && question.status !== 'DELETED' && (
                        <TableActionButton label="Eliminar" icon="trash" tone="danger" disabled={busyId === question.publicId} onClick={() => setPendingAction({ type: 'DELETE', question })} />
                      )}
                      {canManageQuestion(question) && permissions.has('QUESTION_UPDATE') && question.status === 'DELETED' && (
                        <TableActionButton label="Restaurar" icon="restore" disabled={busyId === question.publicId} onClick={() => setPendingAction({ type: 'RESTORE', question })} />
                      )}
                      {globalAdministrator && permissions.has('GLOBAL_CONTENT_PROMOTE') && question.ownership.scope === 'ORGANIZATION' && question.status !== 'DELETED' && (
                        <TableActionButton label="Clonar a GLOBAL" icon="copy" tone="primary" disabled={busyId === question.publicId} onClick={() => void openClone(question)} />
                      )}
                    </TableActions>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <TablePagination
          currentPage={page}
          pageSize={data?.size ?? size}
          totalElements={data?.totalElements ?? 0}
          totalPages={data?.totalPages ?? 0}
          isLoading={loading}
          onPageChange={setPage}
          onPageSizeChange={setPageSize}
        />
      </section>

      <ConfirmDialog
        open={pendingAction !== null}
        title={dialogTitle}
        description={dialogDescription}
        confirmLabel={pendingAction?.type === 'DELETE' ? 'Eliminar'
          : pendingAction?.type === 'RESTORE' ? 'Restaurar'
            : pendingAction?.type === 'ARCHIVE' ? 'Inactivar' : 'Activar'}
        tone={pendingAction?.type === 'DELETE' || pendingAction?.type === 'ARCHIVE' ? 'danger' : 'primary'}
        busy={Boolean(pendingAction && busyId === pendingAction.question.publicId)}
        onCancel={() => setPendingAction(null)}
        onConfirm={() => void executePendingAction()}
      />

      {duplicateState && (
        <div className="ns-modal-backdrop" role="presentation" onMouseDown={(event) => {
          if (event.currentTarget === event.target && !duplicateState.busy) setDuplicateState(undefined)
        }}>
          <section className="ns-modal-card question-duplicate-dialog" role="dialog" aria-modal="true" aria-labelledby="duplicate-title">
            <header>
              <div><p className="eyebrow">Banco de Preguntas</p><h2 id="duplicate-title">Duplicar pregunta global</h2></div>
              <button className="icon-button" type="button" disabled={duplicateState.busy} onClick={() => setDuplicateState(undefined)}><Icon name="close" /></button>
            </header>
            <p className="clone-source-statement">{duplicateState.question.statement}</p>
            <div className="availability-mode" role="radiogroup" aria-label="Destino de la duplicación">
              <label>
                <input type="radio" name="duplicateTarget" checked={duplicateState.targetScope === 'GLOBAL'} disabled={duplicateState.busy}
                  onChange={() => setDuplicateState((current) => current ? { ...current, targetScope: 'GLOBAL', organizationPublicId: '', error: undefined } : current)} />
                <span><strong>Nueva pregunta global</strong><small>La copia será independiente y quedará sin distribución organizacional.</small></span>
              </label>
              <label>
                <input type="radio" name="duplicateTarget" checked={duplicateState.targetScope === 'ORGANIZATION'} disabled={duplicateState.busy}
                  onChange={() => setDuplicateState((current) => current ? { ...current, targetScope: 'ORGANIZATION', error: undefined } : current)} />
                <span><strong>Nueva pregunta organizacional</strong><small>La copia pertenecerá únicamente a la organización seleccionada.</small></span>
              </label>
            </div>
            {duplicateState.targetScope === 'ORGANIZATION' && (
              <label className="ns-field">
                <span>Organización propietaria</span>
                <SelectField value={duplicateState.organizationPublicId} disabled={duplicateState.busy}
                  ariaLabel="Organización propietaria"
                  onChange={(nextValue) => setDuplicateState((current) => current ? { ...current, organizationPublicId: nextValue, error: undefined } : current)}
                  options={[
                    { value: '', label: 'Selecciona una organización' },
                    ...organizations
                      .filter((organization) => organization.status === 'ACTIVE' && organization.organizationType === 'CUSTOMER')
                      .map((organization) => ({ value: organization.publicId, label: `${organization.name} · ${organization.code}` }))
                  ]}
                />
              </label>
            )}
            {duplicateState.error && <p className="error-message" role="alert">{duplicateState.error}</p>}
            <p className="muted">La pregunta original permanecerá sin cambios. La nueva copia conservará enunciado, clasificación, etiquetas, opciones, respuestas y explicación.</p>
            <footer>
              <button className="secondary-button" type="button" disabled={duplicateState.busy} onClick={() => setDuplicateState(undefined)}>Cancelar</button>
              <button className="primary-button" type="button" disabled={duplicateState.busy} onClick={() => void executeDuplicate()}>
                {duplicateState.busy ? 'Duplicando…' : 'Confirmar duplicación'}
              </button>
            </footer>
          </section>
        </div>
      )}

      {cloneState && (
        <div className="ns-modal-backdrop" role="presentation" onMouseDown={(event) => {
          if (event.currentTarget === event.target && !cloneState.busy) setCloneState(undefined)
        }}>
          <section className="ns-modal-card question-clone-dialog" role="dialog" aria-modal="true" aria-labelledby="clone-title">
            <header>
              <div><p className="eyebrow">Banco de Preguntas Global</p><h2 id="clone-title">Clonar a GLOBAL</h2></div>
              <button className="icon-button" type="button" disabled={cloneState.busy} onClick={() => setCloneState(undefined)}><Icon name="close" /></button>
            </header>
            {cloneState.loading && <p className="muted">Analizando pregunta, dependencias y posibles duplicados…</p>}
            {cloneState.error && (
              <div className="inline-error-panel">
                <div className="inline-error-icon"><Icon name="error" /></div>
                <div><strong>No fue posible preparar la clonación</strong><p>{cloneState.error}</p></div>
              </div>
            )}
            {cloneState.preview && (
              <>
                <div className="clone-preview-summary">
                  <div><span>Organización de origen</span><strong>{cloneState.question.ownership.organizationName}</strong></div>
                  <div><span>Versión</span><strong>{cloneState.question.entityVersion}</strong></div>
                  <div><span>Dependencias</span><strong>{cloneState.preview.dependencies.length}</strong></div>
                  <div><span>Posibles duplicados</span><strong>{cloneState.preview.possibleDuplicates.length}</strong></div>
                </div>
                <p className="clone-source-statement">{cloneState.question.statement}</p>
                {cloneState.preview.warnings.length > 0 && (
                  <ul className="clone-warning-list">{cloneState.preview.warnings.map((warning) => <li key={warning}>{warning}</li>)}</ul>
                )}
                <label className="org-check-row">
                  <input type="checkbox" checked={cloneState.includeDependencies} onChange={(event) => setCloneState((current) => current ? { ...current, includeDependencies: event.target.checked } : current)} />
                  Clonar también las dependencias necesarias
                </label>
                <label className="ns-field">
                  <span>Notas de clonación</span>
                  <textarea rows={3} maxLength={1000} value={cloneState.notes} onChange={(event) => setCloneState((current) => current ? { ...current, notes: event.target.value } : current)} />
                </label>
                <p className="muted">Se creará un registro GLOBAL nuevo. La pregunta original permanecerá en su organización sin cambios.</p>
              </>
            )}
            <footer>
              <button className="secondary-button" type="button" disabled={cloneState.busy} onClick={() => setCloneState(undefined)}>Cancelar</button>
              {cloneState.preview && (
                <button className="primary-button" type="button" disabled={cloneState.busy || !cloneState.preview.promotable} onClick={() => void executeClone()}>
                  {cloneState.busy ? 'Clonando…' : 'Confirmar clonación'}
                </button>
              )}
            </footer>
          </section>
        </div>
      )}
    </main>
  )
}
