import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../features/authentication/context/AuthContext'
import {
  changeQuestionStatus,
  deleteQuestion,
  duplicateQuestion,
  getQuestionCatalogs,
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

function monthYear(value: string) {
  return new Intl.DateTimeFormat('es-MX', { month: 'short', year: 'numeric' })
    .format(new Date(value))
    .replace('.', '')
}

function usageText(items: QuestionSummary['forms'], empty: string) {
  if (items.length === 0) return empty
  if (items.length === 1) return items[0]?.name ?? empty
  return `${items[0]?.name ?? empty} +${items.length - 1}`
}

export function AdminQuestionsPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()
  const permissions = useMemo(() => new Set(user?.permissions ?? []), [user])
  const [data, setData] = useState<QuestionPage>()
  const [catalogs, setCatalogs] = useState<QuestionCatalogs>()
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState<QuestionStatus | ''>('')
  const [typeCode, setTypeCode] = useState<QuestionTypeCode | ''>('')
  const [categoryPublicId, setCategoryPublicId] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string>()
  const [reloadKey, setReloadKey] = useState(0)
  const [busyId, setBusyId] = useState<string>()
  const [pendingAction, setPendingAction] = useState<PendingAction>(null)
  const reload = useCallback(() => setReloadKey((value) => value + 1), [])

  useEffect(() => {
    const controller = new AbortController()
    getQuestionCatalogs(controller.signal).then(setCatalogs).catch(() => undefined)
    return () => controller.abort()
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    const timer = window.setTimeout(() => {
      setLoading(true)
      setError(undefined)
      searchQuestions({
        query: query.trim() || undefined,
        status,
        typeCode,
        categoryPublicId,
        size: 50,
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
  }, [query, status, typeCode, categoryPublicId, reloadKey])

  const questions = data?.content ?? []
  const hasFilters = Boolean(query || status || typeCode || categoryPublicId)

  async function duplicate(question: QuestionSummary) {
    setBusyId(question.publicId)
    try {
      const copy = await duplicateQuestion(question.publicId)
      toast.success('Pregunta duplicada', 'La copia quedó disponible para editarse.')
      navigate(`/admin/questions/${copy.publicId}/edit`)
    } catch (requestError) {
      toast.error(
        'No fue posible duplicar la pregunta',
        requestError instanceof ApiRequestError ? requestError.message : undefined
      )
    } finally {
      setBusyId(undefined)
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
        await changeQuestionStatus(
          question.publicId,
          type === 'ACTIVATE' ? 'ACTIVE' : 'ARCHIVED',
          question.entityVersion
        )
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
      } else {
        toast.error('Operación no completada', message)
      }
    } finally {
      setBusyId(undefined)
    }
  }

  const dialogTitle = pendingAction?.type === 'DELETE'
    ? 'Eliminar pregunta'
    : pendingAction?.type === 'RESTORE'
      ? 'Restaurar pregunta'
      : pendingAction?.type === 'ARCHIVE'
        ? 'Archivar pregunta'
        : 'Reactivar pregunta'
  const dialogDescription = pendingAction?.type === 'DELETE'
    ? 'La pregunta dejará de aparecer en el banco normal y en contenido nuevo. El registro permanecerá almacenado.'
    : pendingAction?.type === 'RESTORE'
      ? 'La pregunta volverá como archivada. Podrás reactivarla cuando esté lista.'
      : pendingAction?.type === 'ARCHIVE'
        ? 'La pregunta dejará de estar disponible para contenido nuevo.'
        : 'La pregunta volverá a estar disponible.'

  return (
    <main className="content-page resource-page ns-list-page">
      <header className="ns-page-header">
        <div>
          <p className="eyebrow">Contenido</p>
          <h1>Banco de preguntas</h1>
          <p className="muted">Preguntas Java organizadas por categorías y reutilizadas en formularios.</p>
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
        onClear={() => {
          setQuery('')
          setStatus('')
          setTypeCode('')
          setCategoryPublicId('')
        }}
      >
        <ResourceSearchField
          value={query}
          onChange={setQuery}
          placeholder="Buscar en enunciado, opciones, código Java o categoría"
        />
        <ResourceSelectField label="Tipo" value={typeCode} onChange={(value) => setTypeCode(value as QuestionTypeCode | '')}>
          <option value="">Todos</option>
          {catalogs?.types.map((type) => <option value={type.code} key={type.code}>{type.name}</option>)}
        </ResourceSelectField>
        <ResourceSelectField label="Categoría" value={categoryPublicId} onChange={setCategoryPublicId}>
          <option value="">Todas</option>
          {catalogs?.categories.map((category) => (
            <option value={category.publicId} key={category.publicId}>{category.name}</option>
          ))}
        </ResourceSelectField>
        <ResourceSelectField label="Estado" value={status} onChange={(value) => setStatus(value as QuestionStatus | '')}>
          <option value="">Disponibles</option>
          <option value="ACTIVE">Activas</option>
          <option value="ARCHIVED">Archivadas</option>
          <option value="DELETED">Eliminadas</option>
        </ResourceSelectField>
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
          <table className="ns-data-table ns-question-table">
            <thead>
              <tr>
                <th>Pregunta</th>
                <th>Tipo</th>
                <th>Categorías</th>
                <th>Creada</th>
                <th>Uso actual</th>
                <th>Estado</th>
                <th className="ns-actions-column">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {loading && (
                <tr><td colSpan={7} className="ns-table-empty">Consultando preguntas…</td></tr>
              )}
              {!loading && !error && questions.length === 0 && (
                <tr>
                  <td colSpan={7} className="ns-table-empty">
                    <strong>{hasFilters ? 'No encontramos coincidencias' : 'Aún no hay preguntas'}</strong>
                    <span>{hasFilters ? 'Ajusta o limpia los filtros.' : 'Crea la primera pregunta para comenzar.'}</span>
                  </td>
                </tr>
              )}
              {!loading && questions.map((question) => {
                const deleted = question.status === 'DELETED'
                const archived = question.status === 'ARCHIVED'
                return (
                  <tr className={deleted ? 'ns-row-muted' : ''} key={question.publicId}>
                    <td className="ns-primary-cell">
                      <strong title={question.statement}>{question.statement}</strong>
                      <small>
                        {question.hasCode && <span><Icon name="code" size={12} /> Java</span>}
                        {question.hasMedia && <span><Icon name="image" size={12} /> Imagen</span>}
                      </small>
                    </td>
                    <td>{question.typeName}</td>
                    <td>
                      <div className="ns-chip-list">
                        {question.categories.slice(0, 2).map((category) => (
                          <span className="ns-chip" key={category.publicId}>{category.name}</span>
                        ))}
                        {question.categories.length > 2 && <span className="ns-chip ns-chip-muted">+{question.categories.length - 2}</span>}
                      </div>
                    </td>
                    <td><time dateTime={question.createdAt}>{monthYear(question.createdAt)}</time></td>
                    <td className="ns-usage-cell">
                      <span><b>Formulario:</b> {usageText(question.forms, 'Sin formulario')}</span>
                      <span><b>Colección:</b> {usageText(question.collections, 'Sin colección')}</span>
                    </td>
                    <td>
                      <span className={`status-badge status-${question.status.toLowerCase()}`}>
                        {statusLabel[question.status]}
                      </span>
                    </td>
                    <td>
                      <TableActions>
                        <TableActionLink to={`/admin/questions/${question.publicId}`} label="Ver" icon="eye" />
                        {!deleted && permissions.has('QUESTION_UPDATE') && (
                          <TableActionLink to={`/admin/questions/${question.publicId}/edit`} label="Editar" icon="edit" tone="primary" />
                        )}
                        {!deleted && permissions.has('QUESTION_DUPLICATE') && (
                          <TableActionButton
                            disabled={busyId === question.publicId}
                            label="Duplicar"
                            icon="copy"
                            onClick={() => void duplicate(question)}
                          />
                        )}
                        {!deleted && (archived ? permissions.has('QUESTION_UPDATE') : permissions.has('QUESTION_ARCHIVE')) && (
                          <TableActionButton
                            disabled={busyId === question.publicId}
                            label={archived ? 'Reactivar' : 'Archivar'}
                            icon={archived ? 'restore' : 'archive'}
                            onClick={() => setPendingAction({ type: archived ? 'ACTIVATE' : 'ARCHIVE', question })}
                          />
                        )}
                        {!deleted && permissions.has('QUESTION_ARCHIVE') && (
                          <TableActionButton
                            disabled={busyId === question.publicId}
                            label="Eliminar"
                            icon="trash"
                            tone="danger"
                            onClick={() => setPendingAction({ type: 'DELETE', question })}
                          />
                        )}
                        {deleted && permissions.has('QUESTION_UPDATE') && (
                          <TableActionButton
                            disabled={busyId === question.publicId}
                            label="Restaurar"
                            icon="restore"
                            tone="primary"
                            onClick={() => setPendingAction({ type: 'RESTORE', question })}
                          />
                        )}
                      </TableActions>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </section>

      <ConfirmDialog
        open={pendingAction !== null}
        title={dialogTitle}
        description={dialogDescription}
        confirmLabel={pendingAction?.type === 'DELETE' ? 'Eliminar' : pendingAction?.type === 'RESTORE' ? 'Restaurar' : pendingAction?.type === 'ARCHIVE' ? 'Archivar' : 'Reactivar'}
        tone={pendingAction?.type === 'DELETE' || pendingAction?.type === 'ARCHIVE' ? 'danger' : 'primary'}
        busy={pendingAction !== null && busyId === pendingAction.question.publicId}
        onCancel={() => {
          if (!busyId) setPendingAction(null)
        }}
        onConfirm={() => void executePendingAction()}
      />
    </main>
  )
}
