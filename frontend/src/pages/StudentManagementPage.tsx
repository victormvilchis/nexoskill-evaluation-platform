import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useAuth } from '../features/authentication/context/AuthContext'
import {
  activateStudent,
  deactivateStudent,
  getStudent,
  getStudentSessions,
  permanentlyDeleteStudent,
  renewStudent,
  revokeAllStudentSessions,
  revokeStudentSession
} from '../features/students/api/studentApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { BackButton } from '../shared/components/BackButton'
import { ConfirmDialog } from '../shared/components/ConfirmDialog'
import { LoadingScreen } from '../shared/components/LoadingScreen'
import { useToast } from '../shared/components/ToastProvider'
import type { StudentDetail, StudentSession } from '../shared/types/students'

type PendingAction = 'ACTIVATE' | 'DEACTIVATE'

const statusLabels = {
  ACTIVE: 'Activo',
  INACTIVE: 'Desactivado',
  EXPIRED: 'Vencido',
  DELETED: 'Eliminado'
} as const

function toLocalInput(value?: string | null) {
  if (!value) return ''
  const date = new Date(value)
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16)
}

function formatDateTime(value?: string | null) {
  if (!value) return 'Sin registro'
  return new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

export function StudentManagementPage() {
  const { publicId = '' } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const { user } = useAuth()
  const permissions = useMemo(() => new Set(user?.permissions ?? []), [user])
  const [student, setStudent] = useState<StudentDetail>()
  const [sessions, setSessions] = useState<StudentSession[]>([])
  const [loading, setLoading] = useState(true)
  const [sessionsLoading, setSessionsLoading] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()
  const [pendingAction, setPendingAction] = useState<PendingAction>()
  const [renewOpen, setRenewOpen] = useState(false)
  const [renewalDate, setRenewalDate] = useState('')
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [deleteConfirmed, setDeleteConfirmed] = useState(false)

  async function loadStudent() {
    const detail = await getStudent(publicId)
    setStudent(detail)
    return detail
  }

  async function loadSessions() {
    if (!permissions.has('STUDENT_SESSION_MANAGE')) return
    setSessionsLoading(true)
    try {
      setSessions(await getStudentSessions(publicId))
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError
        ? requestError.message
        : 'No fue posible consultar las sesiones del estudiante.')
    } finally {
      setSessionsLoading(false)
    }
  }

  useEffect(() => {
    let active = true
    setLoading(true)
    getStudent(publicId)
      .then((detail) => { if (active) setStudent(detail) })
      .catch((requestError) => {
        if (active) setError(requestError instanceof ApiRequestError
          ? requestError.message
          : 'No fue posible consultar al estudiante.')
      })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [publicId])

  useEffect(() => {
    if (!student || !permissions.has('STUDENT_SESSION_MANAGE')) return
    void loadSessions()
  }, [student?.publicId, permissions])

  async function executeStatusAction() {
    if (!pendingAction) return
    setBusy(true)
    setError(undefined)
    try {
      await (pendingAction === 'ACTIVATE' ? activateStudent(publicId) : deactivateStudent(publicId))
      const updated = await loadStudent()
      setPendingAction(undefined)
      if (pendingAction === 'DEACTIVATE') await loadSessions()
      toast.success('Estado actualizado', `${updated.displayName} quedó como ${statusLabels[updated.effectiveStatus].toLowerCase()}.`)
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError
        ? requestError.message
        : 'No fue posible actualizar el estado del estudiante.')
    } finally {
      setBusy(false)
    }
  }

  async function executeRenewal() {
    if (!student || !renewalDate) return
    setBusy(true)
    setError(undefined)
    try {
      await renewStudent(publicId, new Date(renewalDate).toISOString(), student.version)
      const updated = await loadStudent()
      setRenewOpen(false)
      setRenewalDate('')
      toast.success('Vigencia renovada', `${updated.displayName} volvió a estado activo.`)
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError
        ? requestError.message
        : 'No fue posible renovar la vigencia del estudiante.')
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
      toast.success('Estudiante eliminado permanentemente',
        'La cuenta y su información exclusiva fueron eliminadas. Esta operación no puede deshacerse.')
      navigate('/admin/students', { replace: true })
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError
        ? requestError.message
        : 'No fue posible eliminar permanentemente al estudiante.')
    } finally {
      setBusy(false)
    }
  }

  async function revokeSession(sessionPublicId: string) {
    setBusy(true)
    setError(undefined)
    try {
      await revokeStudentSession(publicId, sessionPublicId)
      await loadSessions()
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
      await loadSessions()
      toast.success('Sesiones revocadas')
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError ? requestError.message : 'No fue posible revocar las sesiones.')
    } finally {
      setBusy(false)
    }
  }

  if (loading) return <LoadingScreen />
  if (!student) {
    return <main className="content-page"><BackButton fallback="/admin/students" />
      <div className="error-message" role="alert">{error ?? 'El estudiante no existe.'}</div></main>
  }

  const activeSessions = sessions.filter((session) => session.status === 'ACTIVE')

  return (
    <main className="content-page narrow-content resource-page student-management-page">
      <BackButton fallback="/admin/students" />
      <header className="ns-page-header">
        <div><p className="eyebrow">Administración · Estudiante</p><h1>Administrar {student.displayName}</h1>
          <p className="muted">Los cambios de estado, sesiones y eliminación permanente se ejecutan desde esta vista.</p></div>
        <span className={`status-badge status-${student.effectiveStatus.toLowerCase()}`}>{statusLabels[student.effectiveStatus]}</span>
      </header>

      {error && <div className="error-message" role="alert">{error}</div>}

      <section className="ns-card student-management-summary">
        <div><span>Correo</span><strong>{student.email}</strong></div>
        <div><span>Código</span><strong>{student.studentCode}</strong></div>
        <div><span>Organización</span><strong>{student.organization?.name ?? 'Sin organización'}</strong></div>
        <div><span>Vencimiento</span><strong>{formatDateTime(student.expiresAt)}</strong></div>
      </section>

      <section className="ns-card">
        <div className="ns-card-heading"><div><span className="ns-step">1</span><h2>Ciclo de vida</h2></div></div>
        <div className="student-management-actions">
          {student.effectiveStatus === 'ACTIVE' && permissions.has('STUDENT_STATUS_CHANGE') &&
            <button className="danger-button" type="button" onClick={() => setPendingAction('DEACTIVATE')}>Desactivar</button>}
          {student.effectiveStatus === 'INACTIVE' && permissions.has('STUDENT_STATUS_CHANGE') &&
            <button className="primary-button" type="button" onClick={() => setPendingAction('ACTIVATE')}>Activar</button>}
          {student.effectiveStatus === 'EXPIRED' && permissions.has('STUDENT_STATUS_CHANGE') && <>
            <button className="primary-button" type="button" onClick={() => { setRenewalDate(toLocalInput(new Date(Date.now() + 86_400_000).toISOString())); setRenewOpen(true) }}>Renovar vigencia</button>
            <button className="secondary-button" type="button" onClick={() => setPendingAction('DEACTIVATE')}>Desactivar</button>
          </>}
          {permissions.has('STUDENT_DELETE') &&
            <button className="danger-button" type="button" onClick={() => { setDeleteConfirmed(false); setDeleteOpen(true) }}>Eliminar permanentemente</button>}
        </div>
      </section>

      {permissions.has('STUDENT_SESSION_MANAGE') && <section className="ns-card">
        <div className="ns-card-heading"><div><span className="ns-step">2</span><h2>Sesiones</h2></div>
          {activeSessions.length > 0 && <button className="secondary-button" type="button" disabled={busy} onClick={() => void revokeAllSessions()}>Revocar todas</button>}</div>
        {sessionsLoading ? <p className="muted">Consultando sesiones…</p> : sessions.length === 0 ?
          <p className="muted">No hay sesiones registradas.</p> : <div className="student-session-list">{sessions.map((session) =>
            <article key={session.publicId}><div><strong>{session.status === 'ACTIVE' ? 'Activa' : session.status === 'REVOKED' ? 'Revocada' : 'Vencida'}</strong>
              <small>{formatDateTime(session.createdAt)} · {session.ipAddress ?? 'IP no disponible'}</small></div>
              {session.status === 'ACTIVE' && <button className="secondary-button" type="button" disabled={busy} onClick={() => void revokeSession(session.publicId)}>Revocar</button>}</article>)}</div>}
      </section>}

      <ConfirmDialog open={Boolean(pendingAction)}
        title={pendingAction === 'ACTIVATE' ? 'Activar estudiante' : 'Desactivar estudiante'}
        description={pendingAction === 'ACTIVATE'
          ? 'El estudiante recuperará el acceso si su vigencia es válida.'
          : 'El estudiante perderá el acceso y todas sus sesiones activas serán revocadas.'}
        confirmLabel={pendingAction === 'ACTIVATE' ? 'Activar' : 'Desactivar'}
        busy={busy} onCancel={() => setPendingAction(undefined)} onConfirm={() => void executeStatusAction()} />

      <ConfirmDialog open={renewOpen} title="Renovar vigencia"
        description="Captura una nueva fecha de vencimiento válida. El estudiante volverá a estado Activo."
        confirmLabel="Renovar y activar" busy={busy} confirmDisabled={!renewalDate}
        onCancel={() => { setRenewOpen(false); setRenewalDate('') }} onConfirm={() => void executeRenewal()}>
        <label className="form-field"><span>Nueva fecha de vencimiento</span>
          <input type="datetime-local" value={renewalDate} min={toLocalInput(new Date().toISOString())}
            onChange={(event) => setRenewalDate(event.target.value)} /></label>
      </ConfirmDialog>

      <ConfirmDialog open={deleteOpen} title="Eliminar permanentemente al estudiante"
        description="Esta acción eliminará permanentemente al estudiante y toda su información asociada, incluyendo avances, intentos, resultados, asignaciones y certificaciones. Esta operación no se puede deshacer."
        confirmLabel="Eliminar permanentemente" tone="danger" busy={busy} confirmDisabled={!deleteConfirmed}
        onCancel={() => { setDeleteOpen(false); setDeleteConfirmed(false) }} onConfirm={() => void executePermanentDelete()}>
        <label className="student-delete-confirmation"><input type="checkbox" checked={deleteConfirmed}
          onChange={(event) => setDeleteConfirmed(event.target.checked)} />
          <span>Entiendo que esta acción eliminará permanentemente toda la información del estudiante.</span></label>
      </ConfirmDialog>
    </main>
  )
}
