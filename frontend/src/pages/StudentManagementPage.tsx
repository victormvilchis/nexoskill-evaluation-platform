import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useAuth } from '../features/authentication/context/AuthContext'
import {
  activateStudent,
  assignStudentEvaluation,
  assignStudentPath,
  deactivateStudent,
  getStudentAdministration,
  getAssignableStudentEvaluations,
  getAssignableStudentPaths,
  getAssignedStudentPaths,
  getStudentAdministrativeHistory,
  removeStudentPath,
  resetStudentPassword,
  revokeAllStudentSessions,
  revokeStudentSession
} from '../features/students/api/studentApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { StudentTemporaryCredentialsDialog } from '../features/students/components/StudentTemporaryCredentialsDialog'
import { BackButton } from '../shared/components/BackButton'
import { ConfirmDialog } from '../shared/components/ConfirmDialog'
import { LoadingScreen } from '../shared/components/LoadingScreen'
import { TablePagination } from '../shared/components/TablePagination'
import { useToast } from '../shared/components/ToastProvider'
import type { PageSize } from '../shared/types/pagination'
import type {
  AdministrativeHistoryPage,
  StudentAdministrationView,
  StudentSession,
  StudentEvaluationOption,
  StudentAssignedPath,
  StudentPathOption,
  StudentTemporaryCredentials
} from '../shared/types/students'

type PendingAction = 'ACTIVATE' | 'DEACTIVATE' | 'RESET_PASSWORD'

const STATUS_LABELS = {
  ACTIVE: 'Activo',
  INACTIVE: 'Dado de baja',
  EXPIRED: 'Dado de baja',
  DELETED: 'Eliminado'
} as const

function formatDate(value?: string | null) {
  if (!value) return 'N/A'
  return new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium' }).format(new Date(`${value}T12:00:00`))
}

