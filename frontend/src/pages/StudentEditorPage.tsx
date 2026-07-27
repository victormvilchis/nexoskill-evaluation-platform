import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useAuth } from '../features/authentication/context/AuthContext'
import {
  activateStudent,
  archiveStudent,
  createStudent,
  deactivateStudent,
  deleteStudent,
  getStudent,
  getStudentSessions,
  resetStudentPassword,
  restoreStudent,
  revokeAllStudentSessions,
  revokeStudentSession,
  suspendStudent,
  updateStudent
} from '../features/students/api/studentApi'
import { ApiRequestError, ORGANIZATION_CONTEXT_KEY } from '../shared/api/apiClient'
import { BackButton } from '../shared/components/BackButton'
import { ConfirmDialog } from '../shared/components/ConfirmDialog'
import { Icon } from '../shared/components/Icon'
import { TablePagination } from '../shared/components/TablePagination'
import { useClientPagination } from '../shared/hooks/useClientPagination'
import type { PageSize } from '../shared/types/pagination'
import { useToast } from '../shared/components/ToastProvider'
import { useSaveNavigation } from '../shared/hooks/useSaveNavigation'
import type { StudentDetail, StudentSession, StudentStatus } from '../shared/types/students'

interface Props { mode: 'create' | 'edit' | 'view' }
type LifecycleAction = 'ACTIVATE' | 'DEACTIVATE' | 'SUSPEND' | 'ARCHIVE' | 'DELETE' | 'RESTORE' | 'REVOKE_ALL'

