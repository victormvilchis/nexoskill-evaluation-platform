import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { useParams } from 'react-router-dom'
import { useAuth } from '../features/authentication/context/AuthContext'
import { searchOrganizations } from '../features/organizations/api/organizationApi'
import { createStudent, getStudent, getStudentCatalogs, updateStudent } from '../features/students/api/studentApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { BackButton } from '../shared/components/BackButton'
import { LoadingScreen } from '../shared/components/LoadingScreen'
import { useSaveNavigation } from '../shared/hooks/useSaveNavigation'
import type { OrganizationSummary } from '../features/organizations/types/organizations'
import type { StudentCatalogs, StudentDetail } from '../shared/types/students'

interface Props { mode: 'create' | 'edit' | 'view' }
function toLocalInput(value: string | null | undefined) {
  if (!value) return ''
  const date = new Date(value)
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16)
}
function toIso(value: string) { return value ? new Date(value).toISOString() : null }

export function StudentEditorPage({ mode }: Props) {
  const { publicId } = useParams()
  const { user } = useAuth()
  const administrator = Boolean(user?.roles.includes('ADMINISTRATOR'))
  const completeSave = useSaveNavigation('/admin/students')
  const [student, setStudent] = useState<StudentDetail | null>(null)
  const [organizations, setOrganizations] = useState<OrganizationSummary[]>([])
  const [catalogs, setCatalogs] = useState<StudentCatalogs>({ profiles: [], technologicalProfiles: [], technologies: [] })
  const [loading, setLoading] = useState(mode !== 'create')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [organizationPublicId, setOrganizationPublicId] = useState('')
  const [studentCode, setStudentCode] = useState('')
  const [email, setEmail] = useState('')
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [temporaryPassword, setTemporaryPassword] = useState('')
  const [validFrom, setValidFrom] = useState(toLocalInput(new Date().toISOString()))
  const [expiresAt, setExpiresAt] = useState('')
  const [professionalProfilePublicId, setProfessionalProfilePublicId] = useState('')
  const [technologicalProfilePublicId, setTechnologicalProfilePublicId] = useState('')
  const [technologyPublicId, setTechnologyPublicId] = useState('')
  const [certificationEnrollmentDate, setCertificationEnrollmentDate] = useState('')
  const readOnly = mode === 'view'

  useEffect(() => {
    const controller = new AbortController()
    getStudentCatalogs(controller.signal).then(setCatalogs).catch(() => setCatalogs({ profiles: [], technologicalProfiles: [], technologies: [] }))
    if (administrator) {
      searchOrganizations({ status: 'ACTIVE', page: 0, size: 100, signal: controller.signal })
        .then((page) => setOrganizations(page.content.filter((item) => item.organizationType === 'CUSTOMER')))
        .catch(() => setOrganizations([]))
    }
    return () => controller.abort()
  }, [administrator])

  useEffect(() => {
    if (mode === 'create' || !publicId) return
    let active = true
    setLoading(true)
    getStudent(publicId).then((detail) => {
      if (!active) return
      setStudent(detail)
      setOrganizationPublicId(detail.organization?.publicId ?? '')
      setStudentCode(detail.studentCode)
      setEmail(detail.email)
      setFirstName(detail.firstName)
      setLastName(detail.lastName)
      setDisplayName(detail.displayName)
      setValidFrom(toLocalInput(detail.validFrom))
      setExpiresAt(toLocalInput(detail.expiresAt))
      setProfessionalProfilePublicId(detail.professionalProfile?.publicId ?? '')
      setTechnologicalProfilePublicId(detail.technologicalProfile?.publicId ?? '')
      setTechnologyPublicId(detail.technology?.publicId ?? '')
      setCertificationEnrollmentDate(detail.certificationEnrollmentDate ?? '')
    }).catch((requestError) => {
      if (active) setError(requestError instanceof ApiRequestError ? requestError.message : 'No fue posible cargar al estudiante.')
    }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [mode, publicId])

  const selectedOrganization = useMemo(() => organizations.find((item) => item.publicId === organizationPublicId), [organizationPublicId, organizations])
  const appliesCertifications = student?.organization?.appliesCertifications ?? selectedOrganization?.appliesCertifications ?? false

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (readOnly || saving) return
    if (administrator && mode === 'create' && !organizationPublicId) {
      setFieldErrors({ organizationPublicId: 'Selecciona la organización del estudiante.' })
      return
    }
    setSaving(true); setError(null); setFieldErrors({})
    try {
      if (mode === 'create') {
        await createStudent({
          organizationPublicId: administrator ? organizationPublicId : undefined,
          studentCode: studentCode.trim(), email: email.trim(), firstName: firstName.trim(), lastName: lastName.trim(),
          displayName: displayName.trim() || undefined, temporaryPassword, status: 'ACTIVE',
          validFrom: toIso(validFrom)!, expiresAt: toIso(expiresAt),
          professionalProfilePublicId: professionalProfilePublicId || undefined,
          technologicalProfilePublicId: technologicalProfilePublicId || undefined,
          technologyPublicId: technologyPublicId || undefined,
          certificationEnrollmentDate: appliesCertifications ? certificationEnrollmentDate || null : null
        })
        completeSave({ title: 'Estudiante creado correctamente.', message: 'La cuenta se creó activa en la organización autorizada.' })
      } else if (student && publicId) {
        await updateStudent(publicId, {
          email: email.trim(), firstName: firstName.trim(), lastName: lastName.trim(), displayName: displayName.trim() || undefined,
          validFrom: toIso(validFrom)!, expiresAt: toIso(expiresAt),
          professionalProfilePublicId: professionalProfilePublicId || undefined,
          technologicalProfilePublicId: technologicalProfilePublicId || undefined,
          technologyPublicId: technologyPublicId || undefined,
          certificationEnrollmentDate: appliesCertifications ? certificationEnrollmentDate || null : null,
          version: student.version
        })
        completeSave({ title: 'Estudiante actualizado correctamente.' })
      }
    } catch (requestError) {
      if (requestError instanceof ApiRequestError) { setError(requestError.message); setFieldErrors(requestError.fieldErrors ?? {}) }
      else setError('No fue posible guardar al estudiante.')
    } finally { setSaving(false) }
  }

  if (loading) return <LoadingScreen />
  if (error && mode !== 'create' && !student) return <main className="content-page"><BackButton fallback="/admin/students" /><div className="error-message">{error}</div></main>

  return (
    <main className="content-page editor-page student-editor-foundation">
      <BackButton fallback="/admin/students" />
      <header className="page-heading compact"><div><p className="eyebrow">Estudiantes</p><h1>{mode === 'create' ? 'Nuevo estudiante' : mode === 'edit' ? 'Editar estudiante' : 'Ver estudiante'}</h1><p className="muted">Datos generales, clasificación profesional y configuración inicial de certificaciones.</p></div></header>
      {error && <div className="error-message" role="alert">{error}</div>}
      <form className="student-foundation-form" onSubmit={handleSubmit}>
        <section className="editor-card"><div className="section-heading"><div><p className="eyebrow">Datos generales</p><h2>Identidad y organización</h2></div></div><div className="foundation-form-grid">
          {administrator && <label className="form-field"><span>Organización</span>{readOnly || mode === 'edit' ? <strong className="readonly-value">{student?.organization?.name ?? selectedOrganization?.name ?? '—'}</strong> : <select value={organizationPublicId} onChange={(event) => setOrganizationPublicId(event.target.value)} required><option value="">Seleccionar organización</option>{organizations.map((item) => <option key={item.publicId} value={item.publicId}>{item.name} · {item.code}</option>)}</select>}{fieldErrors.organizationPublicId && <small className="field-error">{fieldErrors.organizationPublicId}</small>}</label>}
          {!administrator && <div className="form-field"><span>Organización</span><strong className="readonly-value">Se asignará desde la sesión autenticada</strong></div>}
          <label className="form-field"><span>Código</span><input value={studentCode} onChange={(event) => setStudentCode(event.target.value)} disabled={readOnly || mode === 'edit'} required /></label>
          <label className="form-field"><span>Correo</span><input type="email" value={email} onChange={(event) => setEmail(event.target.value)} disabled={readOnly} required /></label>
          <label className="form-field"><span>Nombre</span><input value={firstName} onChange={(event) => setFirstName(event.target.value)} disabled={readOnly} required /></label>
          <label className="form-field"><span>Apellidos</span><input value={lastName} onChange={(event) => setLastName(event.target.value)} disabled={readOnly} required /></label>
          <label className="form-field"><span>Nombre visible</span><input value={displayName} onChange={(event) => setDisplayName(event.target.value)} disabled={readOnly} /></label>
          {mode === 'create' && <label className="form-field"><span>Contraseña temporal</span><input type="password" value={temporaryPassword} onChange={(event) => setTemporaryPassword(event.target.value)} disabled={readOnly} minLength={10} required /></label>}
          <label className="form-field"><span>Inicio de vigencia</span><input type="datetime-local" value={validFrom} onChange={(event) => setValidFrom(event.target.value)} disabled={readOnly} required /></label>
          <label className="form-field"><span>Vencimiento</span><input type="datetime-local" value={expiresAt} onChange={(event) => setExpiresAt(event.target.value)} disabled={readOnly} /></label>
        </div></section>
        <section className="editor-card"><div className="section-heading"><div><p className="eyebrow">Perfil profesional</p><h2>Clasificación inicial</h2></div></div><div className="foundation-form-grid foundation-form-grid--three">
          <label className="form-field"><span>Perfil</span><select value={professionalProfilePublicId} onChange={(event) => setProfessionalProfilePublicId(event.target.value)} disabled={readOnly}><option value="">Sin perfil</option>{catalogs.profiles.map((item) => <option key={item.publicId} value={item.publicId}>{item.name}</option>)}</select></label>
          <label className="form-field"><span>Perfil tecnológico</span><select value={technologicalProfilePublicId} onChange={(event) => setTechnologicalProfilePublicId(event.target.value)} disabled={readOnly}><option value="">Sin perfil tecnológico</option>{catalogs.technologicalProfiles.map((item) => <option key={item.publicId} value={item.publicId}>{item.name}</option>)}</select></label>
          <label className="form-field"><span>Tecnología</span><select value={technologyPublicId} onChange={(event) => setTechnologyPublicId(event.target.value)} disabled={readOnly}><option value="">Sin tecnología</option>{catalogs.technologies.map((item) => <option key={item.publicId} value={item.publicId}>{item.name}</option>)}</select></label>
        </div></section>
        {appliesCertifications && <section className="editor-card"><div className="section-heading"><div><p className="eyebrow">Certificaciones</p><h2>Configuración inicial</h2></div><span className="status-badge status-active">Habilitadas por la organización</span></div><div className="foundation-form-grid"><label className="form-field"><span>Fecha de alta</span><input type="date" value={certificationEnrollmentDate} onChange={(event) => setCertificationEnrollmentDate(event.target.value)} disabled={readOnly} /></label><p className="form-help">Los intentos, promedios, fechas límite y estados se administran desde el módulo independiente de certificaciones.</p></div></section>}
        {!readOnly && <div className="form-actions"><button className="primary-button" type="submit" disabled={saving}>{saving ? 'Guardando…' : mode === 'create' ? 'Crear estudiante' : 'Guardar cambios'}</button></div>}
      </form>
    </main>
  )
}
