import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useAuth } from '../features/authentication/context/AuthContext'
import { searchOrganizations } from '../features/organizations/api/organizationApi'
import { createAcademyTalent, downloadTalentCv, getTalent, getTalentCatalogs, getTalentCv, updateAcademyTalent, uploadTalentCv, viewTalentCv } from '../features/talent-bank/api/talentBankApi'
import { TalentCvUploadField } from '../features/talent-bank/components/TalentCvUploadField'
import type { OrganizationSummary } from '../features/organizations/types/organizations'
import { ApiRequestError } from '../shared/api/apiClient'
import { BackButton } from '../shared/components/BackButton'
import { DateField } from '../shared/components/DateField'
import { FormActions } from '../shared/components/FormActions'
import { LoadingScreen } from '../shared/components/LoadingScreen'
import { SelectField } from '../shared/components/SelectField'
import { useToast } from '../shared/components/ToastProvider'
import { useSaveNavigation } from '../shared/hooks/useSaveNavigation'
import type { TalentCatalogs, TalentCvMetadata, TalentProfileCode, TalentSummary } from '../shared/types/talentBank'
import { formatPersonName } from '../shared/utils/personNames'

interface Props { mode: 'create' | 'edit' }
function today() { return new Date().toISOString().slice(0, 10) }

