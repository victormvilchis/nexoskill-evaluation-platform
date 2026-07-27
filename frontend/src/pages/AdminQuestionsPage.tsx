import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
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
  restoreQuestion,
  searchQuestions
} from '../features/questions/api/questionApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { ConfirmDialog } from '../shared/components/ConfirmDialog'
import { FilterToolbar } from '../shared/components/FilterToolbar'
import { Icon } from '../shared/components/Icon'
import {
  ResourceSearchField,
  ResourceSelectField
} from '../shared/components/ResourceFilters'
import {
  TableActionButton,
  TableActionLink,
  TableActions
} from '../shared/components/TableActions'
import { useToast } from '../shared/components/ToastProvider'
import type {
  CloneToGlobalPreview,
  ContentScope,
  QuestionCatalogs,
  QuestionPage,
  QuestionStatus,
  QuestionSummary,
  QuestionTypeCode
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

function booleanFilter(value: string): boolean | '' {
  if (value === 'true') return true
  if (value === 'false') return false
  return ''
}

export function AdminQuestionsPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()
  const permissions = useMemo(() => new Set(user?.permissions ?? []), [user])
  const globalAdministrator = Boolean(user?.roles.includes('ADMINISTRATOR'))

  const [data, setData] = useState<QuestionPage>()
  const [catalogs, setCatalogs] = useState<QuestionCatalogs>()
  const [organizations, setOrganizations] = useState<OrganizationSummary[]>([])
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState<QuestionStatus | ''>('ACTIVE')
  const [scope, setScope] = useState<ContentScope | ''>(globalAdministrator ? 'GLOBAL' : '')
  const [organizationPublicId, setOrganizationPublicId] = useState('')
  const [typeCode, setTypeCode] = useState<QuestionTypeCode | ''>('')
  const [categoryPublicId, setCategoryPublicId] = useState('')
  const [technologyPublicId, setTechnologyPublicId] = useState('')
  const [difficultyCode, setDifficultyCode] = useState('')
  const [levelCode, setLevelCode] = useState('')
  const [creatorPublicId, setCreatorPublicId] = useState('')
  const [createdFrom, setCreatedFrom] = useState('')
  const [createdTo, setCreatedTo] = useState('')
  const [updatedFrom, setUpdatedFrom] = useState('')
  const [updatedTo, setUpdatedTo] = useState('')
  const [clonedFilter, setClonedFilter] = useState('')
  const [usageFilter, setUsageFilter] = useState('')
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string>()
  const [reloadKey, setReloadKey] = useState(0)
  const [busyId, setBusyId] = useState<string>()
  const [pendingAction, setPendingAction] = useState<PendingAction>(null)
  const [cloneState, setCloneState] = useState<CloneState>()
  const reload = useCallback(() => setReloadKey((value) => value + 1), [])

  useEffect(() => {
    const controller = new AbortController()
    getQuestionCatalogs(controller.signal).then(setCatalogs).catch(() => undefined)
    if (globalAdministrator) {
      searchOrganizations({ status: 'ALL', size: 100, signal: controller.signal })
        .then((result) => setOrganizations(result.content))
        .catch(() => undefined)
    }
    return () => controller.abort()
  }, [globalAdministrator])

  useEffect(() => {
    setPage(0)
  }, [query, status, scope, organizationPublicId, typeCode, categoryPublicId,
    technologyPublicId, difficultyCode, levelCode, creatorPublicId, createdFrom,
    createdTo, updatedFrom, updatedTo, clonedFilter, usageFilter])

  useEffect(() => {
    const controller = new AbortController()
    const timer = window.setTimeout(() => {
      setLoading(true)
      setError(undefined)
      searchQuestions({
        query: query.trim() || undefined,
        status,
        scope: globalAdministrator ? scope : undefined,
        organizationPublicId: globalAdministrator ? organizationPublicId || undefined : undefined,
        typeCode,
        categoryPublicId,
        technologyPublicId,
        difficultyCode,
        levelCode: levelCode.trim() || undefined,
        creatorPublicId: creatorPublicId.trim() || undefined,
        createdFrom: createdFrom || undefined,
        createdTo: createdTo || undefined,
        updatedFrom: updatedFrom || undefined,
        updatedTo: updatedTo || undefined,
        clonedToGlobal: booleanFilter(clonedFilter),
        inUse: booleanFilter(usageFilter),
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
    }, 280)

    return () => {
      window.clearTimeout(timer)
      controller.abort()
    }
  }, [query, status, scope, organizationPublicId, typeCode, categoryPublicId,
    technologyPublicId, difficultyCode, levelCode, creatorPublicId, createdFrom,
    createdTo, updatedFrom, updatedTo, clonedFilter, usageFilter, page, reloadKey,
    globalAdministrator])

  const questions = data?.content ?? []
  const hasFilters = Boolean(
    query || status !== 'ACTIVE' || typeCode || categoryPublicId || technologyPublicId ||
    difficultyCode || levelCode || creatorPublicId || createdFrom || createdTo || updatedFrom ||
    updatedTo || clonedFilter || usageFilter || organizationPublicId ||
    (globalAdministrator && scope !== 'GLOBAL')
  )

  function clearFilters() {
    setQuery('')
    setStatus('ACTIVE')
    setScope(globalAdministrator ? 'GLOBAL' : '')
    setOrganizationPublicId('')
    setTypeCode('')
    setCategoryPublicId('')
    setTechnologyPublicId('')
    setDifficultyCode('')
    setLevelCode('')
    setCreatorPublicId('')
    setCreatedFrom('')
    setCreatedTo('')
    setUpdatedFrom('')
    setUpdatedTo('')
    setClonedFilter('')
    setUsageFilter('')
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
        error: requestError instanceof ApiRequestError
          ? requestError.message
          : 'No fue posible preparar la clonación.'
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
        error: requestError instanceof ApiRequestError
          ? requestError.message
          : 'No fue posible clonar la pregunta al catálogo global.'
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
        await changeQuestionStatus(question.publicId,
          type === 'ACTIVATE' ? 'ACTIVE' : 'ARCHIVED', question.entityVersion)
        toast.success(type === 'ACTIVATE' ? 'Pregunta reactivada' : 'Pregunta archivada')
      }
      setPendingAction(null)
      reload()
    } catch (requestError) {
      const message = requestError instanceof ApiRequestError
        ? requestError.message
        : 'No fue posible completar la operación.'
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
          <p className="muted">
            {globalAdministrator
              ? 'Administra el catálogo GLOBAL y revisa contenido organizacional mediante filtros explícitos.'
              : 'Consulta y administra únicamente las preguntas propias de tu organización.'}
          </p>
        </div>
        {permissions.has('QUESTION_CREATE') && (
          <Link className="primary-button button-link" to="/admin/questions/new">
            <Icon name="plus" size={16} /> Nueva pregunta
          </Link>
        )}
      </header>

      <FilterToolbar
        resultLabel={`${data?.totalElements ?? 0} ${data?.totalElements === 1 ? 'pregunta' : 'preguntas'}`}
        hasActiveFilters={hasFilters}
        onClear={clearFilters}
      >
        <ResourceSearchField value={query} onChange={setQuery} placeholder="Buscar enunciado, opciones, código u organización" />
        {globalAdministrator && (
          <ResourceSelectField label="Alcance" value={scope} onChange={(value) => {
            setScope(value as ContentScope | '')
            if (value === 'GLOBAL') setOrganizationPublicId('')
          }}>
            <option value="GLOBAL">GLOBAL</option>
            <option value="ORGANIZATION">Organizacional</option>
            <option value="">Todos los alcances</option>
          </ResourceSelectField>
        )}
        {globalAdministrator && (
          <ResourceSelectField label="Organización" value={organizationPublicId} onChange={setOrganizationPublicId}>
            <option value="">Todas las organizaciones</option>
            {organizations.map((organization) => (
              <option value={organization.publicId} key={organization.publicId}>{organization.name}</option>
            ))}
          </ResourceSelectField>
        )}
        <ResourceSelectField label="Tecnología" value={technologyPublicId} onChange={setTechnologyPublicId}>
          <option value="">Todas</option>
          {catalogs?.technologies.map((technology) => <option value={technology.publicId} key={technology.publicId}>{technology.name}</option>)}
        </ResourceSelectField>
        <ResourceSelectField label="Categoría" value={categoryPublicId} onChange={setCategoryPublicId}>
          <option value="">Todas</option>
          {catalogs?.categories.map((category) => (
            <option value={category.publicId} key={category.publicId}>{category.name}{globalAdministrator && category.ownerOrganizationName ? ` · ${category.ownerOrganizationName}` : ''}</option>
          ))}
        </ResourceSelectField>
        <ResourceSelectField label="Estado" value={status} onChange={(value) => setStatus(value as QuestionStatus | '')}>
          <option value="ACTIVE">Activas</option>
          <option value="ARCHIVED">Archivadas</option>
          <option value="DELETED">Eliminadas</option>
          <option value="">Todas</option>
        </ResourceSelectField>
        <ResourceSelectField label="Tipo" value={typeCode} onChange={(value) => setTypeCode(value as QuestionTypeCode | '')}>
          <option value="">Todos</option>
          {catalogs?.types.map((type) => <option value={type.code} key={type.code}>{type.name}</option>)}
        </ResourceSelectField>
        <ResourceSelectField label="Dificultad" value={difficultyCode} onChange={setDifficultyCode}>
          <option value="">Todas</option>
          {catalogs?.difficulties.map((difficulty) => <option value={difficulty.code} key={difficulty.code}>{difficulty.name}</option>)}
        </ResourceSelectField>
        <ResourceSelectField label="Uso" value={usageFilter} onChange={setUsageFilter}>
          <option value="">Todas</option><option value="true">En uso</option><option value="false">Sin uso</option>
        </ResourceSelectField>
        {globalAdministrator && (
          <ResourceSelectField label="Origen" value={clonedFilter} onChange={setClonedFilter}>
            <option value="">Todo origen</option><option value="true">Clonadas a GLOBAL</option><option value="false">Sin clonación</option>
          </ResourceSelectField>
        )}
        <label className="ns-resource-field question-filter-text"><span>Nivel</span><input value={levelCode} onChange={(event) => setLevelCode(event.target.value)} placeholder="Ej. JR" /></label>
        {globalAdministrator && <label className="ns-resource-field question-filter-text"><span>Usuario creador</span><input value={creatorPublicId} onChange={(event) => setCreatorPublicId(event.target.value)} placeholder="Public ID" /></label>}
        <label className="ns-resource-field"><span>Creada desde</span><input type="date" value={createdFrom} onChange={(event) => setCreatedFrom(event.target.value)} /></label>
        <label className="ns-resource-field"><span>Creada hasta</span><input type="date" value={createdTo} onChange={(event) => setCreatedTo(event.target.value)} /></label>
        <label className="ns-resource-field"><span>Actualizada desde</span><input type="date" value={updatedFrom} onChange={(event) => setUpdatedFrom(event.target.value)} /></label>
        <label className="ns-resource-field"><span>Actualizada hasta</span><input type="date" value={updatedTo} onChange={(event) => setUpdatedTo(event.target.value)} /></label>
      </FilterToolbar>

      {error && (
        <section className="inline-error-panel" role="alert">
          <div className="inline-error-icon"><Icon name="error" size={20} /></div>
          <div><strong>No fue posible cargar el banco de preguntas</strong><p>{error}</p></div>
          <button className="secondary-button compact-button" onClick={reload}>Reintentar</button>
        </section>
      )}

      <section className="ns-data-panel" aria-busy={loading}>
        <div className="ns-data-table-wrap">
          <table className="ns-data-table ns-question-table question-governance-table">
            <thead>
              <tr>
                <th>Pregunta</th><th>Tecnología</th><th>Categoría</th><th>Alcance / Organización</th>
                <th>Tipo / Dificultad</th><th>Uso</th><th>Actualización</th><th>Estado</th><th className="ns-actions-column">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {loading && <tr><td colSpan={9} className="ns-table-empty">Consultando preguntas…</td></tr>}
              {!loading && !error && questions.length === 0 && (
                <tr><td colSpan={9} className="ns-table-empty"><strong>No encontramos preguntas</strong><span>Ajusta los filtros o crea contenido nuevo.</span></td></tr>
              )}
              {!loading && questions.map((question) => (
                <tr className={question.status === 'DELETED' ? 'ns-row-muted' : ''} key={question.publicId}>
                  <td className="ns-primary-cell question-statement-cell"><strong>{question.statement}</strong><small>{question.levelCode ? `Nivel ${question.levelCode}` : 'Sin nivel'}{question.ownership.creatorName ? ` · ${question.ownership.creatorName}` : ''}</small></td>
                  <td>{question.technology?.name ?? 'Sin tecnología'}</td>
                  <td>{question.categories.length ? question.categories.map((category) => category.name).join(', ') : 'Sin categoría'}</td>
                  <td><span className={`scope-badge scope-${question.ownership.scope.toLowerCase()}`}>{question.ownership.scope}</span><small className="question-owner-name">{question.ownership.organizationName ?? 'Sin propietario'}</small>{question.ownership.clonedToGlobal && <small className="question-lineage">Origen: {question.ownership.sourceOrganizationName}</small>}</td>
                  <td><strong>{question.typeName}</strong><small>{question.difficultyName ?? 'Sin dificultad'}</small></td>
                  <td><span className={`usage-badge ${question.inUse ? 'is-used' : ''}`}>{question.inUse ? 'En uso' : 'Sin uso'}</span></td>
                  <td>{formatDate(question.updatedAt ?? question.createdAt)}</td>
                  <td><span className={`status-badge status-${question.status.toLowerCase()}`}>{statusLabel[question.status]}</span></td>
                  <td>
                    <TableActions>
                      <TableActionLink to={`/admin/questions/${question.publicId}`} label="Ver" icon="eye" />
                      {permissions.has('QUESTION_UPDATE') && question.status !== 'DELETED' && (
                        <TableActionLink to={`/admin/questions/${question.publicId}/edit`} label="Editar" icon="edit" tone="primary" />
                      )}
                      {permissions.has('QUESTION_DUPLICATE') && question.status !== 'DELETED' && (
                        <TableActionButton label="Duplicar" icon="copy" disabled={busyId === question.publicId} onClick={() => void duplicate(question)} />
                      )}
                      {globalAdministrator && permissions.has('GLOBAL_CONTENT_PROMOTE') && question.ownership.scope === 'ORGANIZATION' && question.status !== 'DELETED' && (
                        <TableActionButton label="Clonar a GLOBAL" icon="copy" tone="primary" onClick={() => void openClone(question)} />
                      )}
                      {question.status === 'ACTIVE' && permissions.has('QUESTION_ARCHIVE') && (
                        <TableActionButton label="Archivar" icon="archive" onClick={() => setPendingAction({ type: 'ARCHIVE', question })} />
                      )}
                      {question.status === 'ARCHIVED' && permissions.has('QUESTION_UPDATE') && (
                        <TableActionButton label="Reactivar" icon="restore" onClick={() => setPendingAction({ type: 'ACTIVATE', question })} />
                      )}
                      {question.status !== 'DELETED' && permissions.has('QUESTION_ARCHIVE') && (
                        <TableActionButton label="Eliminar" icon="trash" tone="danger" onClick={() => setPendingAction({ type: 'DELETE', question })} />
                      )}
                      {question.status === 'DELETED' && permissions.has('QUESTION_UPDATE') && (
                        <TableActionButton label="Restaurar" icon="restore" onClick={() => setPendingAction({ type: 'RESTORE', question })} />
                      )}
                    </TableActions>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="pagination-controls">
          <button className="secondary-button" disabled={loading || page === 0} onClick={() => setPage((current) => Math.max(0, current - 1))}>Anterior</button>
          <span>Página {(data?.page ?? 0) + 1} de {Math.max(data?.totalPages ?? 1, 1)}</span>
          <button className="secondary-button" disabled={loading || !data || page + 1 >= data.totalPages} onClick={() => setPage((current) => current + 1)}>Siguiente</button>
        </div>
      </section>

      <ConfirmDialog
        open={pendingAction !== null}
        title={dialogTitle}
        description={dialogDescription}
        confirmLabel={pendingAction?.type === 'DELETE' ? 'Eliminar' : pendingAction?.type === 'RESTORE' ? 'Restaurar' : pendingAction?.type === 'ARCHIVE' ? 'Archivar' : 'Reactivar'}
        tone={pendingAction?.type === 'DELETE' || pendingAction?.type === 'ARCHIVE' ? 'danger' : 'primary'}
        onCancel={() => setPendingAction(null)}
        onConfirm={() => void executePendingAction()}
      />

      {cloneState && (
        <div className="ns-modal-backdrop" role="presentation" onMouseDown={(event) => {
          if (event.currentTarget === event.target && !cloneState.busy) setCloneState(undefined)
        }}>
          <section className="ns-modal-card question-clone-dialog" role="dialog" aria-modal="true" aria-labelledby="clone-title">
            <header><div><p className="eyebrow">Banco de Preguntas Global</p><h2 id="clone-title">Clonar a GLOBAL</h2></div><button className="icon-button" type="button" disabled={cloneState.busy} onClick={() => setCloneState(undefined)}><Icon name="close" /></button></header>
            {cloneState.loading && <p className="muted">Analizando pregunta, dependencias y posibles duplicados…</p>}
            {cloneState.error && <div className="inline-error-panel"><div className="inline-error-icon"><Icon name="error" /></div><div><strong>No fue posible preparar la clonación</strong><p>{cloneState.error}</p></div></div>}
            {cloneState.preview && (
              <>
                <div className="clone-preview-summary">
                  <div><span>Organización de origen</span><strong>{cloneState.question.ownership.organizationName}</strong></div>
                  <div><span>Versión</span><strong>{cloneState.question.entityVersion}</strong></div>
                  <div><span>Dependencias</span><strong>{cloneState.preview.dependencies.length}</strong></div>
                  <div><span>Posibles duplicados</span><strong>{cloneState.preview.possibleDuplicates.length}</strong></div>
                </div>
                <p className="clone-source-statement">{cloneState.question.statement}</p>
                {cloneState.preview.warnings.length > 0 && <ul className="clone-warning-list">{cloneState.preview.warnings.map((warning) => <li key={warning}>{warning}</li>)}</ul>}
                <label className="org-check-row"><input type="checkbox" checked={cloneState.includeDependencies} onChange={(event) => setCloneState((current) => current ? { ...current, includeDependencies: event.target.checked } : current)} />Clonar también las dependencias necesarias</label>
                <label className="ns-field"><span>Notas de clonación</span><textarea rows={3} maxLength={1000} value={cloneState.notes} onChange={(event) => setCloneState((current) => current ? { ...current, notes: event.target.value } : current)} /></label>
                <p className="muted">Se creará un registro GLOBAL nuevo. La pregunta original permanecerá en su organización sin cambios.</p>
              </>
            )}
            <footer><button className="secondary-button" type="button" disabled={cloneState.busy} onClick={() => setCloneState(undefined)}>Cancelar</button><button className="primary-button" type="button" disabled={cloneState.busy || cloneState.loading || !cloneState.preview?.promotable} onClick={() => void executeClone()}>{cloneState.busy ? 'Clonando…' : 'Confirmar clonación'}</button></footer>
          </section>
        </div>
      )}
    </main>
  )
}
