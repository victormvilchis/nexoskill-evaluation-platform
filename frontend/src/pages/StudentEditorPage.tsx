import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useAuth } from '../features/authentication/context/AuthContext'
import { searchOrganizations } from '../features/organizations/api/organizationApi'
import { createStudent, getStudent, getStudentCatalogs, updateStudent } from '../features/students/api/studentApi'
import { StudentTemporaryCredentialsDialog } from '../features/students/components/StudentTemporaryCredentialsDialog'
import { ApiRequestError } from '../shared/api/apiClient'
import { BackButton } from '../shared/components/BackButton'
import { LoadingScreen } from '../shared/components/LoadingScreen'
import { useToast } from '../shared/components/ToastProvider'
import { useSaveNavigation } from '../shared/hooks/useSaveNavigation'
import type { OrganizationSummary } from '../features/organizations/types/organizations'
import type { StudentCatalogRef, StudentCatalogs, StudentDetail, StudentTemporaryCredentials } from '../shared/types/students'

interface Props { mode: 'create' | 'edit' | 'view' }

type CertificationFlags = {
  appliesTechnologicalCertification: boolean
  appliesDevelopmentSecurity: boolean
  appliesNormativeTesting: boolean
  appliesOne: boolean
  appliesAgile: boolean
}

const EMPTY_FLAGS: CertificationFlags = {
  appliesTechnologicalCertification: false,
  appliesDevelopmentSecurity: false,
  appliesNormativeTesting: false,
  appliesOne: false,
  appliesAgile: false
}

const FLAG_OPTIONS: Array<{ key: keyof CertificationFlags; label: string }> = [
  { key: 'appliesTechnologicalCertification', label: 'Aplica certificación tecnológica' },
  { key: 'appliesDevelopmentSecurity', label: 'Aplica Desarrollo Seguro' },
  { key: 'appliesNormativeTesting', label: 'Aplica Normativa y Testing' },
  { key: 'appliesOne', label: 'Aplica ONE' },
  { key: 'appliesAgile', label: 'Aplica Agile' }
]

function todayInput() {
  const today = new Date()
  const month = String(today.getMonth() + 1).padStart(2, '0')
  const day = String(today.getDate()).padStart(2, '0')
  return `${today.getFullYear()}-${month}-${day}`
}

function formatDate(value?: string | null) {
  if (!value) return 'Sin información'
  return new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium' }).format(new Date(`${value}T12:00:00`))
}

function includeCurrent(options: StudentCatalogRef[], current?: StudentCatalogRef | null) {
  return current && !options.some((item) => item.publicId === current.publicId) ? [...options, current] : options
}

function hasCertificationData(
  admissionDate: string,
  professionalProfilePublicId: string,
  technologicalProfilePublicId: string,
  flags: CertificationFlags
) {
  return Boolean(admissionDate || professionalProfilePublicId || technologicalProfilePublicId
    || Object.values(flags).some(Boolean))
}

