import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useAuth } from '../features/authentication/context/AuthContext'
import {
  activateStudent,
  deactivateStudent,
  getStudentAdministration,
  getStudentAdministrativeHistory,
  permanentlyDeleteStudent,
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
  StudentTemporaryCredentials
} from '../shared/types/students'

type PendingAction = 'ACTIVATE' | 'DEACTIVATE' | 'RESET_PASSWORD'

const STATUS_LABELS = {
  ACTIVE: 'Activo',
  INACTIVE: 'Desactivado',
  EXPIRED: 'Vencido',
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
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [deleteConfirmed, setDeleteConfirmed] = useState(false)
  const [temporaryCredentials, setTemporaryCredentials] = useState<StudentTemporaryCredentials>()

  async function loadAdministration() {
    const response = await getStudentAdministration(publicId)
    setAdministration(response)
    return response
  }

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
      if (pendingAction === 'ACTIVATE') await activateStudent(publicId)
      else await deactivateStudent(publicId)
      const response = await loadAdministration()
      setPendingAction(undefined)
      setHistoryPage(0)
      toast.success('Estado actualizado', `${response.student.displayName} quedó como ${STATUS_LABELS[response.student.effectiveStatus].toLowerCase()}.`)
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

  async function executePermanentDelete() {
    if (!deleteConfirmed) return
    setBusy(true)
    setError(undefined)
    try {
      await permanentlyDeleteStudent(publicId)
      toast.success('Colaborador eliminado permanentemente',
        'La cuenta y la información exclusiva del colaborador fueron eliminadas. La operación no puede deshacerse.')
      navigate('/admin/students', { replace: true })
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError
        ? requestError.message
        : 'No fue posible eliminar permanentemente al colaborador.')
    } finally {
      setBusy(false)
    }
  }

  if (loading) return <LoadingScreen />
  if (!administration) {
    return <main className="content-page"><BackButton fallback="/admin/students" />
      <div className="error-message" role="alert">{error ?? 'El colaborador no existe.'}</div></main>
  }

  const { student, sessions, seat, activeSessions, lastAdministrativeChange } = administration
  const canActivate = Boolean(student.admissionDate)
    && (student.effectiveStatus === 'INACTIVE' || student.effectiveStatus === 'EXPIRED')
  const canDeactivate = student.effectiveStatus === 'ACTIVE' || student.effectiveStatus === 'EXPIRED'

  return (
    <main className="content-page resource-page student-management-page">
      <BackButton fallback="/admin/students" />
      <header className="ns-page-header">
        <div>
          <p className="eyebrow">Administración · Colaborador</p>
          <h1>Administrar {student.displayName}</h1>
          <p className="muted">Las acciones sensibles de cuenta, acceso, contraseña, sesiones y eliminación se concentran en esta vista.</p>
        </div>
        <span className={`status-badge status-${student.effectiveStatus.toLowerCase()}`}>{STATUS_LABELS[student.effectiveStatus]}</span>
      </header>

      {error && <div className="error-message" role="alert">{error}</div>}

      <section className="ns-card student-management-summary">
        <div><span>Código a nivel organización</span><strong>{student.studentCode || 'N/A'}</strong></div>
        <div><span>Usuario corporativo</span><strong>{student.corporateUser || 'N/A'}</strong></div>
        <div><span>Correo</span><strong>{student.email}</strong></div>
        <div><span>Organización</span><strong>{student.organization?.name ?? 'Sin organización'}</strong></div>
        <div><span>Inicio de vigencia</span><strong>{formatDate(student.validFrom)}</strong></div>
        <div><span>Vencimiento</span><strong>{formatDate(student.expiresAt)}</strong></div>
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
            <button className="secondary-button" type="button" disabled={busy} onClick={() => setPendingAction('DEACTIVATE')}>Desactivar</button>
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
                    {session.revocationReason && <small>Motivo: {session.revocationReason}</small>}
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

      <section className="ns-card student-administration-section">
        <div className="ns-card-heading"><div><span className="ns-step">4</span><h2>Historial administrativo</h2></div></div>
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

      {permissions.has('STUDENT_DELETE') && (
        <section className="ns-card student-administration-section student-permanent-delete-zone">
          <div className="ns-card-heading"><div><span className="ns-step">5</span><h2>Zona de eliminación permanente</h2></div></div>
          <p>La eliminación borra la cuenta, credenciales, sesiones, asignaciones, avances, intentos, resultados, certificaciones y datos personales exclusivos del colaborador. No elimina contenido maestro ni información de otras personas.</p>
          <button className="danger-button" type="button" disabled={busy}
            onClick={() => { setDeleteConfirmed(false); setDeleteOpen(true) }}>Eliminar permanentemente</button>
        </section>
      )}

      <ConfirmDialog open={pendingAction === 'ACTIVATE'} title="Activar colaborador"
        description="Se validarán la organización, el inicio de vigencia, el vencimiento y la disponibilidad de asiento. Las sesiones anteriores no se restaurarán."
        confirmLabel="Activar" busy={busy} onCancel={() => setPendingAction(undefined)}
        onConfirm={() => void executeStatusAction()} />

      <ConfirmDialog open={pendingAction === 'DEACTIVATE'} title="Desactivar colaborador"
        description="Se eliminará la Fecha de alta. El colaborador perderá el acceso inmediatamente, sus sesiones serán revocadas y ya no podrá gestionar certificaciones; sus identificadores, avances, resultados y certificaciones existentes se conservarán."
        confirmLabel="Desactivar" busy={busy} onCancel={() => setPendingAction(undefined)}
        onConfirm={() => void executeStatusAction()} />

      <ConfirmDialog open={pendingAction === 'RESET_PASSWORD'} title="Restablecer contraseña"
        description="Se generará una contraseña temporal segura. Se mostrará una sola vez y todas las sesiones activas serán revocadas."
        confirmLabel="Generar contraseña" busy={busy} onCancel={() => setPendingAction(undefined)}
        onConfirm={() => void executePasswordReset()} />

      <ConfirmDialog open={deleteOpen} title="Eliminar permanentemente al colaborador"
        description="Esta acción eliminará permanentemente al colaborador y toda su información asociada. Esta operación no se puede deshacer."
        confirmLabel="Eliminar permanentemente" tone="danger" busy={busy} confirmDisabled={!deleteConfirmed}
        onCancel={() => { setDeleteOpen(false); setDeleteConfirmed(false) }}
        onConfirm={() => void executePermanentDelete()}>
        <label className="student-delete-confirmation">
          <input type="checkbox" checked={deleteConfirmed} onChange={(event) => setDeleteConfirmed(event.target.checked)} />
          <span>Entiendo que esta acción eliminará permanentemente al colaborador y toda su información.</span>
        </label>
      </ConfirmDialog>

      {temporaryCredentials && (
        <StudentTemporaryCredentialsDialog title="Contraseña temporal generada"
          credentials={temporaryCredentials}
          onClose={() => setTemporaryCredentials(undefined)} />
      )}
    </main>
  )
}