function toLocalInput(value: string | null | undefined) {
  if (!value) return ''
  const date = new Date(value)
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 16)
}
function toIso(value: string) { return value ? new Date(value).toISOString() : null }
function formatDate(value: string | null) {
  if (!value) return 'Sin registro'
  return new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

export function StudentEditorPage({ mode }: Props) {
  const { publicId } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const completeSave = useSaveNavigation('/admin/students')
  const { user } = useAuth()
  const permissions = useMemo(() => new Set(user?.permissions ?? []), [user])
  const administrator = Boolean(user?.roles.includes('ADMINISTRATOR'))
  const hasContext = !administrator || Boolean(window.localStorage.getItem(ORGANIZATION_CONTEXT_KEY))
  const [student, setStudent] = useState<StudentDetail | null>(null)
  const [sessions, setSessions] = useState<StudentSession[]>([])
  const [loading, setLoading] = useState(mode !== 'create')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [pendingAction, setPendingAction] = useState<LifecycleAction | null>(null)
  const [busySession, setBusySession] = useState<string | null>(null)

  const [studentCode, setStudentCode] = useState('')
  const [email, setEmail] = useState('')
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [temporaryPassword, setTemporaryPassword] = useState('')
  const [status, setStatus] = useState<'ACTIVE' | 'INACTIVE'>('INACTIVE')
  const [validFrom, setValidFrom] = useState(toLocalInput(new Date().toISOString()))
  const [expiresAt, setExpiresAt] = useState('')
  const [sessionsPage, setSessionsPage] = useState(0)
  const [sessionsSize, setSessionsSize] = useState<PageSize>(10)
  const sessionsData = useClientPagination(sessions, sessionsPage, sessionsSize)
  const readOnly = mode === 'view'

  useEffect(() => {
    if (mode === 'create' || !publicId || !hasContext) return
    let active = true
    setLoading(true)
    Promise.all([
      getStudent(publicId),
      permissions.has('STUDENT_SESSION_MANAGE') ? getStudentSessions(publicId) : Promise.resolve([])
    ]).then(([detail, sessionData]) => {
      if (!active) return
      setStudent(detail)
      setSessions(sessionData)
      setStudentCode(detail.studentCode)
      setEmail(detail.email)
      setFirstName(detail.firstName)
      setLastName(detail.lastName)
      setDisplayName(detail.displayName)
      setValidFrom(toLocalInput(detail.validFrom))
      setExpiresAt(toLocalInput(detail.expiresAt))
    }).catch((requestError) => {
      if (active) setError(requestError instanceof ApiRequestError ? requestError.message : 'No fue posible cargar al estudiante.')
    }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [hasContext, mode, permissions, publicId])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (readOnly || !hasContext || saving) return
    setSaving(true)
    setError(null)
    setFieldErrors({})
    try {
      if (mode === 'create') {
        await createStudent({
          studentCode: studentCode.trim(), email: email.trim(), firstName: firstName.trim(), lastName: lastName.trim(),
          displayName: displayName.trim() || undefined, temporaryPassword, status,
          validFrom: toIso(validFrom)!, expiresAt: toIso(expiresAt)
        })
        completeSave({
          title: 'Estudiante creado correctamente.',
          message: 'La cuenta quedó registrada en la organización seleccionada.'
        })
      } else if (student && publicId) {
        await updateStudent(publicId, {
          email: email.trim(), firstName: firstName.trim(), lastName: lastName.trim(),
          displayName: displayName.trim() || undefined, validFrom: toIso(validFrom)!, expiresAt: toIso(expiresAt),
          version: student.version
        })
        completeSave({ title: 'Estudiante actualizado correctamente.' })
      }
    } catch (requestError) {
      if (requestError instanceof ApiRequestError) {
        setError(requestError.message)
        setFieldErrors(requestError.fieldErrors ?? {})
      } else setError('No fue posible guardar al estudiante.')
    } finally { setSaving(false) }
  }

  async function executeLifecycle() {
    if (!pendingAction || !publicId || saving) return
    setSaving(true)
    try {
      if (pendingAction === 'REVOKE_ALL') {
        await revokeAllStudentSessions(publicId)
        setSessions(await getStudentSessions(publicId))
        toast.success('Sesiones revocadas')
      } else {
        let updated: StudentDetail
        if (pendingAction === 'ACTIVATE') updated = await activateStudent(publicId)
        else if (pendingAction === 'DEACTIVATE') updated = await deactivateStudent(publicId)
        else if (pendingAction === 'SUSPEND') updated = await suspendStudent(publicId)
        else if (pendingAction === 'ARCHIVE') updated = await archiveStudent(publicId)
        else if (pendingAction === 'RESTORE') updated = await restoreStudent(publicId)
        else {
          const reason = window.prompt('Motivo obligatorio de eliminación lógica:')?.trim()
          if (!reason) { setPendingAction(null); return }
          updated = await deleteStudent(publicId, reason)
        }
        setStudent(updated)
        setSessions(await getStudentSessions(publicId).catch(() => []))
        toast.success('Estado actualizado')
      }
      setPendingAction(null)
    } catch (requestError) {
      toast.error('No fue posible completar la operación', requestError instanceof ApiRequestError ? requestError.message : undefined)
    } finally { setSaving(false) }
  }

  async function handleResetPassword() {
    if (!publicId || saving) return
    const password = window.prompt('Captura la nueva contraseña temporal:')
    if (!password) return
    setSaving(true)
    try {
      const updated = await resetStudentPassword(publicId, password)
      setStudent(updated)
      setSessions(await getStudentSessions(publicId).catch(() => []))
      toast.success('Contraseña restablecida', 'Las sesiones anteriores fueron revocadas.')
    } catch (requestError) {
      toast.error('No fue posible restablecer la contraseña', requestError instanceof ApiRequestError ? requestError.message : undefined)
    } finally {
      setSaving(false)
    }
  }

  async function handleRevokeSession(sessionPublicId: string) {
    if (!publicId) return
    setBusySession(sessionPublicId)
    try {
      await revokeStudentSession(publicId, sessionPublicId)
      setSessions(await getStudentSessions(publicId))
      toast.success('Sesión revocada')
    } catch (requestError) {
      toast.error('No fue posible revocar la sesión', requestError instanceof ApiRequestError ? requestError.message : undefined)
    } finally { setBusySession(null) }
  }

  if (!hasContext) {
    return <main className="content-page"><BackButton fallback="/admin/students" /><div className="error-message">Selecciona una organización antes de administrar estudiantes.</div></main>
  }
  if (loading) return <main className="content-page"><p>Cargando estudiante…</p></main>

  return (
    <main className="content-page resource-page student-editor-page">
      <BackButton fallback="/admin/students" />
      <header className="ns-page-header student-editor-header">
        <div>
          <p className="eyebrow">Administración · Estudiantes</p>
          <h1>{mode === 'create' ? 'Nuevo estudiante' : student?.displayName ?? 'Estudiante'}</h1>
          <p className="muted">Cuenta independiente, vigencia y control de sesiones.</p>
        </div>
        {student && <span className={`status-badge status-${student.effectiveStatus.toLowerCase()}`}>{student.effectiveStatus}</span>}
      </header>

      {error && <div className="error-message">{error}</div>}

      <form className="student-editor-grid" onSubmit={handleSubmit}>
        <section className="form-card student-form-card">
          <div className="section-heading"><div><h2>Identidad</h2><p>Datos propios del estudiante; no crea un usuario interno.</p></div></div>
          <div className="form-grid">
            <div className="form-field"><label htmlFor="studentCode">Código</label><input id="studentCode" value={studentCode} disabled={readOnly || mode !== 'create'} maxLength={80} required onChange={(e) => setStudentCode(e.target.value)} />{fieldErrors.studentCode && <small className="field-error">{fieldErrors.studentCode}</small>}</div>
            <div className="form-field"><label htmlFor="email">Correo</label><input id="email" type="email" value={email} disabled={readOnly} maxLength={254} required onChange={(e) => setEmail(e.target.value)} />{fieldErrors.email && <small className="field-error">{fieldErrors.email}</small>}</div>
            <div className="form-field"><label htmlFor="firstName">Nombre</label><input id="firstName" value={firstName} disabled={readOnly} maxLength={100} required onChange={(e) => setFirstName(e.target.value)} /></div>
            <div className="form-field"><label htmlFor="lastName">Apellidos</label><input id="lastName" value={lastName} disabled={readOnly} maxLength={150} required onChange={(e) => setLastName(e.target.value)} /></div>
            <div className="form-field form-wide"><label htmlFor="displayName">Nombre visible</label><input id="displayName" value={displayName} disabled={readOnly} maxLength={250} onChange={(e) => setDisplayName(e.target.value)} /></div>
          </div>
        </section>

        <section className="form-card student-form-card">
          <div className="section-heading"><div><h2>Acceso y vigencia</h2><p>El vencimiento, suspensión o baja revocan la sesión inmediatamente.</p></div></div>
          <div className="form-grid">
            {mode === 'create' && <>
              <div className="form-field"><label htmlFor="status">Estado inicial</label><select id="status" value={status} onChange={(e) => setStatus(e.target.value as 'ACTIVE' | 'INACTIVE')}><option value="INACTIVE">Inactivo</option><option value="ACTIVE">Activo</option></select></div>
              <div className="form-field"><label htmlFor="temporaryPassword">Contraseña temporal</label><input id="temporaryPassword" type="password" value={temporaryPassword} minLength={10} maxLength={128} required onChange={(e) => setTemporaryPassword(e.target.value)} /></div>
            </>}
            <div className="form-field"><label htmlFor="validFrom">Inicio</label><input id="validFrom" type="datetime-local" value={validFrom} disabled={readOnly} required onChange={(e) => setValidFrom(e.target.value)} /></div>
            <div className="form-field"><label htmlFor="expiresAt">Vencimiento</label><input id="expiresAt" type="datetime-local" value={expiresAt} disabled={readOnly} onChange={(e) => setExpiresAt(e.target.value)} /></div>
          </div>
          {!readOnly && <div className="form-actions"><Link className="secondary-button button-link" to="/admin/students">Cancelar</Link><button className="primary-button" type="submit" disabled={saving}>{saving ? 'Guardando…' : mode === 'create' ? 'Crear estudiante' : 'Guardar cambios'}</button></div>}
        </section>
      </form>

      {student && mode === 'view' && (
        <>
          <section className="form-card student-actions-card">
            <div className="section-heading"><div><h2>Administración de cuenta</h2><p>Las operaciones conservan el historial y revocan sesiones cuando corresponde.</p></div></div>
            <div className="student-action-row">
              {permissions.has('STUDENT_UPDATE') && !['ARCHIVED', 'DELETED'].includes(student.status) && <Link className="secondary-button button-link" to={`/admin/students/${student.publicId}/edit`}><Icon name="edit" size={16} /> Editar</Link>}
              {permissions.has('STUDENT_STATUS_CHANGE') && student.status !== 'ACTIVE' && !['DELETED', 'ARCHIVED'].includes(student.status) && <button className="secondary-button" onClick={() => setPendingAction('ACTIVATE')}>Activar</button>}
              {permissions.has('STUDENT_STATUS_CHANGE') && student.status === 'ACTIVE' && <button className="secondary-button" onClick={() => setPendingAction('DEACTIVATE')}>Desactivar</button>}
              {permissions.has('STUDENT_STATUS_CHANGE') && !['SUSPENDED', 'ARCHIVED', 'DELETED'].includes(student.status) && <button className="secondary-button" onClick={() => setPendingAction('SUSPEND')}>Suspender</button>}
              {permissions.has('STUDENT_STATUS_CHANGE') && !['ARCHIVED', 'DELETED'].includes(student.status) && <button className="secondary-button" onClick={() => setPendingAction('ARCHIVE')}>Archivar</button>}
              {permissions.has('STUDENT_STATUS_CHANGE') && ['ARCHIVED', 'DELETED'].includes(student.status) && <button className="secondary-button" onClick={() => setPendingAction('RESTORE')}>Restaurar</button>}
              {permissions.has('STUDENT_STATUS_CHANGE') && student.status !== 'DELETED' && <button className="danger-button" onClick={() => setPendingAction('DELETE')}>Eliminar</button>}
              {permissions.has('STUDENT_PASSWORD_RESET') && !['ARCHIVED', 'DELETED'].includes(student.status) && <button className="secondary-button" onClick={() => void handleResetPassword()}>Restablecer contraseña</button>}
            </div>
            <dl className="student-metadata">
              <div><dt>Creado</dt><dd>{formatDate(student.createdAt)}</dd></div>
              <div><dt>Actualizado</dt><dd>{formatDate(student.updatedAt)}</dd></div>
              <div><dt>Último acceso</dt><dd>{formatDate(student.lastLoginAt)}</dd></div>
              <div><dt>Cambio obligatorio</dt><dd>{student.passwordChangeRequired ? 'Sí' : 'No'}</dd></div>
            </dl>
          </section>

          {permissions.has('STUDENT_SESSION_MANAGE') && (
            <section className="form-card student-sessions-card">
              <div className="section-heading"><div><h2>Sesiones</h2><p>Solo puede existir una sesión activa por estudiante.</p></div>{sessions.some((session) => session.status === 'ACTIVE') && <button className="danger-button" onClick={() => setPendingAction('REVOKE_ALL')}>Revocar sesión activa</button>}</div>
              <div className="ns-data-table-wrap"><table className="ns-data-table"><thead><tr><th>Estado</th><th>Inicio</th><th>Vencimiento</th><th>Dirección IP</th><th>Dispositivo</th><th>Acción</th></tr></thead><tbody>
                {sessions.length === 0 && <tr><td colSpan={6} className="ns-table-empty">No hay sesiones registradas.</td></tr>}
                {sessionsData.content.map((session) => <tr key={session.publicId}><td><span className={`status-badge status-${session.status.toLowerCase()}`}>{session.status}</span></td><td>{formatDate(session.createdAt)}</td><td>{formatDate(session.expiresAt)}</td><td>{session.ipAddress ?? '—'}</td><td className="student-user-agent">{session.userAgent ?? '—'}</td><td>{session.status === 'ACTIVE' ? <button className="danger-button compact-button" disabled={busySession === session.publicId} onClick={() => void handleRevokeSession(session.publicId)}>Revocar</button> : session.revocationReason ?? '—'}</td></tr>)}
              </tbody></table></div>
              <TablePagination compact currentPage={sessionsData.page} pageSize={sessionsData.size} totalElements={sessionsData.totalElements} totalPages={sessionsData.totalPages} onPageChange={setSessionsPage} onPageSizeChange={(nextSize) => { setSessionsSize(nextSize); setSessionsPage(0) }} />
            </section>
          )}
        </>
      )}

      <ConfirmDialog
        open={Boolean(pendingAction)}
        title={pendingAction === 'REVOKE_ALL' ? 'Revocar sesión activa' : 'Confirmar operación'}
        description={pendingAction === 'ACTIVATE' ? 'El estudiante podrá iniciar sesión si su vigencia está activa.' : pendingAction === 'RESTORE' ? 'Se restaurará como inactivo y conservará su historial.' : 'El acceso se retirará inmediatamente y cualquier sesión activa será revocada.'}
        confirmLabel="Confirmar"
        tone={pendingAction && ['SUSPEND', 'ARCHIVE', 'DELETE', 'REVOKE_ALL'].includes(pendingAction) ? 'danger' : 'primary'}
        busy={saving}
        onCancel={() => setPendingAction(null)}
        onConfirm={() => void executeLifecycle()}
      />
    </main>
  )
}