function formatDateTime(value?: string | null) {
  if (!value) return 'Sin registro'
  return new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

function sessionLabel(session: StudentSession) {
  if (session.status === 'ACTIVE') return 'Activa'
  if (session.status === 'REVOKED') return 'Revocada'
  return 'Vencida'
}

function revocationReasonLabel(reason?: string | null) {
  if (!reason) return undefined
  return ({
    DEACTIVATED: 'Baja',
    EXPIRED: 'Acceso restringido por la organización',
    PASSWORD_RESET: 'Restablecimiento de contraseña',
    ADMIN_REVOKED: 'Revocación administrativa',
    ALL_SESSIONS_REVOKED: 'Revocación administrativa de todas las sesiones'
  } as Record<string, string>)[reason] ?? reason
}

export function StudentManagementPage() {
  const { publicId = '' } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const { user } = useAuth()
  const permissions = useMemo(() => new Set(user?.permissions ?? []), [user])

  const [administration, setAdministration] = useState<StudentAdministrationView>()
  const [history, setHistory] = useState<AdministrativeHistoryPage>({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 })
  const [historyPage, setHistoryPage] = useState(0)
  const [historySize, setHistorySize] = useState<PageSize>(10)
  const [loading, setLoading] = useState(true)
  const [historyLoading, setHistoryLoading] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()
  const [pendingAction, setPendingAction] = useState<PendingAction>()
  const [temporaryCredentials, setTemporaryCredentials] = useState<StudentTemporaryCredentials>()
  const [evaluationOptions, setEvaluationOptions] = useState<StudentEvaluationOption[]>([])
  const [evaluationFormPublicId, setEvaluationFormPublicId] = useState('')
  const [evaluationDueAt, setEvaluationDueAt] = useState('')
  const [evaluationLoading, setEvaluationLoading] = useState(false)
  const [evaluationAssigning, setEvaluationAssigning] = useState(false)
  const [pathOptions, setPathOptions] = useState<StudentPathOption[]>([])
  const [assignedPaths, setAssignedPaths] = useState<StudentAssignedPath[]>([])
  const [pathPublicId, setPathPublicId] = useState('')
  const [pathLoading, setPathLoading] = useState(false)
  const [pathAssigning, setPathAssigning] = useState(false)
  const [pathRemoving, setPathRemoving] = useState(false)
  const [pathToRemove, setPathToRemove] = useState<StudentAssignedPath>()

  const canAssignEvaluations = permissions.has('STUDENT_UPDATE') && permissions.has('FORM_VIEW')
  const canViewPaths = permissions.has('STUDENT_UPDATE') && permissions.has('PATH_VIEW')
  const canManagePaths = permissions.has('STUDENT_UPDATE') && permissions.has('PATH_MANAGE')

  useEffect(() => {
    if (!canAssignEvaluations || !publicId) return
    let active = true
    setEvaluationLoading(true)
    getAssignableStudentEvaluations(publicId)
      .then((items) => {
        if (!active) return
        setEvaluationOptions(Array.isArray(items) ? items : [])
      })
      .catch((requestError) => {
        if (active) setError(requestError instanceof ApiRequestError ? requestError.message : 'No fue posible consultar las evaluaciones disponibles.')
      })
      .finally(() => { if (active) setEvaluationLoading(false) })
    return () => { active = false }
  }, [canAssignEvaluations, publicId])

  async function loadEvaluationOptions() {
    if (!canAssignEvaluations) return
    setEvaluationLoading(true)
    try {
      const items = await getAssignableStudentEvaluations(publicId)
      setEvaluationOptions(Array.isArray(items) ? items : [])
    } finally {
      setEvaluationLoading(false)
    }
  }

  async function assignEvaluation() {
    if (!evaluationFormPublicId) return
    setEvaluationAssigning(true)
    setError(undefined)
    try {
      const assigned = await assignStudentEvaluation(publicId, evaluationFormPublicId, evaluationDueAt || undefined)
      toast.success('Evaluación asignada', `${assigned.title} ya está disponible en Mi Desarrollo del colaborador.`)
      setEvaluationFormPublicId('')
      setEvaluationDueAt('')
      await loadEvaluationOptions()
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError ? requestError.message : 'No fue posible asignar la evaluación.')
    } finally {
      setEvaluationAssigning(false)
    }
  }

  async function loadPathData() {
    if (!canViewPaths) return
    setPathLoading(true)
    try {
      const requests: [Promise<StudentAssignedPath[]>, Promise<StudentPathOption[]> | null] = [
        getAssignedStudentPaths(publicId),
        canManagePaths ? getAssignableStudentPaths(publicId) : null
      ]
      const assigned = await requests[0]
      const options = requests[1] ? await requests[1] : []
      setAssignedPaths(Array.isArray(assigned) ? assigned : [])
      setPathOptions(Array.isArray(options) ? options : [])
      if (pathPublicId && !options.some((item) => item.publicId === pathPublicId)) setPathPublicId('')
    } finally {
      setPathLoading(false)
    }
  }

  async function assignPath() {
    if (!pathPublicId || !canManagePaths) return
    setPathAssigning(true)
    setError(undefined)
    try {
      const assigned = await assignStudentPath(publicId, pathPublicId)
      toast.success('Path asignado', `${assigned.name} ya está disponible en Mi Desarrollo del colaborador.`)
      setPathPublicId('')
      await loadPathData()
      setHistoryPage(0)
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError ? requestError.message : 'No fue posible asignar el Path.')
    } finally {
      setPathAssigning(false)
    }
  }

  async function confirmRemovePath() {
    if (!pathToRemove || !canManagePaths) return
    setPathRemoving(true)
    setError(undefined)
    try {
      await removeStudentPath(publicId, pathToRemove.assignmentPublicId)
      toast.success('Path retirado', `${pathToRemove.name} dejó de estar disponible. Los intentos, resultados e historial existentes se conservaron.`)
      setPathToRemove(undefined)
      await loadPathData()
      setHistoryPage(0)
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError ? requestError.message : 'No fue posible retirar el Path.')
    } finally {
      setPathRemoving(false)
    }
  }

  async function loadAdministration() {
    const response = await getStudentAdministration(publicId)
    setAdministration(response)
    return response
  }

  useEffect(() => {
    if (!canViewPaths || !publicId) return
    let active = true
    setPathLoading(true)
    const assignedRequest = getAssignedStudentPaths(publicId)
    const optionsRequest = canManagePaths ? getAssignableStudentPaths(publicId) : Promise.resolve<StudentPathOption[]>([])
    Promise.all([assignedRequest, optionsRequest])
      .then(([assigned, options]) => {
        if (!active) return
        setAssignedPaths(Array.isArray(assigned) ? assigned : [])
        setPathOptions(Array.isArray(options) ? options : [])
      })
      .catch((requestError) => {
        if (active) setError(requestError instanceof ApiRequestError ? requestError.message : 'No fue posible consultar los Paths del colaborador.')
      })
      .finally(() => { if (active) setPathLoading(false) })
    return () => { active = false }
  }, [canManagePaths, canViewPaths, publicId])

  useEffect(() => {
    let active = true
    setLoading(true)
    setError(undefined)
    getStudentAdministration(publicId)
      .then((response) => { if (active) setAdministration(response) })
      .catch((requestError) => {
        if (active) setError(requestError instanceof ApiRequestError
          ? requestError.message
          : 'No fue posible consultar la administración del colaborador.')
      })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [publicId])

  useEffect(() => {
    let active = true
    setHistoryLoading(true)
    getStudentAdministrativeHistory(publicId, historyPage, historySize)
      .then((response) => { if (active) setHistory({ ...response, content: response.content ?? [] }) })
      .catch((requestError) => {
        if (active) setError(requestError instanceof ApiRequestError
          ? requestError.message
          : 'No fue posible consultar el historial administrativo.')
      })
      .finally(() => { if (active) setHistoryLoading(false) })
    return () => { active = false }
  }, [historyPage, historySize, publicId])

  async function executeStatusAction() {
    if (!pendingAction || pendingAction === 'RESET_PASSWORD') return
    setBusy(true)
    setError(undefined)
    try {
      if (pendingAction === 'ACTIVATE') {
        await activateStudent(publicId)
        const response = await loadAdministration()
        setPendingAction(undefined)
        setHistoryPage(0)
        toast.success('Estado actualizado', `${response.student.displayName} quedó como ${STATUS_LABELS[response.student.effectiveStatus].toLowerCase()}.`)
      } else {
        await deactivateStudent(publicId)
        setPendingAction(undefined)
        toast.success('El colaborador fue dado de baja correctamente.', 'El registro fue trasladado a Talent Bank y dejó de contabilizarse en los indicadores operativos.')
        navigate('/admin/talent-bank', { replace: true })
      }
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError
        ? requestError.message
        : 'No fue posible actualizar el estado del colaborador.')
    } finally {
      setBusy(false)
    }
  }

  async function executePasswordReset() {
    setBusy(true)
    setError(undefined)
    try {
      const response = await resetStudentPassword(publicId)
      setTemporaryCredentials({ ...response.temporaryCredentials, studentCode: response.student.studentCode })
      setPendingAction(undefined)
      await loadAdministration()
      setHistoryPage(0)
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError
        ? requestError.message
        : 'No fue posible restablecer la contraseña.')
    } finally {
      setBusy(false)
    }
  }


  async function revokeSession(sessionPublicId: string) {
    setBusy(true)
    setError(undefined)
    try {
      await revokeStudentSession(publicId, sessionPublicId)
      await loadAdministration()
      setHistoryPage(0)
      toast.success('Sesión revocada')
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError ? requestError.message : 'No fue posible revocar la sesión.')
    } finally {
      setBusy(false)
    }
  }

  async function revokeAllSessions() {
    setBusy(true)
    setError(undefined)
    try {
      await revokeAllStudentSessions(publicId)
      await loadAdministration()
      setHistoryPage(0)
      toast.success('Sesiones revocadas')
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError ? requestError.message : 'No fue posible revocar las sesiones.')
    } finally {
      setBusy(false)
    }
  }



  if (loading) return <LoadingScreen />
  if (!administration) {
    return <main className="content-page"><BackButton fallback="/admin/collaborators" />
      <div className="error-message" role="alert">{error ?? 'El colaborador no existe.'}</div></main>
  }

  const { student, sessions, seat, activeSessions, lastAdministrativeChange } = administration
  const canActivate = Boolean(student.admissionDate)
    && (student.effectiveStatus === 'INACTIVE' || student.effectiveStatus === 'EXPIRED')
  const canDeactivate = student.effectiveStatus === 'ACTIVE' || student.effectiveStatus === 'EXPIRED'

  return (
    <main className="content-page resource-page student-management-page">
      <BackButton fallback="/admin/collaborators" />
      {error && <div className="error-message" role="alert">{error}</div>}

      <section className="ns-card student-management-summary">
        <div><span>Código a nivel organización</span><strong>{student.studentCode || 'N/A'}</strong></div>
        <div><span>Usuario corporativo</span><strong>{student.corporateUser || 'N/A'}</strong></div>
        <div><span>Correo</span><strong>{student.email}</strong></div>
        <div><span>Organización</span><strong>{student.organization?.name ?? 'Sin organización'}</strong></div>
        <div><span>Fecha de alta</span><strong>{formatDate(student.admissionDate)}</strong></div>
        <div><span>Asiento</span><strong>{seat.assignedSeatPublicId ? seat.status : 'Sin asiento asignado'}</strong></div>
        <div><span>Sesiones activas</span><strong>{activeSessions}</strong></div>
        <div><span>Último cambio administrativo</span><strong>{formatDateTime(lastAdministrativeChange?.occurredAt)}</strong></div>
      </section>

      <section className="ns-card student-administration-section">
        <div className="ns-card-heading"><div><span className="ns-step">1</span><h2>Estado y acceso</h2></div></div>
        <p className="muted">La Fecha de alta se modifica únicamente desde Editar colaborador. Sin este dato, el colaborador permanece inactivo y no puede reactivarse.</p>
        <div className="student-management-actions">
          {canActivate && permissions.has('STUDENT_STATUS_CHANGE') && (
            <button className="primary-button" type="button" disabled={busy} onClick={() => setPendingAction('ACTIVATE')}>Activar</button>
          )}
          {canDeactivate && permissions.has('STUDENT_STATUS_CHANGE') && (
            <button className="secondary-button" type="button" disabled={busy} onClick={() => setPendingAction('DEACTIVATE')}>Dar de baja</button>
          )}
        </div>
      </section>

      {permissions.has('STUDENT_PASSWORD_RESET') && (
        <section className="ns-card student-administration-section">
          <div className="ns-card-heading"><div><span className="ns-step">2</span><h2>Contraseña y credenciales</h2></div></div>
          <p className="muted">Se generará una contraseña temporal segura, se revocarán las sesiones y el colaborador deberá cambiarla en el siguiente inicio.</p>
          <button className="secondary-button" type="button" disabled={busy} onClick={() => setPendingAction('RESET_PASSWORD')}>Restablecer contraseña</button>
        </section>
      )}

      {permissions.has('STUDENT_SESSION_MANAGE') && (
        <section className="ns-card student-administration-section">
          <div className="ns-card-heading">
            <div><span className="ns-step">3</span><h2>Sesiones</h2></div>
            {activeSessions > 0 && <button className="secondary-button" type="button" disabled={busy} onClick={() => void revokeAllSessions()}>Revocar todas</button>}
          </div>
          {sessions.length === 0 ? <p className="muted">No hay sesiones registradas.</p> : (
            <div className="student-session-list">
              {sessions.map((session) => (
                <article key={session.publicId}>
                  <div>
                    <strong>{sessionLabel(session)}</strong>
                    <small>{formatDateTime(session.createdAt)} · {session.ipAddress ?? 'IP no disponible'}</small>
                    {session.revocationReason && <small>Motivo: {revocationReasonLabel(session.revocationReason)}</small>}
                  </div>
                  {session.status === 'ACTIVE' && (
                    <button className="secondary-button" type="button" disabled={busy}
                      onClick={() => void revokeSession(session.publicId)}>Revocar</button>
                  )}
                </article>
              ))}
            </div>
          )}
        </section>
      )}

      {canViewPaths && (
        <section className="ns-card student-administration-section student-path-assignment-admin">
          <div className="ns-card-heading"><div><span className="ns-step">4</span><h2>Paths asignados</h2></div></div>
          <p className="muted">Cada Path habilita sus Colecciones y Formularios sin duplicarlos. Retirar un Path conserva intentos, resultados e historial existentes.</p>
          {pathLoading ? <p className="muted">Consultando Paths…</p> : assignedPaths.length === 0 ? <div className="path-assignment-empty"><strong>Sin Paths asignados</strong><span>Este colaborador todavía no tiene una ruta de desarrollo asignada.</span></div> : <div className="path-assignment-list">{assignedPaths.map((path) => <article key={path.assignmentPublicId}><div><strong>{path.name}</strong><small>{path.collectionCount} {path.collectionCount === 1 ? 'Colección' : 'Colecciones'} · {path.formCount} {path.formCount === 1 ? 'Formulario' : 'Formularios'} · Asignado {formatDateTime(path.assignedAt)}</small>{path.description && <span>{path.description}</span>}</div>{canManagePaths && <button className="secondary-button" type="button" disabled={pathRemoving || pathAssigning} onClick={() => setPathToRemove(path)}>Retirar</button>}</article>)}</div>}
          {canManagePaths && <div className="path-assignment-form"><label className="form-field"><span>Asignar Path</span><select value={pathPublicId} disabled={pathLoading || pathAssigning || pathRemoving} onChange={(event) => setPathPublicId(event.target.value)}><option value="">{pathLoading ? 'Cargando Paths…' : 'Seleccionar Path'}</option>{pathOptions.map((item) => <option key={item.publicId} value={item.publicId}>{item.name} · {item.collectionCount} {item.collectionCount === 1 ? 'Colección' : 'Colecciones'} · {item.organizationName}</option>)}</select>{!pathLoading && pathOptions.length === 0 && <small>No hay Paths activos adicionales disponibles dentro del alcance del colaborador.</small>}</label><button className="primary-button" type="button" disabled={!pathPublicId || pathLoading || pathAssigning || pathRemoving} onClick={() => void assignPath()}>{pathAssigning ? 'Asignando…' : 'Asignar Path'}</button></div>}
        </section>
      )}

      {canAssignEvaluations && (
        <section className="ns-card student-administration-section">
          <div className="ns-card-heading"><div><span className="ns-step">{canViewPaths ? 5 : 4}</span><h2>Evaluaciones de Mi Desarrollo</h2></div></div>
          <p className="muted">Asigna un formulario de evaluación activo al colaborador. La práctica de preparación permanece separada y no consume intentos oficiales.</p>
          <div className="foundation-form-grid">
            <label className="form-field ns-field-span-6"><span>Evaluación</span>
              <select value={evaluationFormPublicId} disabled={evaluationLoading || evaluationAssigning} onChange={(event) => setEvaluationFormPublicId(event.target.value)}>
                <option value="">{evaluationLoading ? 'Cargando evaluaciones…' : 'Seleccionar evaluación'}</option>
                {evaluationOptions.map((item) => <option key={item.publicId} value={item.publicId}>{item.title}</option>)}
              </select>
              {!evaluationLoading && evaluationOptions.length === 0 && <small>No hay evaluaciones activas disponibles para asignar dentro del alcance del colaborador.</small>}
            </label>
            <label className="form-field ns-field-span-6"><span>Fecha límite <small>(opcional)</small></span>
              <input type="datetime-local" value={evaluationDueAt} disabled={evaluationAssigning} onChange={(event) => setEvaluationDueAt(event.target.value)} />
            </label>
          </div>
          <div className="student-management-actions"><button className="primary-button" type="button" disabled={!evaluationFormPublicId || evaluationLoading || evaluationAssigning} onClick={() => void assignEvaluation()}>{evaluationAssigning ? 'Asignando…' : 'Asignar evaluación'}</button></div>
        </section>
      )}

      <section className="ns-card student-administration-section">
        <div className="ns-card-heading"><div><span className="ns-step">{4 + (canViewPaths ? 1 : 0) + (canAssignEvaluations ? 1 : 0)}</span><h2>Historial administrativo</h2></div></div>
        <div className="ns-data-table-wrap">
          <table className="ns-data-table">
            <thead><tr><th>Evento</th><th>Descripción</th><th>Fecha</th></tr></thead>
            <tbody>
              {historyLoading && <tr><td colSpan={3} className="ns-table-empty">Cargando historial…</td></tr>}
              {!historyLoading && history.content.length === 0 && <tr><td colSpan={3} className="ns-table-empty">Todavía no existen cambios administrativos registrados.</td></tr>}
              {!historyLoading && history.content.map((item) => (
                <tr key={item.publicId}><td><strong>{item.eventType}</strong></td><td>{item.description}</td><td>{formatDateTime(item.occurredAt)}</td></tr>
              ))}
            </tbody>
          </table>
        </div>
        <TablePagination compact currentPage={history.page} pageSize={history.size}
          totalElements={history.totalElements} totalPages={history.totalPages} isLoading={historyLoading}
          onPageChange={setHistoryPage}
          onPageSizeChange={(nextSize) => { setHistorySize(nextSize); setHistoryPage(0) }} />
      </section>

      <ConfirmDialog open={pendingAction === 'ACTIVATE'} title="Activar colaborador"
        description="Se validarán el estado de la organización y la disponibilidad operativa. Las sesiones anteriores no se restaurarán."
        confirmLabel="Activar" busy={busy} onCancel={() => setPendingAction(undefined)}
        onConfirm={() => void executeStatusAction()} />

      <ConfirmDialog open={pendingAction === 'DEACTIVATE'} title="Dar de baja"
        description="El colaborador será retirado del módulo Colaboradores y trasladado a Talent Bank dentro de la misma organización. Conservará su información, certificaciones, historial y CV; perderá el acceso, sus sesiones serán revocadas y dejará de contabilizarse en los indicadores operativos."
        confirmLabel="Dar de baja" busy={busy} onCancel={() => setPendingAction(undefined)}
        onConfirm={() => void executeStatusAction()} />

      <ConfirmDialog open={pendingAction === 'RESET_PASSWORD'} title="Restablecer contraseña"
        description="Se generará una contraseña temporal segura. Se mostrará una sola vez y todas las sesiones activas serán revocadas."
        confirmLabel="Generar contraseña" busy={busy} onCancel={() => setPendingAction(undefined)}
        onConfirm={() => void executePasswordReset()} />

      <ConfirmDialog open={Boolean(pathToRemove)} title="Retirar Path"
        description={pathToRemove ? `${pathToRemove.name} dejará de estar disponible para el colaborador. Los intentos, resultados e historial existentes se conservarán.` : ''}
        confirmLabel="Retirar Path" busy={pathRemoving} onCancel={() => setPathToRemove(undefined)}
        onConfirm={() => void confirmRemovePath()} />

      {temporaryCredentials && (
        <StudentTemporaryCredentialsDialog title="Contraseña temporal generada"
          credentials={temporaryCredentials}
          onClose={() => setTemporaryCredentials(undefined)} />
      )}
    </main>
  )
}
