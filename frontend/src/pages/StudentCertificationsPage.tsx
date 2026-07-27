import { type FormEvent, useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  getCertificationCatalogs,
  getStudentCertifications,
  saveStudentCertifications
} from '../features/certifications/api/certificationApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { Icon } from '../shared/components/Icon'
import { useSaveNavigation } from '../shared/hooks/useSaveNavigation'
import type {
  CertificationAttemptPayload,
  CertificationCatalogs,
  CertificationExamStatus,
  CertificationRequirementPayload,
  CertificationStatus,
  CertificationType,
  StudentCertificationDetail,
  TechnologicalProfile
} from '../shared/types/certifications'

const TYPE_LABELS: Record<CertificationType, string> = {
  DEVELOPMENT_SECURITY: 'Desarrollo Seguro',
  TECHNOLOGICAL: 'Certificación tecnológica',
  ONE: 'ONE',
  NORMATIVE_TESTING: 'Normativa y Testing',
  AGILE: 'Agile'
}

interface ProfileModel {
  professionalProfilePublicId: string
  certificationTechnologyPublicId: string
  enrollmentDate: string
  technologicalProfile: TechnologicalProfile | ''
  profileVersion: number | null
}

interface AttemptDraft extends CertificationAttemptPayload {
  enabled: boolean
}

function blankProfile(): ProfileModel {
  return {
    professionalProfilePublicId: '',
    certificationTechnologyPublicId: '',
    enrollmentDate: '',
    technologicalProfile: '',
    profileVersion: null
  }
}

function requirementFromDetail(detail: StudentCertificationDetail): CertificationRequirementPayload[] {
  return detail.requirements.map((item) => ({
    type: item.type,
    applies: item.applies,
    certificationStatus: item.certificationStatus,
    examStatus: item.examStatus,
    manualDeadline: item.manualDeadline,
    deadlineOverrideReason: item.deadlineOverrideReason,
    applicationDate: item.applicationDate,
    score: item.score,
    currentAttempt: item.currentAttempt,
    actionsToTake: item.actionsToTake,
    observations: item.observations,
    version: item.version
  }))
}

function blankAttempt(type: CertificationType): AttemptDraft {
  return {
    enabled: false,
    type,
    attemptNumber: 1,
    scheduledDate: null,
    applicationDate: null,
    examStatus: 'NOT_SCHEDULED',
    score: null,
    result: null,
    observations: null
  }
}

function formatDate(value: string | null | undefined) {
  if (!value) return 'Sin fecha'
  return new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium' }).format(new Date(`${value}T12:00:00`))
}