export function StudentEditorPage({ mode }: Props) {
  const { publicId } = useParams()
  const navigate = useNavigate()
  const { user } = useAuth()
  const toast = useToast()
  const administrator = Boolean(user?.roles.includes('ADMINISTRATOR'))
  const readOnly = mode === 'view'
  const completeSave = useSaveNavigation('/admin/students')
  const organizationRequest = useRef(0)

  const [student, setStudent] = useState<StudentDetail | null>(null)
  const [organizations, setOrganizations] = useState<OrganizationSummary[]>([])
  const [organizationLoading, setOrganizationLoading] = useState(administrator && mode === 'create')
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
  const [temporaryCredentials, setTemporaryCredentials] = useState<StudentTemporaryCredentials>()
  const [validFrom, setValidFrom] = useState(todayInput())
  const [expiresAt, setExpiresAt] = useState('')
  const [admissionDate, setAdmissionDate] = useState('')
  const [professionalProfilePublicId, setProfessionalProfilePublicId] = useState('')
  const [technologicalProfilePublicId, setTechnologicalProfilePublicId] = useState('')
  const [flags, setFlags] = useState<CertificationFlags>(EMPTY_FLAGS)

  useEffect(() => {
    if (!administrator || mode !== 'create') return
    const controller = new AbortController()
    setOrganizationLoading(true)
    setOrganizationError(undefined)
    searchOrganizations({ status: 'ACTIVE', page: 0, size: 100, signal: controller.signal })
      .then((page) => {
        const today = todayInput()
        setOrganizations((page.content ?? []).filter((item) => item.organizationType === 'CUSTOMER'
          && item.code !== 'GLOBAL'
          && (!item.expiresOn || item.expiresOn >= today)))
      })
      .catch((requestError) => {
        if (!controller.signal.aborted) {
          setOrganizations([])
          setOrganizationError(requestError instanceof ApiRequestError
            ? requestError.message
            : 'No fue posible cargar las organizaciones disponibles.')
        }
      })
      .finally(() => { if (!controller.signal.aborted) setOrganizationLoading(false) })
    return () => controller.abort()
  }, [administrator, mode])

  useEffect(() => {
    if (mode === 'create' || !publicId) return
    let active = true
    setLoading(true)
    setError(null)
    getStudent(publicId)
      .then(async (detail) => {
        if (!active) return
        setStudent(detail)
        setOrganizationPublicId(detail.organization?.publicId ?? '')
        setStudentCode(detail.studentCode)
        setEmail(detail.email)
        setFirstName(detail.firstName)
        setLastName(detail.lastName)
        setDisplayName(detail.displayName)
        setValidFrom(detail.validFrom ?? '')
        setExpiresAt(detail.expiresAt ?? '')
        setAdmissionDate(detail.admissionDate ?? '')
        setProfessionalProfilePublicId(detail.professionalProfile?.publicId ?? '')
        setTechnologicalProfilePublicId(detail.technologicalProfile?.publicId ?? '')
        setFlags({
          appliesTechnologicalCertification: detail.appliesTechnologicalCertification,
          appliesDevelopmentSecurity: detail.appliesDevelopmentSecurity,
          appliesNormativeTesting: detail.appliesNormativeTesting,
          appliesOne: detail.appliesOne,
          appliesAgile: detail.appliesAgile
        })
        if (detail.organization?.appliesCertifications && mode === 'edit') {
          setCatalogLoading(true)
          try {
            const response = await getStudentCatalogs(administrator ? detail.organization.publicId : undefined)
            if (active) setCatalogs(response)
          } catch (requestError) {
            if (active) setCatalogError(requestError instanceof ApiRequestError
              ? requestError.message
              : 'No fue posible cargar los catálogos de la organización.')
          } finally {
            if (active) setCatalogLoading(false)
          }
        }
      })
      .catch((requestError) => {
        if (active) setError(requestError instanceof ApiRequestError
          ? requestError.message
          : 'No fue posible cargar al estudiante.')
      })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [administrator, mode, publicId])

  useEffect(() => {
    if (mode !== 'create' || administrator) return
    const controller = new AbortController()
    setCatalogLoading(true)
    setCatalogError(undefined)
    getStudentCatalogs(undefined, controller.signal)
      .then((response) => {
        setCatalogs(response)
        setOrganizationPublicId(response.organization.publicId)
      })
      .catch((requestError) => {
        if (!controller.signal.aborted) setCatalogError(requestError instanceof ApiRequestError
          ? requestError.message
          : 'No fue posible resolver la organización autenticada.')
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

  function clearCertificationData() {
    setAdmissionDate('')
    setProfessionalProfilePublicId('')
    setTechnologicalProfilePublicId('')
    setFlags(EMPTY_FLAGS)
  }

  async function changeOrganization(nextPublicId: string) {
    const requestId = ++organizationRequest.current
    const hadData = hasCertificationData(admissionDate, professionalProfilePublicId, technologicalProfilePublicId, flags)
    setOrganizationPublicId(nextPublicId)
    setCatalogs(undefined)
    setCatalogError(undefined)
    setFieldErrors((current) => ({ ...current, organizationPublicId: '' }))
    if (!nextPublicId) {
      if (hadData) clearCertificationData()
      return
    }
    setCatalogLoading(true)
    try {
      const response = await getStudentCatalogs(nextPublicId)
      if (requestId !== organizationRequest.current) return
      if (!response.appliesCertifications && hadData) {
        clearCertificationData()
        toast.warning('Datos condicionados retirados',
          'La organización seleccionada no utiliza gestión de certificaciones. Los datos de perfil y seguimiento fueron retirados.')
      }
      setCatalogs(response)
    } catch (requestError) {
      if (requestId !== organizationRequest.current) return
      setCatalogError(requestError instanceof ApiRequestError
        ? requestError.message
        : 'No fue posible consultar la configuración de la organización.')
    } finally {
      if (requestId === organizationRequest.current) setCatalogLoading(false)
    }
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

  function validateForm() {
    const errors: Record<string, string> = {}
    if (administrator && mode === 'create' && !organizationPublicId) errors.organizationPublicId = 'Debes seleccionar una organización.'
    if (!studentCode.trim() && mode === 'create') errors.studentCode = 'El código del estudiante es obligatorio.'
    if (!email.trim()) errors.email = 'El correo electrónico es obligatorio.'
    if (!firstName.trim()) errors.firstName = 'El nombre es obligatorio.'
    if (!lastName.trim()) errors.lastName = 'Los apellidos son obligatorios.'
    if (!validFrom) errors.validFrom = 'El inicio de vigencia es obligatorio.'
    if (!expiresAt) errors.expiresAt = 'La fecha de vencimiento es obligatoria.'
    if (validFrom && expiresAt && expiresAt < validFrom) {
      errors.expiresAt = 'La fecha de vencimiento no puede ser anterior al inicio de vigencia.'
    }
    if (appliesCertifications && !admissionDate) {
      errors.admissionDate = 'La fecha de alta es obligatoria cuando la organización aplica certificaciones.'
    }
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) focusFirstFieldError(errors)
    return Object.keys(errors).length === 0
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (readOnly || saving || !validateForm()) return
    setSaving(true)
    setError(null)
    const certificationPayload = appliesCertifications ? {
      ...(admissionDate ? { admissionDate } : {}),
      ...(professionalProfilePublicId ? { professionalProfilePublicId } : {}),
      ...(technologicalProfilePublicId ? { technologicalProfilePublicId } : {}),
      ...flags
    } : {}
    try {
      if (mode === 'create') {
        const response = await createStudent({
          ...(administrator ? { organizationPublicId } : {}),
          studentCode: studentCode.trim(),
          email: email.trim(),
          firstName: firstName.trim(),
          lastName: lastName.trim(),
          displayName: displayName.trim() || undefined,
          status: 'ACTIVE',
          validFrom,
          expiresAt,
          ...certificationPayload
        })
        setTemporaryCredentials(response.temporaryCredentials)
        toast.success('Estudiante creado correctamente', 'Guarda las credenciales antes de cerrar esta vista.')
      } else if (student && publicId) {
        const admissionChanged = appliesCertifications && student.admissionDate !== admissionDate
        await updateStudent(publicId, {
          email: email.trim(),
          firstName: firstName.trim(),
          lastName: lastName.trim(),
          displayName: displayName.trim() || undefined,
          validFrom,
          expiresAt,
          ...certificationPayload,
          version: student.version
        })
        completeSave({
          title: 'Estudiante actualizado correctamente.',
          message: admissionChanged
            ? 'La fecha de alta cambió; se recalcularon únicamente las fechas límite pendientes.'
            : undefined
        })
      }
    } catch (requestError) {
      if (requestError instanceof ApiRequestError) {
        const errors = requestError.fieldErrors ?? {}
        setError(requestError.message)
        setFieldErrors(errors)
        focusFirstFieldError(errors)
      } else {
        setError('No fue posible guardar al estudiante.')
      }
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <LoadingScreen />
  if (error && mode !== 'create' && !student) {
    return <main className="content-page"><BackButton fallback="/admin/students" />
      <div className="error-message" role="alert">{error}</div></main>
  }

  return (
    <main className="content-page editor-page student-editor-foundation">
      <BackButton fallback="/admin/students" />
      <header className="page-heading compact">
        <div>
          <p className="eyebrow">Estudiantes</p>
          <h1>{mode === 'create' ? 'Nueva persona estudiante' : mode === 'edit' ? 'Editar estudiante' : 'Ver estudiante'}</h1>
          <p className="muted">Inicio de vigencia y vencimiento controlan el acceso; la fecha de alta se usa únicamente para el seguimiento de certificaciones.</p>
        </div>
      </header>
      {error && <div className="error-message" role="alert">{error}</div>}

      <form className="student-foundation-form" onSubmit={handleSubmit} noValidate>
        {administrator && mode === 'create' && (
          <section className="editor-card student-organization-first">
            <div className="section-heading"><div><p className="eyebrow">Organización</p><h2>Selecciona primero la organización</h2></div></div>
            <div className="foundation-form-grid">
              <label className="form-field">
                <span>Organización</span>
                <select name="organizationPublicId" value={organizationPublicId}
                  onChange={(event) => void changeOrganization(event.target.value)}
                  disabled={organizationLoading} required aria-invalid={Boolean(fieldErrors.organizationPublicId)}>
                  <option value="">Seleccionar organización</option>
                  {organizations.map((item) => <option key={item.publicId} value={item.publicId}>{item.name} · {item.code}</option>)}
                </select>
                {field('organizationPublicId')}
              </label>
              {organizationLoading && <p className="form-help">Cargando organizaciones comerciales activas…</p>}
              {!organizationLoading && !organizationPublicId && <p className="form-help">Selecciona una organización para habilitar el resto del formulario.</p>}
              {organizationPublicId && catalogLoading && <p className="form-help">Consultando la configuración de {selectedOrganization?.name ?? 'la organización'}…</p>}
              {organizationError && <div className="error-message" role="alert">{organizationError}</div>}
              {catalogError && <div className="error-message" role="alert">{catalogError}</div>}
            </div>
          </section>
        )}

        {mode === 'create' && !administrator && catalogLoading && (
          <section className="editor-card"><p className="muted">Resolviendo la organización y su configuración…</p></section>
        )}
        {mode === 'create' && !administrator && catalogError && <div className="error-message" role="alert">{catalogError}</div>}

        {organizationResolved && (
          <>
            <section className="editor-card">
              <div className="section-heading"><div><p className="eyebrow">Datos generales</p><h2>Identidad y acceso</h2></div></div>
              <div className="foundation-form-grid">
                <label className="form-field"><span>Código</span>
                  {readOnly ? <strong className="readonly-value">{studentCode}</strong> :
                    <input name="studentCode" value={studentCode} onChange={(event) => setStudentCode(event.target.value)}
                      disabled={mode === 'edit'} required aria-invalid={Boolean(fieldErrors.studentCode)} />}
                  {field('studentCode')}
                </label>
                <label className="form-field"><span>Correo</span>
                  {readOnly ? <strong className="readonly-value">{email}</strong> :
                    <input name="email" type="email" value={email} onChange={(event) => setEmail(event.target.value)}
                      required aria-invalid={Boolean(fieldErrors.email)} />}
                  {field('email')}
                </label>
                <label className="form-field"><span>Nombre</span>
                  {readOnly ? <strong className="readonly-value">{firstName}</strong> :
                    <input name="firstName" value={firstName} onChange={(event) => setFirstName(event.target.value)}
                      required aria-invalid={Boolean(fieldErrors.firstName)} />}
                  {field('firstName')}
                </label>
                <label className="form-field"><span>Apellidos</span>
                  {readOnly ? <strong className="readonly-value">{lastName}</strong> :
                    <input name="lastName" value={lastName} onChange={(event) => setLastName(event.target.value)}
                      required aria-invalid={Boolean(fieldErrors.lastName)} />}
                  {field('lastName')}
                </label>
                <label className="form-field"><span>Nombre visible</span>
                  {readOnly ? <strong className="readonly-value">{displayName}</strong> :
                    <input name="displayName" value={displayName} onChange={(event) => setDisplayName(event.target.value)}
                      aria-invalid={Boolean(fieldErrors.displayName)} />}
                  {field('displayName')}
                </label>
                <label className="form-field"><span>Inicio de vigencia</span>
                  {readOnly ? <strong className="readonly-value">{formatDate(validFrom)}</strong> :
                    <input name="validFrom" type="date" value={validFrom} onChange={(event) => setValidFrom(event.target.value)}
                      required aria-invalid={Boolean(fieldErrors.validFrom)} />}
                  {field('validFrom')}
                </label>
                <label className="form-field"><span>Vencimiento</span>
                  {readOnly ? <strong className="readonly-value">{formatDate(expiresAt)}</strong> :
                    <input name="expiresAt" type="date" value={expiresAt} min={validFrom || undefined}
                      onChange={(event) => setExpiresAt(event.target.value)} required aria-invalid={Boolean(fieldErrors.expiresAt)} />}
                  {field('expiresAt')}
                </label>
                {readOnly && student?.organization && (
                  <label className="form-field"><span>Organización</span><strong className="readonly-value">{student.organization.name}</strong></label>
                )}
                {readOnly && student && (
                  <label className="form-field"><span>Estado</span><strong className="readonly-value">{student.effectiveStatus === 'ACTIVE' ? 'Activo' : student.effectiveStatus === 'INACTIVE' ? 'Desactivado' : 'Vencido'}</strong></label>
                )}
              </div>
            </section>

            {appliesCertifications && (
              <section className="editor-card">
                <div className="section-heading"><div><p className="eyebrow">Perfil profesional</p><h2>Clasificación profesional</h2></div></div>
                {catalogLoading && !readOnly && <p className="muted">Cargando catálogos de la organización…</p>}
                {catalogError && !readOnly && <div className="error-message" role="alert">{catalogError}</div>}
                {(!catalogLoading || readOnly) && (
                  <div className="foundation-form-grid foundation-form-grid--three">
                    <label className="form-field"><span>Perfil</span>
                      {readOnly ? <strong className="readonly-value">{student?.professionalProfile?.name ?? 'Sin información registrada'}</strong> :
                        <select name="professionalProfilePublicId" value={professionalProfilePublicId}
                          onChange={(event) => setProfessionalProfilePublicId(event.target.value)}
                          disabled={profileOptions.length === 0} aria-invalid={Boolean(fieldErrors.professionalProfilePublicId)}>
                          <option value="">Seleccionar perfil</option>
                          {profileOptions.map((item) => <option key={item.publicId} value={item.publicId}>{item.name}</option>)}
                        </select>}
                      {field('professionalProfilePublicId')}
                      {!readOnly && profileOptions.length === 0 && <small>Esta organización todavía no tiene perfiles activos configurados.</small>}
                    </label>
                    <label className="form-field"><span>Perfil tecnológico</span>
                      {readOnly ? <strong className="readonly-value">{student?.technologicalProfile?.name ?? 'Sin información registrada'}</strong> :
                        <select name="technologicalProfilePublicId" value={technologicalProfilePublicId}
                          onChange={(event) => setTechnologicalProfilePublicId(event.target.value)}
                          disabled={technologicalProfileOptions.length === 0}
                          aria-invalid={Boolean(fieldErrors.technologicalProfilePublicId)}>
                          <option value="">Seleccionar perfil tecnológico</option>
                          {technologicalProfileOptions.map((item) => <option key={item.publicId} value={item.publicId}>{item.name}</option>)}
                        </select>}
                      {field('technologicalProfilePublicId')}
                      {!readOnly && technologicalProfileOptions.length === 0 && <small>Esta organización todavía no tiene perfiles tecnológicos activos.</small>}
                    </label>
                    <label className="form-field"><span>Fecha de alta</span>
                      {readOnly ? <strong className="readonly-value">{formatDate(admissionDate)}</strong> :
                        <input name="admissionDate" type="date" value={admissionDate}
                          onChange={(event) => setAdmissionDate(event.target.value)} required
                          aria-invalid={Boolean(fieldErrors.admissionDate)} />}
                      {field('admissionDate')}
                      {!readOnly && mode === 'edit' && <small>Al cambiarla se recalculan solo las fechas límite pendientes; no se modifican intentos ni certificaciones aprobadas.</small>}
                    </label>
                  </div>
                )}
              </section>
            )}

            {appliesCertifications && (
              <section className="editor-card">
                <div className="section-heading"><div><p className="eyebrow">Certificaciones</p><h2>Seguimiento inicial</h2></div></div>
                <p className="muted">Selecciona únicamente las áreas que aplican. Tecnologías, niveles, intentos, resultados y vencimientos se administran en Administrar certificaciones.</p>
                <div className="student-certification-flags">
                  {FLAG_OPTIONS.map((option) => readOnly ? (
                    <div className="student-certification-flag-readonly" key={option.key}>
                      <span>{option.label.replace('Aplica ', '')}</span>
                      <strong>{flags[option.key] ? 'Sí aplica' : 'No aplica'}</strong>
                    </div>
                  ) : (
                    <div className="student-certification-flag" key={option.key}>
                      <input id={`student-${option.key}`} name={option.key} type="checkbox" checked={flags[option.key]}
                        onChange={(event) => {
                          const nextValue = event.target.checked
                          if (mode === 'edit' && !nextValue && flags[option.key]) {
                            const accepted = window.confirm('Esta área dejará de estar disponible para nuevos seguimientos. Si ya tiene ciclos, intentos o resultados, su historial se conservará.')
                            if (!accepted) return
                          }
                          setFlags((current) => ({ ...current, [option.key]: nextValue }))
                        }} />
                      <label htmlFor={`student-${option.key}`}>{option.label}</label>
                    </div>
                  ))}
                </div>
              </section>
            )}
          </>
        )}

        {!readOnly && organizationResolved && (
          <div className="form-actions">
            <button className="primary-button" type="submit"
              disabled={saving || catalogLoading || Boolean(catalogError)}>
              {saving ? 'Guardando…' : mode === 'create' ? 'Crear estudiante' : 'Guardar cambios'}
            </button>
          </div>
        )}
      </form>
      {temporaryCredentials && (
        <StudentTemporaryCredentialsDialog title="Estudiante creado correctamente"
          credentials={temporaryCredentials}
          onClose={() => {
            setTemporaryCredentials(undefined)
            navigate('/admin/students', { replace: true })
          }} />
      )}
    </main>
  )
}