export function AcademyTalentEditorPage({ mode }: Props) {
  const { publicId } = useParams()
  const { user } = useAuth()
  const administrator = Boolean(user?.roles.includes('ADMINISTRATOR'))
  const navigate = useNavigate()
  const toast = useToast()
  const completeSave = useSaveNavigation('/admin/talent-bank')
  const [talent, setTalent] = useState<TalentSummary>()
  const [organizations, setOrganizations] = useState<OrganizationSummary[]>([])
  const [catalogs, setCatalogs] = useState<TalentCatalogs>()
  const [organizationPublicId, setOrganizationPublicId] = useState('')
  const [studentCode, setStudentCode] = useState('')
  const [email, setEmail] = useState('')
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const originalName = useRef({ firstName: '', lastName: '' })
  const nameEdited = useRef({ firstName: false, lastName: false })
  const [validFrom, setValidFrom] = useState(today())
  const [expiresAt, setExpiresAt] = useState('2999-12-31')
  const [organizationHiredOn, setOrganizationHiredOn] = useState('')
  const [profileCode, setProfileCode] = useState<TalentProfileCode>('JR')
  const [technologyPublicId, setTechnologyPublicId] = useState('')
  const [cvFile, setCvFile] = useState<File>()
  const [currentCv, setCurrentCv] = useState<TalentCvMetadata | null>(null)
  const [loading, setLoading] = useState(mode === 'edit')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string>()
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  useEffect(() => {
    if (!administrator || mode !== 'create') return
    const controller = new AbortController()
    searchOrganizations({ status: 'ACTIVE', page: 0, size: 100, signal: controller.signal })
      .then((result) => setOrganizations(result.content.filter((item) => item.organizationType === 'CUSTOMER')))
      .catch(() => { if (!controller.signal.aborted) setOrganizations([]) })
    return () => controller.abort()
  }, [administrator, mode])

  useEffect(() => {
    if (mode !== 'create' || administrator) return
    const controller = new AbortController()
    getTalentCatalogs(undefined, controller.signal).then((result) => {
      setCatalogs(result); setOrganizationPublicId(result.organization.publicId)
    }).catch((requestError) => {
      if (!controller.signal.aborted) setError(requestError instanceof ApiRequestError ? requestError.message : 'No fue posible resolver la organización.')
    })
    return () => controller.abort()
  }, [administrator, mode])

  useEffect(() => {
    if (mode !== 'edit' || !publicId) return
    let active = true
    Promise.all([getTalent(publicId), getTalentCv(publicId)]).then(async ([detail, cvMetadata]) => {
      if (detail.talentType !== 'ACADEMY') throw new Error('Este registro no corresponde a Academia.')
      const catalogResult = await getTalentCatalogs(administrator ? detail.organization.publicId : undefined)
      if (!active) return
      setTalent(detail); setCurrentCv(cvMetadata); setCatalogs(catalogResult); setOrganizationPublicId(detail.organization.publicId)
      setStudentCode(detail.studentCode); setEmail(detail.email)
      originalName.current = { firstName: detail.firstName, lastName: detail.lastName }
      nameEdited.current = { firstName: false, lastName: false }
      setFirstName(formatPersonName(detail.firstName)); setLastName(formatPersonName(detail.lastName))
      setValidFrom(detail.validFrom); setExpiresAt(detail.expiresAt); setOrganizationHiredOn(detail.organizationHiredOn ?? '')
      setProfileCode((detail.profileCode ?? 'JR') as TalentProfileCode); setTechnologyPublicId(detail.technology?.publicId ?? '')
    }).catch((requestError) => { if (active) setError(requestError instanceof ApiRequestError ? requestError.message : String(requestError)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [administrator, mode, publicId])

  async function selectOrganization(value: string) {
    setOrganizationPublicId(value); setCatalogs(undefined); setTechnologyPublicId('')
    if (!value) return
    try { setCatalogs(await getTalentCatalogs(value)) }
    catch (requestError) { setError(requestError instanceof ApiRequestError ? requestError.message : 'No fue posible cargar los catálogos.') }
  }

  async function viewCurrentCv() {
    if (!publicId) return
    try { await viewTalentCv(publicId) }
    catch (requestError) { toast.error('No fue posible ver el CV.', requestError instanceof Error ? requestError.message : undefined) }
  }

  async function downloadCurrentCv() {
    if (!publicId) return
    try { await downloadTalentCv(publicId) }
    catch (requestError) { toast.error('No fue posible descargar el CV.', requestError instanceof Error ? requestError.message : undefined) }
  }

  const manualCode = Boolean(catalogs?.organization.manualStudentCode)
  const organizationResolved = Boolean(catalogs?.organization)
  const selectedOrganization = useMemo(() => organizations.find((item) => item.publicId === organizationPublicId), [organizations, organizationPublicId])

  function validate() {
    const errors: Record<string, string> = {}
    if (administrator && mode === 'create' && !organizationPublicId) errors.organizationPublicId = 'Selecciona una organización.'
    if (manualCode && !studentCode.trim()) errors.studentCode = 'El código es obligatorio.'
    if (!email.trim()) errors.email = 'El correo es obligatorio.'
    if (!firstName.trim()) errors.firstName = 'El nombre es obligatorio.'
    if (!lastName.trim()) errors.lastName = 'Los apellidos son obligatorios.'
    if (!organizationHiredOn) errors.organizationHiredOn = 'La fecha de contratación es obligatoria.'
    if (!technologyPublicId) errors.technologyPublicId = 'Selecciona una tecnología.'
    setFieldErrors(errors); return Object.keys(errors).length === 0
  }

  async function submit(event: FormEvent) {
    event.preventDefault(); if (saving || !validate()) return
    setSaving(true); setError(undefined)
    const savedFirstName = (mode === 'edit' && !nameEdited.current.firstName
      ? originalName.current.firstName
      : firstName).trim()
    const savedLastName = (mode === 'edit' && !nameEdited.current.lastName
      ? originalName.current.lastName
      : lastName).trim()
    const payload = {
      ...(administrator && mode === 'create' ? { organizationPublicId } : {}),
      studentCode: manualCode ? studentCode.trim() : undefined,
      email: email.trim(), firstName: savedFirstName, lastName: savedLastName,
      displayName: `${savedFirstName} ${savedLastName}`.trim(), validFrom, expiresAt,
      organizationHiredOn, profileCode, technologyPublicId
    }
    try {
      if (mode === 'create') {
        const result = await createAcademyTalent(payload)
        let cvWarning = false
        if (cvFile) {
          try { await uploadTalentCv(result.talent.publicId, cvFile) }
          catch { cvWarning = true }
        }
        toast[cvWarning ? 'warning' : 'success'](
          cvWarning ? 'Talento registrado con una advertencia.' : 'El talento de Academia se registró correctamente.',
          cvWarning ? 'El talento quedó guardado, pero el CV deberá cargarse nuevamente desde su detalle.' : 'El registro quedó disponible en Talent Bank.'
        )
        navigate('/admin/talent-bank', { replace: true })
      } else if (publicId && talent) {
        await updateAcademyTalent(publicId, { ...payload, version: talent.version })
        let cvWarning = false
        let cvUpdated = false
        if (cvFile) {
          try {
            const metadata = await uploadTalentCv(publicId, cvFile)
            setCurrentCv(metadata)
            setCvFile(undefined)
            cvUpdated = true
          } catch { cvWarning = true }
        }
        completeSave({
          title: cvWarning
            ? 'Talento actualizado con una advertencia.'
            : cvUpdated
              ? 'El CV se actualizó correctamente.'
              : 'Talento actualizado correctamente.',
          message: cvWarning ? 'Los datos se guardaron, pero el CV deberá cargarse nuevamente.' : undefined
        })
      }
    } catch (requestError) {
      if (requestError instanceof ApiRequestError) {
        const errors = { ...(requestError.fieldErrors ?? {}) }
        if (requestError.code === 'STUDENT_EMAIL_EXISTS') errors.email = 'Correo ya utilizado.'
        setFieldErrors(errors)
        toast.error(requestError.code === 'STUDENT_EMAIL_EXISTS'
          ? `El correo ${email.trim()} ya está utilizado por otro colaborador de la organización.`
          : requestError.message)
      } else {
        toast.error('No fue posible guardar el talento.')
      }
    } finally { setSaving(false) }
  }

  if (loading) return <LoadingScreen />
  return <main className="content-page editor-page academy-talent-editor">
    <BackButton fallback="/admin/talent-bank" />
    {error && <div className="error-message" role="alert">{error}</div>}
    <form className="student-foundation-form" onSubmit={submit} noValidate>
      {administrator && mode === 'create' && <section className="editor-card"><div className="section-heading"><div><p className="eyebrow">Organización</p><h2>Selecciona primero la organización</h2></div></div><label className="form-field"><span>Organización</span><SelectField name="organizationPublicId" value={organizationPublicId} onChange={(value) => void selectOrganization(value)} options={[{ value: '', label: 'Seleccionar organización' }, ...organizations.map((item) => ({ value: item.publicId, label: `${item.name} · ${item.code}` }))]} />{fieldErrors.organizationPublicId && <small className="field-error">{fieldErrors.organizationPublicId}</small>}{organizationPublicId && <small>{selectedOrganization?.name}</small>}</label></section>}
      {organizationResolved && <>
        <section className="editor-card"><div className="section-heading"><div><p className="eyebrow">Academia</p><h2>Información del talento</h2></div></div><div className="foundation-form-grid">
          {(mode !== 'create' || manualCode) && <label className="form-field ns-field-span-4"><span>Código a nivel organización</span>{manualCode ? <input name="studentCode" value={studentCode} onChange={(event) => setStudentCode(event.target.value.toUpperCase())} /> : <strong className="readonly-value">{studentCode || 'N/A'}</strong>}{fieldErrors.studentCode && <small className="field-error">{fieldErrors.studentCode}</small>}</label>}
          <label className="form-field ns-field-span-8"><span>Correo</span><input name="email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} aria-invalid={Boolean(fieldErrors.email)} />{fieldErrors.email && <small className="field-error">{fieldErrors.email}</small>}</label>
          <label className="form-field ns-field-span-6"><span>Nombre</span><input name="firstName" value={firstName} onChange={(event) => { nameEdited.current.firstName = true; setFirstName(event.target.value) }} aria-invalid={Boolean(fieldErrors.firstName)} />{fieldErrors.firstName && <small className="field-error">{fieldErrors.firstName}</small>}</label>
          <label className="form-field ns-field-span-6"><span>Apellidos</span><input name="lastName" value={lastName} onChange={(event) => { nameEdited.current.lastName = true; setLastName(event.target.value) }} aria-invalid={Boolean(fieldErrors.lastName)} />{fieldErrors.lastName && <small className="field-error">{fieldErrors.lastName}</small>}</label>
          <label className="form-field ns-field-span-4"><span>Fecha de contratación en la organización</span><DateField name="organizationHiredOn" value={organizationHiredOn} onChange={setOrganizationHiredOn} />{fieldErrors.organizationHiredOn && <small className="field-error">{fieldErrors.organizationHiredOn}</small>}</label>
          <label className="form-field ns-field-span-4"><span>Perfil</span><SelectField name="profileCode" value={profileCode} onChange={(value) => setProfileCode(value as TalentProfileCode)} options={(catalogs?.profiles ?? []).map((value) => ({ value, label: value }))} /></label>
          <label className="form-field ns-field-span-8"><span>Tecnología</span><SelectField name="technologyPublicId" value={technologyPublicId} onChange={setTechnologyPublicId} options={[{ value: '', label: 'Seleccionar tecnología' }, ...(catalogs?.technologies ?? []).map((item) => ({ value: item.publicId, label: item.name }))]} />{fieldErrors.technologyPublicId && <small className="field-error">{fieldErrors.technologyPublicId}</small>}</label>
        </div></section>
        <section className="editor-card"><div className="section-heading"><div><p className="eyebrow">Documentación</p><h2>Currículum vitae</h2></div></div><TalentCvUploadField file={cvFile} currentCv={currentCv} disabled={saving} onChange={setCvFile} onViewCurrent={viewCurrentCv} onDownloadCurrent={downloadCurrentCv} /></section>
        <FormActions sticky><button type="button" className="secondary-button" disabled={saving} onClick={() => navigate('/admin/talent-bank')}>Cancelar</button><button className="primary-button" disabled={saving} type="submit">{saving ? 'Guardando…' : mode === 'create' ? 'Registrar talento' : 'Guardar cambios'}</button></FormActions>
      </>}
    </form>
  </main>
}
