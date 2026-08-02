import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useAuth } from '../features/authentication/context/AuthContext'
import { searchOrganizations } from '../features/organizations/api/organizationApi'
import { getStudentExperience, updateStudentExperience } from '../features/students/api/studentImportApi'
import { createStudent, getStudent, getStudentCatalogs, updateStudent } from '../features/students/api/studentApi'
import { StudentExperienceFields } from '../features/students/components/StudentExperienceFields'
import { StudentTemporaryCredentialsDialog } from '../features/students/components/StudentTemporaryCredentialsDialog'
import type { StudentExperiencePayload } from '../features/students/types/studentImport'
import { ApiRequestError } from '../shared/api/apiClient'
import { BackButton } from '../shared/components/BackButton'
import { ConfirmDialog } from '../shared/components/ConfirmDialog'
import { DateField } from '../shared/components/DateField'
import { FormActions } from '../shared/components/FormActions'
import { LoadingScreen } from '../shared/components/LoadingScreen'
import { SelectField } from '../shared/components/SelectField'
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
  appliesJira: boolean
}
const EMPTY_FLAGS: CertificationFlags = {
  appliesTechnologicalCertification: false,
  appliesDevelopmentSecurity: false,
  appliesNormativeTesting: false,
  appliesOne: false,
  appliesAgile: false,
  appliesJira: false
}
const EMPTY_EXPERIENCE: StudentExperiencePayload = {
  currentTechnologies: [],
  languages: [],
  knownTechnologies: []
}
const FLAG_OPTIONS: Array<{ key: keyof CertificationFlags; label: string }> = [
  { key: 'appliesTechnologicalCertification', label: 'Aplica certificación tecnológica' },
  { key: 'appliesDevelopmentSecurity', label: 'Aplica Desarrollo Seguro' },
  { key: 'appliesNormativeTesting', label: 'Aplica Normativa y Testing' },
  { key: 'appliesOne', label: 'Aplica ONE' },
  { key: 'appliesAgile', label: 'Aplica Agile' },
  { key: 'appliesJira', label: 'Aplica Jira' }
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
function cleanExperience(value: StudentExperiencePayload): StudentExperiencePayload {
  const clean = (items: StudentExperiencePayload['currentTechnologies']) => items
    .map((item) => ({ name: item.name.trim(), level: item.level }))
    .filter((item) => item.name)
  return {
    currentTechnologies: clean(value.currentTechnologies),
    languages: clean(value.languages),
    knownTechnologies: clean(value.knownTechnologies)
  }
}
function hasCertificationData(professionalProfilePublicId: string,
  technologicalProfilePublicId: string, flags: CertificationFlags) {
  return Boolean(professionalProfilePublicId || technologicalProfilePublicId
    || Object.values(flags).some(Boolean))
}

export function StudentEditorPage({ mode }: Props) {
  const { publicId } = useParams()
  const navigate = useNavigate()
  const { user } = useAuth()
  const toast = useToast()
  const administrator = Boolean(user?.roles.includes('ADMINISTRATOR'))
  const readOnly = mode === 'view'
  const completeSave = useSaveNavigation('/admin/collaborators')
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
  const [corporateUser, setCorporateUser] = useState('')
  const [email, setEmail] = useState('')
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [temporaryCredentials, setTemporaryCredentials] = useState<StudentTemporaryCredentials>()
  const [validFrom, setValidFrom] = useState(todayInput())
  const [expiresAt, setExpiresAt] = useState('')
  const [admissionDate, setAdmissionDate] = useState('')
  const [professionalProfilePublicId, setProfessionalProfilePublicId] = useState('')
  const [technologicalProfilePublicId, setTechnologicalProfilePublicId] = useState('')
  const [flags, setFlags] = useState<CertificationFlags>(EMPTY_FLAGS)
  const [experience, setExperience] = useState<StudentExperiencePayload>(EMPTY_EXPERIENCE)
  const [confirmingDeactivation, setConfirmingDeactivation] = useState(false)
  const [pendingFlagRemoval, setPendingFlagRemoval] = useState<keyof CertificationFlags>()

  useEffect(() => {
    if (!administrator || mode !== 'create') return
    const controller = new AbortController()
    setOrganizationLoading(true)
    setOrganizationError(undefined)
    searchOrganizations({ status: 'ACTIVE', page: 0, size: 100, signal: controller.signal })
      .then((page) => {
        const today = todayInput()
        setOrganizations((page.content ?? []).filter((item) => item.organizationType === 'CUSTOMER'
          && item.code !== 'GLOBAL' && (!item.expiresOn || item.expiresOn >= today)))
      })
      .catch((requestError) => {
        if (!controller.signal.aborted) {
          setOrganizations([])
          setOrganizationError(requestError instanceof ApiRequestError
            ? requestError.message : 'No fue posible cargar las organizaciones disponibles.')
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
    Promise.all([getStudent(publicId), getStudentExperience(publicId)])
      .then(async ([detail, experienceValue]) => {
        if (!active) return
        setStudent(detail)
        setExperience(experienceValue)
        setOrganizationPublicId(detail.organization?.publicId ?? '')
        setStudentCode(detail.studentCode)
        setCorporateUser(detail.corporateUser ?? '')
        setEmail(detail.email)
        setFirstName(detail.firstName)
        setLastName(detail.lastName)
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
          appliesAgile: detail.appliesAgile,
          appliesJira: detail.appliesJira
        })
        if (detail.organization?.appliesCertifications && mode === 'edit') {
          setCatalogLoading(true)
          try {
            const response = await getStudentCatalogs(administrator ? detail.organization.publicId : undefined)
            if (active) setCatalogs(response)
          } catch (requestError) {
            if (active) setCatalogError(requestError instanceof ApiRequestError
              ? requestError.message : 'No fue posible cargar los catálogos de la organización.')
          } finally { if (active) setCatalogLoading(false) }
        }
      })
      .catch((requestError) => {
        if (active) setError(requestError instanceof ApiRequestError
          ? requestError.message : 'No fue posible cargar al colaborador.')
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
      .then((response) => { setCatalogs(response); setOrganizationPublicId(response.organization.publicId) })
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
    ? Boolean(catalogs?.appliesCertifications) : Boolean(student?.organization?.appliesCertifications)
  const manualStudentCode = mode === 'create'
    ? Boolean(catalogs?.organization.manualStudentCode) : Boolean(student?.organization?.manualStudentCode)
  const profileOptions = includeCurrent(catalogs?.profiles ?? [], student?.professionalProfile)
  const technologicalProfileOptions = includeCurrent(catalogs?.technologicalProfiles ?? [], student?.technologicalProfile)

  function clearCertificationData() {
    setProfessionalProfilePublicId(''); setTechnologicalProfilePublicId(''); setFlags(EMPTY_FLAGS)
  }
  function requestFlagChange(key: keyof CertificationFlags, nextValue: boolean) {
    if (mode === 'edit' && !nextValue && flags[key]) {
      setPendingFlagRemoval(key)
      return
    }
    setFlags((current) => ({ ...current, [key]: nextValue }))
  }
  async function changeOrganization(nextPublicId: string) {
    const requestId = ++organizationRequest.current
    const hadData = hasCertificationData(professionalProfilePublicId, technologicalProfilePublicId, flags)
    setOrganizationPublicId(nextPublicId); setCatalogs(undefined); setCatalogError(undefined); setStudentCode('')
    setFieldErrors((current) => ({ ...current, organizationPublicId: '' }))
    if (!nextPublicId) { if (hadData) clearCertificationData(); return }
    setCatalogLoading(true)
    try {
      const response = await getStudentCatalogs(nextPublicId)
      if (requestId !== organizationRequest.current) return
      if (!response.appliesCertifications && hadData) {
        clearCertificationData()
        toast.warning('Datos condicionados retirados', 'La organización seleccionada no utiliza gestión de certificaciones. Los datos de perfil y seguimiento fueron retirados.')
      }
      setCatalogs(response)
    } catch (requestError) {
      if (requestId !== organizationRequest.current) return
      setCatalogError(requestError instanceof ApiRequestError
        ? requestError.message : 'No fue posible consultar la configuración de la organización.')
    } finally { if (requestId === organizationRequest.current) setCatalogLoading(false) }
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
      element?.focus(); element?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    }, 0)
  }
  function validateForm() {
    const errors: Record<string, string> = {}
    if (administrator && mode === 'create' && !organizationPublicId) errors.organizationPublicId = 'Debes seleccionar una organización.'
    if (!email.trim()) errors.email = 'El correo electrónico es obligatorio.'
    if (manualStudentCode && !studentCode.trim()) errors.studentCode = 'El Código a nivel organización es obligatorio.'
    if (corporateUser.trim() && !admissionDate && corporateUser.trim() !== (student?.corporateUser ?? '').trim()) errors.corporateUser = 'Captura una Fecha de alta para habilitar el Usuario corporativo.'
    if (!firstName.trim()) errors.firstName = 'El nombre es obligatorio.'
    if (!lastName.trim()) errors.lastName = 'Los apellidos son obligatorios.'
    if (!validFrom) errors.validFrom = 'El inicio de vigencia es obligatorio.'
    if (!expiresAt) errors.expiresAt = 'La fecha de vencimiento es obligatoria.'
    if (validFrom && expiresAt && expiresAt < validFrom) errors.expiresAt = 'La fecha de vencimiento no puede ser anterior al inicio de vigencia.'
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) focusFirstFieldError(errors)
    return Object.keys(errors).length === 0
  }
  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (readOnly || saving || !validateForm()) return
    if (mode === 'edit' && student?.admissionDate && !admissionDate) {
      setConfirmingDeactivation(true)
      return
    }
    void persistStudent()
  }

  async function persistStudent() {
    if (readOnly || saving) return
    setSaving(true); setError(null)
    const certificationPayload = appliesCertifications ? {
      ...(professionalProfilePublicId ? { professionalProfilePublicId } : {}),
      ...(technologicalProfilePublicId ? { technologicalProfilePublicId } : {}), ...flags
    } : {}
    try {
      if (mode === 'create') {
        const response = await createStudent({
          ...(administrator ? { organizationPublicId } : {}), email: email.trim(),
          firstName: firstName.trim(), lastName: lastName.trim(),
          displayName: `${firstName.trim()} ${lastName.trim()}`.trim(),
          status: admissionDate ? 'ACTIVE' : 'INACTIVE', validFrom, expiresAt,
          admissionDate: admissionDate || undefined,
          studentCode: manualStudentCode ? studentCode.trim() : undefined,
          corporateUser: admissionDate && corporateUser.trim() ? corporateUser.trim() : undefined,
          ...certificationPayload
        })
        setTemporaryCredentials({ ...response.temporaryCredentials, studentCode: response.student.studentCode })
        try {
          await updateStudentExperience(response.student.publicId, cleanExperience(experience))
        } catch {
          toast.warning('Colaborador creado con una advertencia', 'La cuenta se creó correctamente, pero la experiencia deberá guardarse desde Editar colaborador.')
          return
        }
        toast.success('Colaborador creado correctamente', 'Guarda las credenciales antes de cerrar esta vista.')
      } else if (student && publicId) {
        const admissionChanged = (student.admissionDate ?? '') !== admissionDate
        await updateStudent(publicId, {
          email: email.trim(), firstName: firstName.trim(), lastName: lastName.trim(),
          displayName: `${firstName.trim()} ${lastName.trim()}`.trim(), validFrom, expiresAt,
          admissionDate: admissionDate || undefined,
          studentCode: manualStudentCode ? studentCode.trim() : undefined,
          corporateUser: admissionDate ? (corporateUser.trim() || undefined) : undefined,
          ...certificationPayload, version: student.version
        })
        let experienceWarning = false
        try {
          await updateStudentExperience(publicId, cleanExperience(experience))
        } catch {
          experienceWarning = true
        }
        completeSave({ title: experienceWarning
          ? 'Colaborador actualizado con una advertencia.'
          : 'Colaborador actualizado correctamente.', message: experienceWarning
          ? 'Los datos principales se guardaron, pero la experiencia deberá actualizarse nuevamente.'
          : admissionChanged
            ? 'La fecha de alta cambió; se recalcularon únicamente las fechas límite pendientes.'
            : undefined })
      }
    } catch (requestError) {
      if (requestError instanceof ApiRequestError) {
        const errors = requestError.fieldErrors ?? {}; setError(requestError.message); setFieldErrors(errors); focusFirstFieldError(errors)
      } else setError('No fue posible guardar al colaborador.')
    } finally { setSaving(false) }
  }

  if (loading) return <LoadingScreen />
  if (error && mode !== 'create' && !student) return <main className="content-page"><BackButton fallback="/admin/collaborators" /><div className="error-message" role="alert">{error}</div></main>
  return (
    <main className="content-page editor-page student-editor-foundation">
      <BackButton fallback="/admin/collaborators" />
      <header className="page-heading compact ns-redundant-editor-heading"><div><p className="eyebrow">Colaboradores</p><h1>{mode === 'create' ? 'Nuevo colaborador' : mode === 'edit' ? 'Editar colaborador' : 'Ver colaborador'}</h1><p className="muted">La Fecha de alta determina si el colaborador está activo y puede gestionar certificaciones.</p></div></header>
      {error && <div className="error-message" role="alert">{error}</div>}
      <form className="student-foundation-form" onSubmit={handleSubmit} noValidate>
        {administrator && mode === 'create' && <section className="editor-card student-organization-first"><div className="section-heading"><div><p className="eyebrow">Organización</p><h2>Selecciona primero la organización</h2></div></div><div className="foundation-form-grid"><label className="form-field"><span>Organización</span><SelectField name="organizationPublicId" value={organizationPublicId} onChange={(nextValue) => void changeOrganization(nextValue)} disabled={organizationLoading} required ariaInvalid={Boolean(fieldErrors.organizationPublicId)} ariaLabel="Organización" options={[{ value: '', label: 'Seleccionar organización' }, ...organizations.map((item) => ({ value: item.publicId, label: `${item.name} · ${item.code}` }))]} />{field('organizationPublicId')}</label>{organizationLoading && <p className="form-help">Cargando organizaciones comerciales activas…</p>}{!organizationLoading && !organizationPublicId && <p className="form-help">Selecciona una organización para habilitar el resto del formulario.</p>}{organizationPublicId && catalogLoading && <p className="form-help">Consultando la configuración de {selectedOrganization?.name ?? 'la organización'}…</p>}{organizationError && <div className="error-message" role="alert">{organizationError}</div>}{catalogError && <div className="error-message" role="alert">{catalogError}</div>}</div></section>}
        {mode === 'create' && !administrator && catalogLoading && <section className="editor-card"><p className="muted">Resolviendo la organización y su configuración…</p></section>}
        {mode === 'create' && !administrator && catalogError && <div className="error-message" role="alert">{catalogError}</div>}
        {organizationResolved && <>
          <section className="editor-card"><div className="section-heading"><div><p className="eyebrow">Datos generales</p><h2>Identidad y acceso</h2></div></div><div className="foundation-form-grid">
            <label className="form-field ns-field-span-3"><span>Código a nivel organización</span>{manualStudentCode && !readOnly
              ? <input name="studentCode" value={studentCode} maxLength={80} onChange={(event) => setStudentCode(event.target.value.toUpperCase())} required aria-invalid={Boolean(fieldErrors.studentCode)} />
              : <strong className="readonly-value">{mode === 'create' ? 'Se generará automáticamente al guardar' : (studentCode || 'N/A')}</strong>}{field('studentCode')}</label>
            <label className="form-field ns-field-span-3"><span>Usuario corporativo <small>(opcional)</small></span>{readOnly
              ? <strong className="readonly-value">{corporateUser || 'N/A'}</strong>
              : <input name="corporateUser" value={corporateUser} maxLength={100} disabled={!admissionDate}
                  onChange={(event) => setCorporateUser(event.target.value)} aria-invalid={Boolean(fieldErrors.corporateUser)} />}{field('corporateUser')}{!readOnly && !admissionDate && <small>Captura una Fecha de alta para habilitar el campo Usuario corporativo.</small>}</label>
            <label className="form-field ns-field-span-6"><span>Correo</span>{readOnly ? <strong className="readonly-value">{email}</strong> : <input name="email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} required aria-invalid={Boolean(fieldErrors.email)} />}{field('email')}</label>
            <label className="form-field ns-field-span-6"><span>Nombre</span>{readOnly ? <strong className="readonly-value">{firstName}</strong> : <input name="firstName" value={firstName} onChange={(event) => setFirstName(event.target.value)} required aria-invalid={Boolean(fieldErrors.firstName)} />}{field('firstName')}</label>
            <label className="form-field ns-field-span-6"><span>Apellidos</span>{readOnly ? <strong className="readonly-value">{lastName}</strong> : <input name="lastName" value={lastName} onChange={(event) => setLastName(event.target.value)} required aria-invalid={Boolean(fieldErrors.lastName)} />}{field('lastName')}</label>
            <label className="form-field ns-field-span-4"><span>Inicio de vigencia</span>{readOnly ? <strong className="readonly-value">{formatDate(validFrom)}</strong> : <DateField name="validFrom" value={validFrom} onChange={setValidFrom} required ariaInvalid={Boolean(fieldErrors.validFrom)} ariaLabel="Seleccionar inicio de vigencia" />}{field('validFrom')}</label>
            <label className="form-field ns-field-span-4"><span>Vencimiento</span>{readOnly ? <strong className="readonly-value">{formatDate(expiresAt)}</strong> : <DateField name="expiresAt" value={expiresAt} min={validFrom || undefined} onChange={setExpiresAt} required ariaInvalid={Boolean(fieldErrors.expiresAt)} ariaLabel="Seleccionar vencimiento" />}{field('expiresAt')}</label>
            <label className="form-field ns-field-span-4"><span>Fecha de alta <small>(opcional)</small></span>{readOnly ? <strong className="readonly-value">{admissionDate ? formatDate(admissionDate) : 'N/A'}</strong> : <DateField name="admissionDate" value={admissionDate} onChange={setAdmissionDate} ariaInvalid={Boolean(fieldErrors.admissionDate)} ariaLabel="Seleccionar Fecha de alta" />}{field('admissionDate')}{!readOnly && !admissionDate && <small className="warning-text">Sin Fecha de alta, el colaborador permanecerá inactivo y no podrá gestionar certificaciones.</small>}</label>
            {readOnly && student?.organization && <label className="form-field ns-field-span-6"><span>Organización</span><strong className="readonly-value">{student.organization.name}</strong></label>}
            {readOnly && student && <label className="form-field ns-field-span-6"><span>Estado</span><strong className="readonly-value">{student.effectiveStatus === 'ACTIVE' ? 'Activo' : student.effectiveStatus === 'INACTIVE' ? 'Desactivado' : 'Vencido'}</strong></label>}
          </div></section>
          {appliesCertifications && <section className="editor-card"><div className="section-heading"><div><p className="eyebrow">Perfil profesional</p><h2>Clasificación profesional</h2></div></div>{catalogLoading && !readOnly && <p className="muted">Cargando catálogos de la organización…</p>}{catalogError && !readOnly && <div className="error-message" role="alert">{catalogError}</div>}{(!catalogLoading || readOnly) && <div className="foundation-form-grid foundation-form-grid--three">
            <label className="form-field ns-field-span-6"><span>Perfil</span>{readOnly ? <strong className="readonly-value">{student?.professionalProfile?.name ?? 'Sin información registrada'}</strong> : <SelectField name="professionalProfilePublicId" value={professionalProfilePublicId} onChange={setProfessionalProfilePublicId} disabled={!admissionDate || profileOptions.length === 0} ariaLabel="Perfil" options={[{ value: '', label: 'Seleccionar perfil' }, ...profileOptions.map((item) => ({ value: item.publicId, label: item.name }))]} />}{!readOnly && !admissionDate && <small>El colaborador está inactivo; la información de certificaciones es solo de consulta.</small>}{!readOnly && admissionDate && profileOptions.length === 0 && <small>Esta organización todavía no tiene perfiles activos configurados.</small>}</label>
            <label className="form-field ns-field-span-6"><span>Perfil tecnológico</span>{readOnly ? <strong className="readonly-value">{student?.technologicalProfile?.name ?? 'Sin información registrada'}</strong> : <SelectField name="technologicalProfilePublicId" value={technologicalProfilePublicId} onChange={setTechnologicalProfilePublicId} disabled={!admissionDate || technologicalProfileOptions.length === 0} ariaLabel="Perfil tecnológico" options={[{ value: '', label: 'Seleccionar perfil tecnológico' }, ...technologicalProfileOptions.map((item) => ({ value: item.publicId, label: item.name }))]} />}{!readOnly && admissionDate && technologicalProfileOptions.length === 0 && <small>Esta organización todavía no tiene perfiles tecnológicos activos.</small>}</label>

          </div>}</section>}
          {appliesCertifications && <section className="editor-card"><div className="section-heading"><div><p className="eyebrow">Certificaciones</p><h2>Seguimiento inicial</h2></div></div><p className="muted">{admissionDate ? 'Selecciona únicamente las áreas que aplican.' : 'El colaborador se encuentra inactivo porque no tiene Fecha de alta. No es posible gestionar sus certificaciones.'}</p><div className="student-certification-flags">{FLAG_OPTIONS.map((option) => readOnly ? <div className="student-certification-flag-readonly" key={option.key}><span>{option.label.replace('Aplica ', '')}</span><strong>{flags[option.key] ? 'Sí aplica' : 'No aplica'}</strong></div> : <div className="student-certification-flag" key={option.key}><input id={`student-${option.key}`} name={option.key} type="checkbox" checked={flags[option.key]} disabled={!admissionDate} onChange={(event) => requestFlagChange(option.key, event.target.checked)} /><label htmlFor={`student-${option.key}`}>{option.label}</label></div>)}</div></section>}
          <StudentExperienceFields value={experience} onChange={setExperience} readOnly={readOnly} disabled={saving} />
        </>}
        {!readOnly && organizationResolved && (
          <FormActions sticky>
            <button
              className="secondary-button"
              type="button"
              disabled={saving}
              onClick={() => navigate('/admin/collaborators')}
            >
              Cancelar
            </button>
            <button
              className="primary-button"
              type="submit"
              disabled={saving || catalogLoading || Boolean(catalogError)}
            >
              {saving ? 'Guardando…' : mode === 'create' ? 'Crear colaborador' : 'Guardar cambios'}
            </button>
          </FormActions>
        )}
      </form>
      {temporaryCredentials && <StudentTemporaryCredentialsDialog title="Colaborador creado correctamente" credentials={temporaryCredentials} onClose={() => { setTemporaryCredentials(undefined); navigate('/admin/collaborators', { replace: true }) }} />}
      <ConfirmDialog
        open={confirmingDeactivation}
        title="Confirmar baja del colaborador"
        description="Al eliminar la Fecha de alta, el colaborador será dado de baja, quedará inactivo, perderá el acceso a la plataforma y ya no será posible gestionar sus certificaciones."
        confirmLabel="Confirmar baja"
        tone="danger"
        busy={saving}
        onCancel={() => {
          setConfirmingDeactivation(false)
          setAdmissionDate(student?.admissionDate ?? '')
        }}
        onConfirm={() => {
          setConfirmingDeactivation(false)
          void persistStudent()
        }}
      />
      <ConfirmDialog
        open={Boolean(pendingFlagRemoval)}
        title="Confirmar cambio de seguimiento"
        description="Esta área dejará de estar disponible para nuevos seguimientos. El historial existente se conservará."
        confirmLabel="Confirmar cambio"
        onCancel={() => setPendingFlagRemoval(undefined)}
        onConfirm={() => {
          if (pendingFlagRemoval) {
            setFlags((current) => ({ ...current, [pendingFlagRemoval]: false }))
          }
          setPendingFlagRemoval(undefined)
        }}
      />
    </main>
  )
}