export function StudentCertificationsPage() {
  const { publicId } = useParams()
  const navigate = useNavigate()
  const completeSave = useSaveNavigation('/admin/students')
  const [catalogs, setCatalogs] = useState<CertificationCatalogs | null>(null)
  const [detail, setDetail] = useState<StudentCertificationDetail | null>(null)
  const [profile, setProfile] = useState<ProfileModel>(() => blankProfile())
  const [requirements, setRequirements] = useState<CertificationRequirementPayload[]>([])
  const [attempts, setAttempts] = useState<Record<CertificationType, AttemptDraft>>(() => ({
    DEVELOPMENT_SECURITY: blankAttempt('DEVELOPMENT_SECURITY'),
    TECHNOLOGICAL: blankAttempt('TECHNOLOGICAL'),
    ONE: blankAttempt('ONE'),
    NORMATIVE_TESTING: blankAttempt('NORMATIVE_TESTING'),
    AGILE: blankAttempt('AGILE')
  }))
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  useEffect(() => {
    if (!publicId) return
    let active = true
    setLoading(true)
    Promise.all([getCertificationCatalogs(), getStudentCertifications(publicId)])
      .then(([catalogResponse, detailResponse]) => {
        if (!active) return
        setCatalogs(catalogResponse)
        setDetail(detailResponse)
        setRequirements(requirementFromDetail(detailResponse))
        if (detailResponse.profile) {
          setProfile({
            professionalProfilePublicId: detailResponse.profile.professionalProfilePublicId,
            certificationTechnologyPublicId: detailResponse.profile.certificationTechnologyPublicId,
            enrollmentDate: detailResponse.profile.enrollmentDate,
            technologicalProfile: detailResponse.profile.technologicalProfile,
            profileVersion: detailResponse.profile.version
          })
        }
        const nextAttempts = { ...attempts }
        detailResponse.requirements.forEach((requirement) => {
          const highest = detailResponse.attempts
            .filter((attempt) => attempt.type === requirement.type)
            .reduce((value, attempt) => Math.max(value, attempt.attemptNumber), 0)
          nextAttempts[requirement.type] = {
            ...blankAttempt(requirement.type),
            attemptNumber: highest + 1
          }
        })
        setAttempts(nextAttempts)
      })
      .catch((requestError: unknown) => {
        if (active) setError(requestError instanceof ApiRequestError
          ? requestError.message
          : 'No fue posible cargar la gestión de certificaciones.')
      })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [publicId])

  const nextDeadline = useMemo(() => {
    const values = detail?.requirements
      .filter((item) => item.applies && item.effectiveDeadline)
      .map((item) => item.effectiveDeadline!)
      .sort()
    return values?.[0] ?? null
  }, [detail])

  const completed = detail?.requirements.filter((item) => item.certificationStatus === 'CERTIFIED').length ?? 0
  const pending = detail?.requirements.filter((item) => item.applies && item.certificationStatus !== 'CERTIFIED').length ?? 0

  function updateRequirement(type: CertificationType, changes: Partial<CertificationRequirementPayload>) {
    setRequirements((current) => current.map((item) => item.type === type ? { ...item, ...changes } : item))
  }

  function updateAttempt(type: CertificationType, changes: Partial<AttemptDraft>) {
    setAttempts((current) => ({ ...current, [type]: { ...current[type], ...changes } }))
  }

  function validate() {
    const next: Record<string, string> = {}
    if (!profile.professionalProfilePublicId) next.professionalProfilePublicId = 'Selecciona un perfil.'
    if (!profile.certificationTechnologyPublicId) next.certificationTechnologyPublicId = 'Selecciona una tecnología.'
    if (!profile.enrollmentDate) next.enrollmentDate = 'La fecha de alta es obligatoria.'
    if (!profile.technologicalProfile) next.technologicalProfile = 'Selecciona un perfil tecnológico.'
    requirements.forEach((item) => {
      if (item.score !== null && (item.score < 0 || item.score > 100)) {
        next[`${item.type}.score`] = 'El promedio debe estar entre 0 y 100.'
      }
      if (item.currentAttempt !== null && item.currentAttempt < 1) {
        next[`${item.type}.currentAttempt`] = 'El intento debe iniciar en 1.'
      }
      if (item.manualDeadline && !item.deadlineOverrideReason?.trim()) {
        next[`${item.type}.deadlineOverrideReason`] = 'Indica el motivo del ajuste manual.'
      }
    })
    Object.values(attempts).filter((item) => item.enabled).forEach((item) => {
      if (item.attemptNumber < 1) next[`${item.type}.newAttempt`] = 'El intento debe iniciar en 1.'
      if (item.score !== null && (item.score < 0 || item.score > 100)) {
        next[`${item.type}.newAttemptScore`] = 'El promedio debe estar entre 0 y 100.'
      }
    })
    setFieldErrors(next)
    return Object.keys(next).length === 0
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!publicId || saving || !validate()) {
      if (!saving) setError('Revisa los campos marcados antes de guardar.')
      return
    }
    setSaving(true)
    setError('')
    try {
      await saveStudentCertifications(publicId, {
        professionalProfilePublicId: profile.professionalProfilePublicId,
        certificationTechnologyPublicId: profile.certificationTechnologyPublicId,
        enrollmentDate: profile.enrollmentDate,
        technologicalProfile: profile.technologicalProfile as TechnologicalProfile,
        profileVersion: profile.profileVersion,
        requirements,
        newAttempts: Object.values(attempts)
          .filter((item) => item.enabled)
          .map(({ enabled: _enabled, ...item }) => item)
      })
      completeSave({
        title: 'Certificaciones actualizadas correctamente.',
        message: 'El seguimiento y los intentos quedaron guardados.'
      })
    } catch (requestError: unknown) {
      if (requestError instanceof ApiRequestError) {
        setError(requestError.message)
        setFieldErrors(requestError.fieldErrors ?? {})
      } else {
        setError('No fue posible guardar las certificaciones.')
      }
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return <main className="content-page"><section className="ns-loading-card">Cargando certificaciones…</section></main>
  }

  return (
    <main className="content-page certification-page">
      <button className="ns-back-button" type="button" onClick={() => navigate('/admin/students')}>
        ← Volver a estudiantes
      </button>

      <header className="ns-page-header">
        <div>
          <p className="eyebrow">Estudiantes</p>
          <h1>Administrar certificaciones</h1>
          <p className="muted">Seguimiento independiente del perfil, requisitos e intentos de certificación.</p>
        </div>
      </header>

      {error && (
        <section className="inline-error-panel" role="alert">
          <div className="inline-error-icon"><Icon name="error" size={20} /></div>
          <div><strong>No fue posible completar la operación</strong><p>{error}</p></div>
        </section>
      )}

      {detail && (
        <section className="certification-summary">
          <div><small>Estudiante</small><strong>{detail.studentName}</strong></div>
          <div><small>Organización</small><strong>{detail.organizationName}</strong></div>
          <div><small>Perfil</small><strong>{detail.profile?.professionalProfileName ?? 'Pendiente de configurar'}</strong></div>
          <div><small>Tecnología</small><strong>{detail.profile?.certificationTechnologyName ?? 'Sin definir'}</strong></div>
          <div><small>Fecha de alta</small><strong>{formatDate(detail.profile?.enrollmentDate)}</strong></div>
          <div><small>Próxima fecha límite</small><strong>{formatDate(nextDeadline)}</strong></div>
          <div><small>Pendientes</small><strong>{pending}</strong></div>
          <div><small>Completadas</small><strong>{completed}</strong></div>
        </section>
      )}

      <form className="certification-form" onSubmit={submit} noValidate>
        <fieldset disabled={saving}>
          <section className="ns-card">
            <div className="ns-card-heading">
              <div><span className="ns-step">1</span><h2>Perfil de certificación</h2></div>
              <p>Información base utilizada para los cálculos de fechas.</p>
            </div>
            <div className="certification-grid">
              <label className="ns-field">
                <span>Perfil <b>*</b></span>
                <select value={profile.professionalProfilePublicId}
                  onChange={(event) => setProfile((current) => ({ ...current, professionalProfilePublicId: event.target.value }))}>
                  <option value="">Seleccionar perfil</option>
                  {catalogs?.profiles.map((item) => <option key={item.publicId} value={item.publicId}>{item.name}</option>)}
                </select>
                {fieldErrors.professionalProfilePublicId && <small className="org-field-error">{fieldErrors.professionalProfilePublicId}</small>}
              </label>
              <label className="ns-field">
                <span>Fecha de alta <b>*</b></span>
                <input type="date" value={profile.enrollmentDate}
                  onChange={(event) => setProfile((current) => ({ ...current, enrollmentDate: event.target.value }))} />
                {fieldErrors.enrollmentDate && <small className="org-field-error">{fieldErrors.enrollmentDate}</small>}
              </label>
              <label className="ns-field">
                <span>Tecnología en la que se certifica <b>*</b></span>
                <select value={profile.certificationTechnologyPublicId}
                  onChange={(event) => setProfile((current) => ({ ...current, certificationTechnologyPublicId: event.target.value }))}>
                  <option value="">Seleccionar tecnología</option>
                  {catalogs?.technologies.map((item) => <option key={item.publicId} value={item.publicId}>{item.name}</option>)}
                </select>
                {fieldErrors.certificationTechnologyPublicId && <small className="org-field-error">{fieldErrors.certificationTechnologyPublicId}</small>}
              </label>
              <label className="ns-field">
                <span>Perfil tecnológico <b>*</b></span>
                <select value={profile.technologicalProfile}
                  onChange={(event) => setProfile((current) => ({ ...current, technologicalProfile: event.target.value as TechnologicalProfile }))}>
                  <option value="">Seleccionar perfil tecnológico</option>
                  {catalogs?.technologicalProfiles.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
                </select>
                {fieldErrors.technologicalProfile && <small className="org-field-error">{fieldErrors.technologicalProfile}</small>}
              </label>
            </div>
          </section>

          <section className="certification-sections">
            {requirements.map((item, index) => {
              const attempt = attempts[item.type]
              const showExamFields = item.type === 'DEVELOPMENT_SECURITY'
                || item.type === 'TECHNOLOGICAL'
                || item.type === 'NORMATIVE_TESTING'
              return (
                <details className="ns-card certification-section" key={item.type} open={index === 0}>
                  <summary>
                    <div>
                      <strong>{TYPE_LABELS[item.type]}</strong>
                      <span>{item.applies ? 'Aplica' : 'No aplica'}</span>
                    </div>
                    <Icon name="chevronDown" size={18} />
                  </summary>
                  <div className="certification-section-body">
                    <label className="certification-toggle">
                      <input type="checkbox" checked={item.applies}
                        onChange={(event) => updateRequirement(item.type, {
                          applies: event.target.checked,
                          certificationStatus: event.target.checked ? 'PENDING' : 'NOT_APPLICABLE',
                          examStatus: event.target.checked ? item.examStatus : 'NOT_SCHEDULED'
                        })} />
                      ¿Aplica {TYPE_LABELS[item.type]}?
                    </label>

                    {item.applies && (
                      <>
                        <div className="certification-grid">
                          <label className="ns-field">
                            <span>Estatus de certificación</span>
                            <select value={item.certificationStatus}
                              onChange={(event) => updateRequirement(item.type, { certificationStatus: event.target.value as CertificationStatus })}>
                              {catalogs?.certificationStatuses.filter((option) => option.value !== 'NOT_APPLICABLE')
                                .map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
                            </select>
                          </label>
                          {showExamFields && (
                            <label className="ns-field">
                              <span>Estatus del examen</span>
                              <select value={item.examStatus}
                                onChange={(event) => updateRequirement(item.type, { examStatus: event.target.value as CertificationExamStatus })}>
                                {catalogs?.examStatuses.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
                              </select>
                            </label>
                          )}
                          <label className="ns-field">
                            <span>Fecha límite calculada</span>
                            <input disabled type="date" value={detail?.requirements.find((value) => value.type === item.type)?.calculatedDeadline ?? ''} />
                          </label>
                          <label className="ns-field">
                            <span>Fecha límite ajustada</span>
                            <input type="date" value={item.manualDeadline ?? ''}
                              onChange={(event) => updateRequirement(item.type, { manualDeadline: event.target.value || null })} />
                          </label>
                          {item.manualDeadline && (
                            <label className="ns-field certification-wide">
                              <span>Motivo del ajuste <b>*</b></span>
                              <input maxLength={500} value={item.deadlineOverrideReason ?? ''}
                                onChange={(event) => updateRequirement(item.type, { deadlineOverrideReason: event.target.value })} />
                              {fieldErrors[`${item.type}.deadlineOverrideReason`] && <small className="org-field-error">{fieldErrors[`${item.type}.deadlineOverrideReason`]}</small>}
                            </label>
                          )}
                          {showExamFields && (
                            <>
                              <label className="ns-field">
                                <span>Fecha de aplicación</span>
                                <input type="date" value={item.applicationDate ?? ''}
                                  onChange={(event) => updateRequirement(item.type, { applicationDate: event.target.value || null })} />
                              </label>
                              <label className="ns-field">
                                <span>Promedio</span>
                                <input min="0" max="100" step="0.01" type="number" value={item.score ?? ''}
                                  onChange={(event) => updateRequirement(item.type, { score: event.target.value === '' ? null : Number(event.target.value) })} />
                                {fieldErrors[`${item.type}.score`] && <small className="org-field-error">{fieldErrors[`${item.type}.score`]}</small>}
                              </label>
                              <label className="ns-field">
                                <span>Intento actual</span>
                                <input min="1" step="1" type="number" value={item.currentAttempt ?? ''}
                                  onChange={(event) => updateRequirement(item.type, { currentAttempt: event.target.value === '' ? null : Number(event.target.value) })} />
                              </label>
                            </>
                          )}
                          {item.type === 'NORMATIVE_TESTING' && (
                            <label className="ns-field certification-wide">
                              <span>Acciones a realizar Normativa</span>
                              <textarea maxLength={1000} value={item.actionsToTake ?? ''}
                                onChange={(event) => updateRequirement(item.type, { actionsToTake: event.target.value })} />
                            </label>
                          )}
                          <label className="ns-field certification-wide">
                            <span>Observaciones</span>
                            <textarea maxLength={1000} value={item.observations ?? ''}
                              onChange={(event) => updateRequirement(item.type, { observations: event.target.value })} />
                          </label>
                        </div>

                        {showExamFields && (
                          <div className="attempt-draft">
                            <label className="certification-toggle">
                              <input type="checkbox" checked={attempt.enabled}
                                onChange={(event) => updateAttempt(item.type, { enabled: event.target.checked })} />
                              Registrar un nuevo intento al guardar
                            </label>
                            {attempt.enabled && (
                              <div className="certification-grid">
                                <label className="ns-field"><span>Número de intento</span><input min="1" type="number" value={attempt.attemptNumber} onChange={(event) => updateAttempt(item.type, { attemptNumber: Number(event.target.value) })} /></label>
                                <label className="ns-field"><span>Fecha programada</span><input type="date" value={attempt.scheduledDate ?? ''} onChange={(event) => updateAttempt(item.type, { scheduledDate: event.target.value || null })} /></label>
                                <label className="ns-field"><span>Fecha de aplicación</span><input type="date" value={attempt.applicationDate ?? ''} onChange={(event) => updateAttempt(item.type, { applicationDate: event.target.value || null })} /></label>
                                <label className="ns-field"><span>Estado del examen</span><select value={attempt.examStatus} onChange={(event) => updateAttempt(item.type, { examStatus: event.target.value as CertificationExamStatus })}>{catalogs?.examStatuses.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>
                                <label className="ns-field"><span>Promedio</span><input min="0" max="100" step="0.01" type="number" value={attempt.score ?? ''} onChange={(event) => updateAttempt(item.type, { score: event.target.value === '' ? null : Number(event.target.value) })} /></label>
                                <label className="ns-field certification-wide"><span>Observaciones</span><input maxLength={1000} value={attempt.observations ?? ''} onChange={(event) => updateAttempt(item.type, { observations: event.target.value })} /></label>
                              </div>
                            )}
                          </div>
                        )}
                      </>
                    )}
                  </div>
                </details>
              )
            })}
          </section>

          <section className="ns-card">
            <div className="ns-card-heading"><div><h2>Historial de intentos</h2></div></div>
            <div className="ns-data-table-wrap">
              <table className="ns-data-table">
                <thead><tr><th>Certificación</th><th>Intento</th><th>Aplicación</th><th>Estado</th><th>Promedio</th></tr></thead>
                <tbody>
                  {detail?.attempts.length === 0 && <tr><td colSpan={5} className="ns-table-empty">Todavía no existen intentos registrados.</td></tr>}
                  {detail?.attempts.map((attempt) => (
                    <tr key={attempt.publicId}>
                      <td>{TYPE_LABELS[attempt.type]}</td>
                      <td>{attempt.attemptNumber}</td>
                      <td>{formatDate(attempt.applicationDate)}</td>
                      <td>{attempt.examStatus}</td>
                      <td>{attempt.score ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>

          <section className="ns-card">
            <div className="ns-card-heading"><div><h2>Historial de cambios</h2></div></div>
            <div className="certification-history">
              {detail?.history.length === 0 && <p className="muted">Todavía no existen cambios registrados.</p>}
              {detail?.history.map((entry) => (
                <article key={entry.publicId}>
                  <strong>{entry.eventType}</strong>
                  <span>{new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(entry.changedAt))}</span>
                </article>
              ))}
            </div>
          </section>
        </fieldset>

        <footer className="certification-actions">
          <button className="secondary-button" type="button" disabled={saving} onClick={() => navigate('/admin/students')}>Cancelar</button>
          <button className="primary-button" type="submit" disabled={saving}>
            {saving ? 'Guardando…' : 'Guardar cambios'}
          </button>
        </footer>
      </form>
    </main>
  )
}
