import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { useParams } from 'react-router-dom'
import { useAuth } from '../features/authentication/context/AuthContext'
import { searchOrganizations } from '../features/organizations/api/organizationApi'
import { createStudent, getStudent, getStudentCatalogs, updateStudent } from '../features/students/api/studentApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { BackButton } from '../shared/components/BackButton'
import { LoadingScreen } from '../shared/components/LoadingScreen'
import { useToast } from '../shared/components/ToastProvider'
import { useSaveNavigation } from '../shared/hooks/useSaveNavigation'
import type { OrganizationSummary } from '../features/organizations/types/organizations'
import type { StudentCatalogRef, StudentCatalogs, StudentDetail } from '../shared/types/students'

interface Props { mode: 'create' | 'edit' | 'view' }
function toLocalInput(value: string | null | undefined) {
  if (!value) return ''
  const date = new Date(value)
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16)
}
function toIso(value: string) { return value ? new Date(value).toISOString() : null }
function hasCertificationValues(values: string[]) { return values.some(Boolean) }
function includeCurrent(options: StudentCatalogRef[], current?: StudentCatalogRef | null) {
  return current && !options.some((item) => item.publicId === current.publicId) ? [...options, current] : options
}

export function StudentEditorPage({ mode }: Props) {
  const { publicId } = useParams()
  const { user } = useAuth()
  const toast = useToast()
  const administrator = Boolean(user?.roles.includes('ADMINISTRATOR'))
  const completeSave = useSaveNavigation('/admin/students')
  const readOnly = mode === 'view'
  const [student, setStudent] = useState<StudentDetail | null>(null)
  const [organizations, setOrganizations] = useState<OrganizationSummary[]>([])
  const [organizationError, setOrganizationError] = useState<string>()
  const [catalogs, setCatalogs] = useState<StudentCatalogs>()
  const [catalogLoading, setCatalogLoading] = useState(false)
  const [catalogError, setCatalogError] = useState<string>()
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

  useEffect(() => {
    if (!administrator || mode !== 'create') return
    const controller = new AbortController()
    setOrganizationError(undefined)
    searchOrganizations({ status: 'ACTIVE', page: 0, size: 100, signal: controller.signal })
      .then((page) => {
        const today = new Date(); today.setHours(0, 0, 0, 0)
        setOrganizations(page.content.filter((item) => item.organizationType === 'CUSTOMER'
          && (!item.expiresOn || new Date(`${item.expiresOn}T23:59:59`).getTime() >= today.getTime())))
      })
      .catch((requestError) => {
        if (!controller.signal.aborted) {
          setOrganizations([])
          setOrganizationError(requestError instanceof ApiRequestError
            ? requestError.message
            : 'No fue posible cargar las organizaciones disponibles.')
        }
      })
    return () => controller.abort()
  }, [administrator, mode])

  useEffect(() => {
    if (mode === 'create' || !publicId) return
    let active = true
    setLoading(true)
    getStudent(publicId).then(async (detail) => {
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
      if (detail.organization?.appliesCertifications && mode === 'edit') {
        setCatalogLoading(true)
        try {
          const response = await getStudentCatalogs(administrator ? detail.organization.publicId : undefined)
          if (active) setCatalogs(response)
        } catch (requestError) {
          if (active) setCatalogError(requestError instanceof ApiRequestError
            ? requestError.message : 'No fue posible cargar los catálogos de certificación.')
        } finally { if (active) setCatalogLoading(false) }
      }
    }).catch((requestError) => {
      if (active) setError(requestError instanceof ApiRequestError ? requestError.message : 'No fue posible cargar al estudiante.')
    }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [administrator, mode, publicId])

  useEffect(() => {
    if (mode !== 'create' || administrator) return
    const controller = new AbortController()
    setCatalogLoading(true)
    getStudentCatalogs(undefined, controller.signal)
      .then((response) => {
        setCatalogs(response)
        setOrganizationPublicId(response.organization.publicId)
      })
      .catch((requestError) => {
        if (!controller.signal.aborted) setCatalogError(requestError instanceof ApiRequestError
          ? requestError.message : 'No fue posible resolver la organización autenticada.')
      })
      .finally(() => { if (!controller.signal.aborted) setCatalogLoading(false) })
    return () => controller.abort()
  }, [administrator, mode])

  const selectedOrganization = useMemo(
    () => organizations.find((item) => item.publicId === organizationPublicId),
    [organizationPublicId, organizations]
  )
  const organizationResolved = mode === 'create' ? Boolean(catalogs?.organization) : Boolean(student?.organization)
  const appliesCertifications = mode === 'create'
    ? Boolean(catalogs?.appliesCertifications)
    : Boolean(student?.organization?.appliesCertifications)
  const profileOptions = includeCurrent(catalogs?.profiles ?? [], student?.professionalProfile)
  const technologicalProfileOptions = includeCurrent(catalogs?.technologicalProfiles ?? [], student?.technologicalProfile)
  const technologyOptions = includeCurrent(catalogs?.technologies ?? [], student?.technology)

  function clearCertificationFields() {
    setProfessionalProfilePublicId('')
    setTechnologicalProfilePublicId('')
    setTechnologyPublicId('')
    setCertificationEnrollmentDate('')
  }

  async function changeOrganization(nextPublicId: string) {
    const captured = hasCertificationValues([
      professionalProfilePublicId, technologicalProfilePublicId, technologyPublicId, certificationEnrollmentDate
    ])
    setOrganizationPublicId(nextPublicId)
    setCatalogs(undefined)
    setCatalogError(undefined)
    setFieldErrors((current) => ({ ...current, organizationPublicId: '' }))
    if (!nextPublicId) {
      if (captured) clearCertificationFields()
      return
    }
    setCatalogLoading(true)
    try {
      const response = await getStudentCatalogs(nextPublicId)
      if (!response.appliesCertifications && captured) {
        clearCertificationFields()
        toast.warning('Información de perfil y certificación retirada',
          'La organización seleccionada no utiliza gestión de certificaciones. Los datos capturados fueron retirados del formulario.')
      }
      setCatalogs(response)
    } catch (requestError) {
      setCatalogError(requestError instanceof ApiRequestError
        ? requestError.message : 'No fue posible consultar la configuración de la organización.')
    } finally { setCatalogLoading(false) }
  }

  function field(name: string) {
    const message = fieldErrors[name]
    return message ? <small className="field-error" role="alert">{message}</small> : null
  }

  function focusFirstFieldError(errors: Record<string, string>) {
    const first = Object.keys(errors)[0]
    if (!first) return
    window.setTimeout(() => {
      const element = document.querySelector<HTMLElement>(`[name="${CSS.escape(first)}"]`)
      element?.focus()
      element?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    }, 0)
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (readOnly || saving) return
    if (administrator && mode === 'create' && !organizationPublicId) {
      const errors = { organizationPublicId: 'Debes seleccionar una organización.' }
      setFieldErrors(errors)
      focusFirstFieldError(errors)
      return
    }
    setSaving(true)
    setError(null)
    setFieldErrors({})
    const certificationFields = appliesCertifications ? {
      ...(professionalProfilePublicId ? { professionalProfilePublicId } : {}),
      ...(technologicalProfilePublicId ? { technologicalProfilePublicId } : {}),
      ...(technologyPublicId ? { technologyPublicId } : {}),
      ...(certificationEnrollmentDate ? { certificationEnrollmentDate } : {})
    } : {}
    try {
      if (mode === 'create') {
        await createStudent({
          ...(administrator ? { organizationPublicId } : {}),
          studentCode: studentCode.trim(), email: email.trim(), firstName: firstName.trim(), lastName: lastName.trim(),
          displayName: displayName.trim() || undefined, temporaryPassword, status: 'ACTIVE',
          validFrom: toIso(validFrom)!, expiresAt: toIso(expiresAt), ...certificationFields
        })
        completeSave({ title: 'Estudiante creado correctamente.', message: 'La cuenta se creó en la organización autorizada.' })
      } else if (student && publicId) {
        await updateStudent(publicId, {
          email: email.trim(), firstName: firstName.trim(), lastName: lastName.trim(),
          displayName: displayName.trim() || undefined, validFrom: toIso(validFrom)!, expiresAt: toIso(expiresAt),
          ...certificationFields, version: student.version
        })
        completeSave({ title: 'Estudiante actualizado correctamente.' })
      }
    } catch (requestError) {
      if (requestError instanceof ApiRequestError) {
        const errors = requestError.fieldErrors ?? {}
        setError(requestError.message)
        setFieldErrors(errors)
        focusFirstFieldError(errors)
      } else setError('No fue posible guardar al estudiante.')
    } finally { setSaving(false) }
  }

  if (loading) return <LoadingScreen />
  if (error && mode !== 'create' && !student) return <main className="content-page"><BackButton fallback="/admin/students" /><div className="error-message">{error}</div></main>

  return (
    <main className="content-page editor-page student-editor-foundation">
      <BackButton fallback="/admin/students" />
      <header className="page-heading compact"><div><p className="eyebrow">Estudiantes</p><h1>{mode === 'create' ? 'Nueva persona estudiante' : mode === 'edit' ? 'Editar estudiante' : 'Ver estudiante'}</h1><p className="muted">La configuración real de la organización determina si aplica información profesional y certificaciones.</p></div></header>
      {error && <div className="error-message" role="alert">{error}</div>}
      <form className="student-foundation-form" onSubmit={handleSubmit} noValidate>
        {administrator && mode === 'create' && <section className="editor-card student-organization-first"><div className="section-heading"><div><p className="eyebrow">Organización</p><h2>Selecciona primero la organización</h2></div></div><div className="foundation-form-grid">
          <label className="form-field"><span>Organización</span><select name="organizationPublicId" value={organizationPublicId} onChange={(event) => void changeOrganization(event.target.value)} required aria-invalid={Boolean(fieldErrors.organizationPublicId)}><option value="">Seleccionar organización</option>{organizations.map((item) => <option key={item.publicId} value={item.publicId}>{item.name} · {item.code}</option>)}</select>{field('organizationPublicId')}</label>
          {!organizationPublicId && <p className="form-help">Selecciona una organización comercial activa y vigente para continuar.</p>}
          {organizationPublicId && catalogLoading && <p className="form-help">Consultando la configuración de {selectedOrganization?.name ?? 'la organización'}…</p>}
          {organizationError && <div className="error-message" role="alert">{organizationError}</div>}
          {catalogError && <div className="error-message" role="alert">{catalogError}</div>}
        </div></section>}

        {mode === 'create' && !administrator && catalogLoading && <section className="editor-card"><p className="muted">Resolviendo la organización y su configuración…</p></section>}
        {mode === 'create' && !administrator && catalogError && <div className="error-message" role="alert">{catalogError}</div>}

        {organizationResolved && <>
          <section className="editor-card"><div className="section-heading"><div><p className="eyebrow">Datos generales</p><h2>Identidad y vigencia</h2></div></div><div className="foundation-form-grid">
            <label className="form-field"><span>Código</span><input name="studentCode" value={studentCode} onChange={(event) => setStudentCode(event.target.value)} disabled={readOnly || mode === 'edit'} required aria-invalid={Boolean(fieldErrors.studentCode)} />{field('studentCode')}</label>
            <label className="form-field"><span>Correo</span><input name="email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} disabled={readOnly} required aria-invalid={Boolean(fieldErrors.email)} />{field('email')}</label>
            <label className="form-field"><span>Nombre</span><input name="firstName" value={firstName} onChange={(event) => setFirstName(event.target.value)} disabled={readOnly} required aria-invalid={Boolean(fieldErrors.firstName)} />{field('firstName')}</label>
            <label className="form-field"><span>Apellidos</span><input name="lastName" value={lastName} onChange={(event) => setLastName(event.target.value)} disabled={readOnly} required aria-invalid={Boolean(fieldErrors.lastName)} />{field('lastName')}</label>
            <label className="form-field"><span>Nombre visible</span><input name="displayName" value={displayName} onChange={(event) => setDisplayName(event.target.value)} disabled={readOnly} aria-invalid={Boolean(fieldErrors.displayName)} />{field('displayName')}</label>
            {mode === 'create' && <label className="form-field"><span>Contraseña temporal</span><input name="temporaryPassword" type="password" value={temporaryPassword} onChange={(event) => setTemporaryPassword(event.target.value)} disabled={readOnly} minLength={10} required aria-invalid={Boolean(fieldErrors.temporaryPassword)} />{field('temporaryPassword')}</label>}
            <label className="form-field"><span>Inicio de vigencia</span><input name="validFrom" type="datetime-local" value={validFrom} onChange={(event) => setValidFrom(event.target.value)} disabled={readOnly} required aria-invalid={Boolean(fieldErrors.validFrom)} />{field('validFrom')}</label>
            <label className="form-field"><span>Vencimiento</span><input name="expiresAt" type="datetime-local" value={expiresAt} onChange={(event) => setExpiresAt(event.target.value)} disabled={readOnly} aria-invalid={Boolean(fieldErrors.expiresAt)} />{field('expiresAt')}</label>
          </div></section>

          {appliesCertifications && <section className="editor-card"><div className="section-heading"><div><p className="eyebrow">Perfil profesional</p><h2>Clasificación inicial</h2></div></div>
            {catalogLoading && <p className="muted">Cargando catálogos de la organización…</p>}
            {catalogError && <div className="error-message" role="alert">{catalogError}</div>}
            {!catalogLoading && !catalogError && <div className="foundation-form-grid foundation-form-grid--three">
              <label className="form-field"><span>Perfil</span>{readOnly ? <strong className="readonly-value">{student?.professionalProfile?.name ?? 'Sin información'}</strong> : <select name="professionalProfilePublicId" value={professionalProfilePublicId} onChange={(event) => setProfessionalProfilePublicId(event.target.value)} disabled={profileOptions.length === 0} aria-invalid={Boolean(fieldErrors.professionalProfilePublicId)}><option value="">Seleccionar perfil</option>{profileOptions.map((item) => <option key={item.publicId} value={item.publicId}>{item.name}</option>)}</select>}{field('professionalProfilePublicId')}{profileOptions.length === 0 && !readOnly && <small>Esta organización todavía no tiene perfiles activos configurados.</small>}</label>
              <label className="form-field"><span>Perfil tecnológico</span>{readOnly ? <strong className="readonly-value">{student?.technologicalProfile?.name ?? 'Sin información'}</strong> : <select name="technologicalProfilePublicId" value={technologicalProfilePublicId} onChange={(event) => setTechnologicalProfilePublicId(event.target.value)} disabled={technologicalProfileOptions.length === 0} aria-invalid={Boolean(fieldErrors.technologicalProfilePublicId)}><option value="">Seleccionar perfil tecnológico</option>{technologicalProfileOptions.map((item) => <option key={item.publicId} value={item.publicId}>{item.name}</option>)}</select>}{field('technologicalProfilePublicId')}{technologicalProfileOptions.length === 0 && !readOnly && <small>Esta organización todavía no tiene perfiles tecnológicos activos.</small>}</label>
              <label className="form-field"><span>Tecnología</span>{readOnly ? <strong className="readonly-value">{student?.technology?.name ?? 'Sin información'}</strong> : <select name="technologyPublicId" value={technologyPublicId} onChange={(event) => setTechnologyPublicId(event.target.value)} disabled={technologyOptions.length === 0} aria-invalid={Boolean(fieldErrors.technologyPublicId)}><option value="">Seleccionar tecnología</option>{technologyOptions.map((item) => <option key={item.publicId} value={item.publicId}>{item.name}</option>)}</select>}{field('technologyPublicId')}{technologyOptions.length === 0 && !readOnly && <small>Esta organización todavía no tiene tecnologías activas configuradas.</small>}</label>
            </div>}
          </section>}

          {appliesCertifications && <section className="editor-card"><div className="section-heading"><div><p className="eyebrow">Certificaciones</p><h2>Configuración inicial</h2></div></div><div className="foundation-form-grid"><label className="form-field"><span>Fecha de alta</span><input name="certificationEnrollmentDate" type="date" value={certificationEnrollmentDate} onChange={(event) => setCertificationEnrollmentDate(event.target.value)} disabled={readOnly} aria-invalid={Boolean(fieldErrors.certificationEnrollmentDate)} />{field('certificationEnrollmentDate')}</label><p className="form-help">El seguimiento detallado se realiza desde Administrar certificaciones.</p></div></section>}
        </>}
        {!readOnly && organizationResolved && <div className="form-actions"><button className="primary-button" type="submit" disabled={saving || catalogLoading || Boolean(catalogError)}>{saving ? 'Guardando…' : mode === 'create' ? 'Crear estudiante' : 'Guardar cambios'}</button></div>}
      </form>
    </main>
  )
}
