import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  getCertificationCatalogs,
  getStudentCertificationAttempts,
  getStudentCertificationHistory,
  getStudentCertifications,
  saveStudentCertifications
} from '../features/certifications/api/certificationApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { BackButton } from '../shared/components/BackButton'
import { DateField } from '../shared/components/DateField'
import { LoadingScreen } from '../shared/components/LoadingScreen'
import { TablePagination } from '../shared/components/TablePagination'
import { useToast } from '../shared/components/ToastProvider'
import type {
  CertificationApplicability,
  CertificationAttemptPayload,
  CertificationAttemptView,
  CertificationCatalogs,
  CertificationCyclePayload,
  CertificationCycleView,
  CertificationExamStatus,
  CertificationHistoryView,
  CertificationLevel,
  CertificationProcessType,
  CertificationTrackingStatus,
  CertificationType,
  CertificationValidityStatus,
  PagedResponse,
  StudentCertificationDetail
} from '../shared/types/certifications'
import type { PageSize } from '../shared/types/pagination'

type TabId = 'SUMMARY' | CertificationType | 'ATTEMPTS' | 'EXPIRATIONS' | 'HISTORY'

type EditableCycle = CertificationCyclePayload & {
  key: string
  processType?: CertificationProcessType
  deadlineDate?: string | null
  expirationDate?: string | null
  lastApprovedApplicationDate?: string | null
  validityStatus?: CertificationValidityStatus
  technologyName?: string | null
  previousApprovedCyclePublicId?: string | null
  attemptCount?: number
  latestScore?: number | null
  latestExamStatus?: CertificationExamStatus
  importedFailureCount?: number | null
  resultSource?: 'IMPORT' | 'MANUAL' | null
}

const TYPE_LABELS: Record<CertificationType, string> = {
  TECHNOLOGICAL: 'Tecnológica',
  DEVELOPMENT_SECURITY: 'Desarrollo Seguro',
  NORMATIVE_TESTING: 'Normativa y Testing',
  ONE: 'ONE',
  AGILE: 'Agile',
  JIRA: 'Jira'
}

const STUDENT_STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Activo',
  INACTIVE: 'Desactivado',
  EXPIRED: 'Vencido',
  DELETED: 'Eliminado'
}

const VALIDITY_LABELS: Record<CertificationValidityStatus, string> = {
  NOT_OBTAINED: 'Aún no obtenida',
  VALID: 'Vigente',
  EXPIRING_SOON: 'Próxima a vencer',
  EXPIRED: 'Vencida'
}

const EXAM_LABELS: Record<CertificationExamStatus, string> = {
  NOT_SCHEDULED: 'Sin programar',
  SCHEDULED: 'Programado',
  RESCHEDULED: 'Reprogramado',
  COMPLETED: 'Completado',
  PASSED: 'Aprobado',
  FAILED: 'No aprobado',
  ABSENT: 'Ausente',
  CANCELLED: 'Cancelado'
}

const FLAG_OPTIONS: Array<{ key: keyof CertificationApplicability; type: CertificationType; label: string }> = [
  { key: 'technological', type: 'TECHNOLOGICAL', label: 'Tecnológica' },
  { key: 'developmentSecurity', type: 'DEVELOPMENT_SECURITY', label: 'Desarrollo Seguro' },
  { key: 'normativeTesting', type: 'NORMATIVE_TESTING', label: 'Normativa y Testing' },
  { key: 'one', type: 'ONE', label: 'ONE' },
  { key: 'agile', type: 'AGILE', label: 'Agile' },
  { key: 'jira', type: 'JIRA', label: 'Jira' }
]

const TABS: Array<{ id: TabId; label: string }> = [
  { id: 'SUMMARY', label: 'Resumen' },
  { id: 'TECHNOLOGICAL', label: 'Tecnológica' },
  { id: 'DEVELOPMENT_SECURITY', label: 'Desarrollo Seguro' },
  { id: 'NORMATIVE_TESTING', label: 'Normativa y Testing' },
  { id: 'ONE', label: 'ONE' },
  { id: 'AGILE', label: 'Agile' },
  { id: 'JIRA', label: 'Jira' },
  { id: 'ATTEMPTS', label: 'Intentos' },
  { id: 'EXPIRATIONS', label: 'Vencimientos' },
  { id: 'HISTORY', label: 'Historial' }
]

function formatDate(value?: string | null) {
  if (!value) return 'Sin fecha'
  return new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium' }).format(new Date(`${value}T12:00:00`))
}

function formatDateTime(value?: string | null) {
  if (!value) return 'Sin fecha'
  return new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

function cycleFromView(cycle: CertificationCycleView): EditableCycle {
  return {
    key: cycle.publicId,
    publicId: cycle.publicId,
    type: cycle.type,
    technologyPublicId: cycle.technologyPublicId,
    technologyName: cycle.technologyName,
    certificationLevel: cycle.certificationLevel,
    primary: cycle.primary,
    processType: cycle.processType,
    trackingStatus: cycle.trackingStatus,
    deadlineDate: cycle.deadlineDate,
    scheduledDate: cycle.scheduledDate,
    applicationDate: cycle.applicationDate,
    lastApprovedApplicationDate: cycle.lastApprovedApplicationDate,
    approved: cycle.approved,
    expirationDate: cycle.expirationDate,
    validityStatus: cycle.validityStatus,
    previousApprovedCyclePublicId: cycle.previousApprovedCyclePublicId,
    actionsToTake: cycle.actionsToTake,
    softtekManagement: cycle.softtekManagement,
    observations: cycle.observations,
    active: cycle.active,
    latestScore: cycle.latestScore,
    latestExamStatus: cycle.latestExamStatus,
    attemptCount: cycle.attemptCount,
    importedFailureCount: cycle.importedFailureCount,
    resultSource: cycle.resultSource,
    version: cycle.version
  }
}

function newCycle(type: CertificationType, primary = false): EditableCycle {
  return {
    key: `new-${type}-${crypto.randomUUID()}`,
    type,
    technologyPublicId: null,
    certificationLevel: type === 'TECHNOLOGICAL' ? 'JR' : null,
    primary: type === 'TECHNOLOGICAL' && primary,
    trackingStatus: 'PENDING',
    scheduledDate: null,
    applicationDate: null,
    approved: null,
    actionsToTake: null,
    softtekManagement: null,
    observations: null,
    active: true,
    version: null,
    processType: undefined,
    deadlineDate: null,
    expirationDate: null,
    lastApprovedApplicationDate: null,
    validityStatus: 'NOT_OBTAINED',
    attemptCount: 0,
    latestScore: null,
    latestExamStatus: 'NOT_SCHEDULED',
    importedFailureCount: null,
    resultSource: null
  }
}

function blankAttempt(): CertificationAttemptPayload {
  return {
    scheduledDate: null,
    applicationDate: null,
    examStatus: 'NOT_SCHEDULED',
    score: null,
    approved: null,
    result: null,
    observations: null,
    version: null
  }
}

function supportsAttempts(type: CertificationType) {
  return type === 'TECHNOLOGICAL' || type === 'DEVELOPMENT_SECURITY' || type === 'NORMATIVE_TESTING'
}

function hasExpiration(type: CertificationType) {
  return type === 'TECHNOLOGICAL' || type === 'DEVELOPMENT_SECURITY' || type === 'NORMATIVE_TESTING'
}

export function StudentCertificationsPage() {
  const { publicId = '' } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const [catalogs, setCatalogs] = useState<CertificationCatalogs>()
  const [detail, setDetail] = useState<StudentCertificationDetail>()
  const [applicability, setApplicability] = useState<CertificationApplicability>({
    technological: false,
    developmentSecurity: false,
    normativeTesting: false,
    one: false,
    agile: false,
    jira: false
  })
  const [cycles, setCycles] = useState<EditableCycle[]>([])
  const [attemptDrafts, setAttemptDrafts] = useState<Record<string, CertificationAttemptPayload | undefined>>({})
  const [activeTab, setActiveTab] = useState<TabId>('SUMMARY')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [dirty, setDirty] = useState(false)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const [selectedAttemptCycle, setSelectedAttemptCycle] = useState('')
  const [attempts, setAttempts] = useState<PagedResponse<CertificationAttemptView>>({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 })
  const [attemptsPage, setAttemptsPage] = useState(0)
  const [attemptsSize, setAttemptsSize] = useState<PageSize>(10)
  const [attemptsLoading, setAttemptsLoading] = useState(false)

  const [history, setHistory] = useState<PagedResponse<CertificationHistoryView>>({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 })
  const [historyPage, setHistoryPage] = useState(0)
  const [historySize, setHistorySize] = useState<PageSize>(10)
  const [historyLoading, setHistoryLoading] = useState(false)

  async function loadWorkspace() {
    const [catalogResponse, detailResponse] = await Promise.all([
      getCertificationCatalogs(publicId),
      getStudentCertifications(publicId)
    ])
    setCatalogs(catalogResponse)
    setDetail(detailResponse)
    setApplicability(detailResponse.applicability)
    setCycles(detailResponse.cycles.map(cycleFromView))
    setAttemptDrafts({})
    setDirty(false)
    const firstAttemptCycle = detailResponse.cycles.find((cycle) => supportsAttempts(cycle.type))?.publicId ?? ''
    setSelectedAttemptCycle((current) => current || firstAttemptCycle)
    return detailResponse
  }

  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    Promise.all([getCertificationCatalogs(publicId), getStudentCertifications(publicId)])
      .then(([catalogResponse, detailResponse]) => {
        if (!active) return
        setCatalogs(catalogResponse)
        setDetail(detailResponse)
        setApplicability(detailResponse.applicability)
        setCycles(detailResponse.cycles.map(cycleFromView))
        setSelectedAttemptCycle(detailResponse.cycles.find((cycle) => supportsAttempts(cycle.type))?.publicId ?? '')
      })
      .catch((requestError) => {
        if (active) setError(requestError instanceof ApiRequestError
          ? requestError.message
          : 'No fue posible cargar la administración de certificaciones.')
      })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [publicId])

  useEffect(() => {
    if (!selectedAttemptCycle || activeTab !== 'ATTEMPTS') return
    let active = true
    setAttemptsLoading(true)
    getStudentCertificationAttempts(publicId, selectedAttemptCycle, attemptsPage, attemptsSize)
      .then((response) => { if (active) setAttempts({ ...response, content: response.content ?? [] }) })
      .catch((requestError) => {
        if (active) setError(requestError instanceof ApiRequestError
          ? requestError.message
          : 'No fue posible cargar los intentos.')
      })
      .finally(() => { if (active) setAttemptsLoading(false) })
    return () => { active = false }
  }, [activeTab, attemptsPage, attemptsSize, publicId, selectedAttemptCycle])

  useEffect(() => {
    if (activeTab !== 'HISTORY') return
    let active = true
    setHistoryLoading(true)
    getStudentCertificationHistory(publicId, historyPage, historySize)
      .then((response) => { if (active) setHistory({ ...response, content: response.content ?? [] }) })
      .catch((requestError) => {
        if (active) setError(requestError instanceof ApiRequestError
          ? requestError.message
          : 'No fue posible cargar el historial de certificaciones.')
      })
      .finally(() => { if (active) setHistoryLoading(false) })
    return () => { active = false }
  }, [activeTab, historyPage, historySize, publicId])

  const applicableTypes = useMemo(() => FLAG_OPTIONS
    .filter((item) => applicability[item.key])
    .map((item) => item.type), [applicability])

  const hasAttemptSection = applicableTypes.some(supportsAttempts)
  const hasExpirationSection = applicableTypes.some(hasExpiration)

  const visibleTabs = useMemo(() => TABS.filter((tab) => {
    if (tab.id === 'ATTEMPTS') return hasAttemptSection
    if (tab.id === 'EXPIRATIONS') return hasExpirationSection
    if (!Object.prototype.hasOwnProperty.call(TYPE_LABELS, tab.id)) return true
    return applicableTypes.includes(tab.id as CertificationType)
  }), [applicableTypes, hasAttemptSection, hasExpirationSection])

  useEffect(() => {
    if (!visibleTabs.some((tab) => tab.id === activeTab)) setActiveTab('SUMMARY')
  }, [activeTab, visibleTabs])

  const expirationCycles = useMemo(() => cycles.filter((cycle) => cycle.active !== false
    && applicableTypes.includes(cycle.type)
    && hasExpiration(cycle.type)
    && (cycle.expirationDate || cycle.validityStatus === 'EXPIRED' || cycle.validityStatus === 'EXPIRING_SOON')),
  [applicableTypes, cycles])

  const attemptCycles = useMemo(() => cycles.filter((cycle) => cycle.active !== false
    && applicableTypes.includes(cycle.type)
    && Boolean(cycle.publicId)
    && supportsAttempts(cycle.type)), [applicableTypes, cycles])


  function updateCycle(key: string, changes: Partial<EditableCycle>) {
    setCycles((current) => current.map((cycle) => cycle.key === key ? { ...cycle, ...changes } : cycle))
    setDirty(true)
    setFieldErrors((current) => {
      const next = { ...current }
      Object.keys(changes).forEach((name) => delete next[`${key}.${name}`])
      return next
    })
  }

  function addCycle(type: CertificationType, primary = false) {
    const created = newCycle(type, primary)
    if (type === 'TECHNOLOGICAL' && primary) {
      setCycles((current) => [...current.map((cycle) => cycle.type === 'TECHNOLOGICAL' ? { ...cycle, primary: false } : cycle), created])
    } else {
      setCycles((current) => [...current, created])
    }
    setDirty(true)
    setActiveTab(type)
  }

  function removeUnsavedCycle(key: string) {
    setCycles((current) => current.filter((cycle) => cycle.key !== key))
    setAttemptDrafts((current) => {
      const next = { ...current }
      delete next[key]
      return next
    })
    setDirty(true)
  }

  function setPrimary(key: string) {
    setCycles((current) => current.map((cycle) => cycle.type === 'TECHNOLOGICAL'
      ? { ...cycle, primary: cycle.key === key }
      : cycle))
    setDirty(true)
  }

  function toggleAttemptDraft(cycle: EditableCycle, enabled: boolean) {
    setAttemptDrafts((current) => ({ ...current, [cycle.key]: enabled ? (current[cycle.key] ?? blankAttempt()) : undefined }))
    setDirty(true)
  }

  function updateAttemptDraft(key: string, changes: Partial<CertificationAttemptPayload>) {
    setAttemptDrafts((current) => ({ ...current, [key]: { ...(current[key] ?? blankAttempt()), ...changes } }))
    setDirty(true)
  }

  function editAttempt(attempt: CertificationAttemptView) {
    const cycle = cycles.find((item) => item.publicId === attempt.cyclePublicId)
    if (!cycle) return
    setAttemptDrafts((current) => ({
      ...current,
      [cycle.key]: {
        publicId: attempt.publicId,
        scheduledDate: attempt.scheduledDate,
        applicationDate: attempt.applicationDate,
        examStatus: attempt.examStatus,
        score: attempt.score,
        approved: attempt.approved,
        result: attempt.result,
        observations: attempt.observations,
        version: attempt.version
      }
    }))
    setActiveTab(cycle.type)
    setDirty(true)
  }

  function validate() {
    const next: Record<string, string> = {}
    cycles.filter((cycle) => {
      const flag = FLAG_OPTIONS.find((item) => item.type === cycle.type)
      return flag ? applicability[flag.key] : false
    }).forEach((cycle) => {
      if (cycle.type === 'TECHNOLOGICAL') {
        if (!cycle.technologyPublicId) next[`${cycle.key}.technologyPublicId`] = 'Selecciona una tecnología.'
        if (!cycle.certificationLevel) next[`${cycle.key}.certificationLevel`] = 'Selecciona JR, STD o SR.'
      }
      if (hasExpiration(cycle.type) && cycle.approved === true && !cycle.applicationDate) {
        next[`${cycle.key}.applicationDate`] = 'La fecha de aplicación es obligatoria para aprobar.'
      }
      const attempt = attemptDrafts[cycle.key]
      if (attempt) {
        if (attempt.score !== null && attempt.score !== undefined && (attempt.score < 0 || attempt.score > 100)) {
          next[`${cycle.key}.attempt.score`] = 'El promedio debe estar entre 0 y 100.'
        }
        if (attempt.approved === true && !attempt.applicationDate) {
          next[`${cycle.key}.attempt.applicationDate`] = 'La fecha de aplicación es obligatoria para aprobar el intento.'
        }
      }
    })
    setFieldErrors(next)
    const errorKeys = Object.keys(next)
    if (errorKeys.length > 0) {
      const firstKey = errorKeys[0]
      if (firstKey) {
        const cycleKey = firstKey.split('.')[0]
        const cycle = cycles.find((item) => item.key === cycleKey)
        if (cycle) setActiveTab(cycle.type)
      }
      setError('Revisa los campos marcados antes de guardar.')
    }
    return Object.keys(next).length === 0
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (saving || !validate()) return
    setSaving(true)
    setError('')
    setFieldErrors({})
    const applicableCycles = cycles.filter((cycle) => {
      const flag = FLAG_OPTIONS.find((item) => item.type === cycle.type)
      return flag ? applicability[flag.key] : false
    })
    try {
      await saveStudentCertifications(publicId, {
        cycles: applicableCycles.map((cycle) => ({
          ...(cycle.publicId ? { publicId: cycle.publicId } : {}),
          type: cycle.type,
          technologyPublicId: cycle.type === 'TECHNOLOGICAL' ? cycle.technologyPublicId : null,
          certificationLevel: cycle.type === 'TECHNOLOGICAL' ? cycle.certificationLevel : null,
          primary: cycle.type === 'TECHNOLOGICAL' && cycle.primary,
          trackingStatus: cycle.trackingStatus,
          scheduledDate: cycle.scheduledDate || null,
          applicationDate: cycle.applicationDate || null,
          approved: cycle.approved,
          actionsToTake: cycle.actionsToTake?.trim() || null,
          softtekManagement: cycle.softtekManagement?.trim() || null,
          observations: cycle.observations?.trim() || null,
          active: cycle.active !== false,
          version: cycle.version,
          attempts: attemptDrafts[cycle.key] ? [attemptDrafts[cycle.key]!] : []
        }))
      })
      await loadWorkspace()
      if (activeTab === 'ATTEMPTS' && selectedAttemptCycle) {
        const response = await getStudentCertificationAttempts(publicId, selectedAttemptCycle, attemptsPage, attemptsSize)
        setAttempts({ ...response, content: response.content ?? [] })
      }
      toast.success('Certificaciones actualizadas', 'Los cambios se guardaron de forma transaccional.')
    } catch (requestError) {
      if (requestError instanceof ApiRequestError) {
        setError(requestError.message)
        setFieldErrors(requestError.fieldErrors ?? {})
      } else {
        setError('No fue posible guardar la administración de certificaciones.')
      }
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <LoadingScreen />
  if (!detail || !catalogs) {
    return <main className="content-page"><BackButton fallback="/admin/students" />
      <div className="error-message" role="alert">{error || 'No fue posible consultar las certificaciones.'}</div></main>
  }

  const inactiveBecauseNoAdmissionDate = !detail.student.admissionDate
  const inactiveStudent = inactiveBecauseNoAdmissionDate || detail.student.status !== 'ACTIVE'

  return (
    <main className="content-page certification-page certification-workspace">
      <BackButton fallback="/admin/students" />
      <header className="ns-page-header">
        <div>
          <p className="eyebrow">Colaboradores · Certificaciones</p>
          <h1>Administrar certificaciones</h1>
          <p className="muted">Los datos generales son de solo lectura. El tipo Certificación o Recertificación, las fechas límite y los vencimientos se calculan en backend.</p>
        </div>
      </header>

      {error && <div className="error-message" role="alert">{error}</div>}
      {inactiveStudent && <div className="warning-message" role="status">{inactiveBecauseNoAdmissionDate
        ? 'El colaborador se encuentra inactivo porque no tiene Fecha de alta. No es posible gestionar sus certificaciones. La información existente permanece disponible para consulta.'
        : 'El colaborador no se encuentra activo. No es posible gestionar sus certificaciones. La información existente permanece disponible para consulta.'}</div>}

      <section className="ns-card certification-student-summary">
        <div><span>Colaborador</span><strong>{detail.student.displayName}</strong></div>
        <div><span>Organización</span><strong>{detail.student.organizationName}</strong></div>
        <div><span>Estado</span><strong>{STUDENT_STATUS_LABELS[detail.student.status] ?? detail.student.status}</strong></div>
        <div><span>Inicio de vigencia</span><strong>{formatDate(detail.student.validFrom)}</strong></div>
        <div><span>Vencimiento de acceso</span><strong>{formatDate(detail.student.expiresAt)}</strong></div>
        <div><span>Fecha de alta</span><strong>{detail.student.admissionDate ? formatDate(detail.student.admissionDate) : 'N/A'}</strong></div>
        <div><span>Perfil</span><strong>{detail.student.professionalProfile?.name ?? 'Sin perfil'}</strong></div>
        <div><span>Perfil tecnológico</span><strong>{detail.student.technologicalProfile?.name ?? 'Sin perfil tecnológico'}</strong></div>
      </section>

      <nav className="certification-tabs" aria-label="Secciones de certificaciones">
        {visibleTabs.map((tab) => (
          <button key={tab.id} type="button" className={activeTab === tab.id ? 'is-active' : ''}
            aria-current={activeTab === tab.id ? 'page' : undefined} onClick={() => setActiveTab(tab.id)}>
            {tab.label}
          </button>
        ))}
      </nav>

      <form className="certification-form" onSubmit={submit} noValidate>
        <fieldset disabled={saving || inactiveStudent}>
          {activeTab === 'SUMMARY' && (
            <section className="certification-panel">
              <div className="certification-metrics">
                <Metric label="Áreas aplicables" value={detail.metrics.applicableAreas} />
                <Metric label="Pendientes" value={detail.metrics.pending} />
                <Metric label="Programadas" value={detail.metrics.scheduled} />
                <Metric label="Aprobadas" value={detail.metrics.approved} />
                <Metric label="No aprobadas" value={detail.metrics.notApproved} />
                <Metric label="Vigentes" value={detail.metrics.valid} />
                <Metric label="Próximas a vencer" value={detail.metrics.expiringSoon} />
                <Metric label="Vencidas" value={detail.metrics.expired} />
                <Metric label="Recertificaciones pendientes" value={detail.metrics.pendingRecertifications} />
              </div>
              <div className="ns-card certification-summary-note">
                <h2>Seguimientos sin vencimiento</h2>
                <p>ONE, Agile y Jira conservan aplicabilidad, estatus, observaciones e historial; no generan intentos, fechas límite ni vencimientos.</p>
              </div>
            </section>
          )}


          {FLAG_OPTIONS.map((option) => activeTab === option.type && applicability[option.key] ? (
            <CertificationSection key={option.type} type={option.type} cycles={cycles.filter((cycle) => cycle.type === option.type)}
              catalogs={catalogs} attemptDrafts={attemptDrafts} fieldErrors={fieldErrors}
              onAdd={addCycle} onUpdate={updateCycle} onRemove={removeUnsavedCycle}
              onSetPrimary={setPrimary} onToggleAttempt={toggleAttemptDraft} onUpdateAttempt={updateAttemptDraft} />
          ) : null)}

          {activeTab === 'ATTEMPTS' && (
            <section className="ns-card certification-panel">
              <div className="ns-card-heading"><div><h2>Intentos</h2><p className="muted">Los intentos se consultan con paginación de servidor. Para editar uno, cárgalo en la sección de su certificación y guarda todos los cambios juntos.</p></div></div>
              {attemptCycles.length === 0 ? <p className="muted">Todavía no existen ciclos que admitan intentos.</p> : (
                <>
                  <label className="ns-field certification-attempt-cycle-select"><span>Ciclo</span>
                    <select value={selectedAttemptCycle} onChange={(event) => { setSelectedAttemptCycle(event.target.value); setAttemptsPage(0) }}>
                      {attemptCycles.map((cycle) => <option key={cycle.publicId} value={cycle.publicId}>{TYPE_LABELS[cycle.type]} · {cycle.technologyName ?? cycle.processType ?? 'Ciclo'}</option>)}
                    </select>
                  </label>
                  <div className="ns-data-table-wrap"><table className="ns-data-table"><thead><tr><th>Intento</th><th>Programada</th><th>Aplicación</th><th>Examen</th><th>Promedio</th><th>Resultado</th><th>Acciones</th></tr></thead><tbody>
                    {attemptsLoading && <tr><td colSpan={7} className="ns-table-empty">Cargando intentos…</td></tr>}
                    {!attemptsLoading && attempts.content.length === 0 && <tr><td colSpan={7} className="ns-table-empty">Todavía no existen intentos registrados.</td></tr>}
                    {!attemptsLoading && attempts.content.map((attempt) => <tr key={attempt.publicId}>
                      <td>{attempt.attemptNumber}</td><td>{formatDate(attempt.scheduledDate)}</td><td>{formatDate(attempt.applicationDate)}</td>
                      <td>{EXAM_LABELS[attempt.examStatus]}</td><td>{attempt.score ?? '—'}</td><td>{attempt.result ?? '—'}</td>
                      <td><button className="secondary-button" type="button" onClick={() => editAttempt(attempt)}>Editar</button></td>
                    </tr>)}
                  </tbody></table></div>
                  <TablePagination compact currentPage={attempts.page} pageSize={attempts.size}
                    totalElements={attempts.totalElements} totalPages={attempts.totalPages} isLoading={attemptsLoading}
                    onPageChange={setAttemptsPage}
                    onPageSizeChange={(size) => { setAttemptsSize(size); setAttemptsPage(0) }} />
                </>
              )}
            </section>
          )}

          {activeTab === 'EXPIRATIONS' && (
            <section className="ns-card certification-panel">
              <div className="ns-card-heading"><div><h2>Vencimientos</h2><p className="muted">Solo incluye Tecnológica, Desarrollo Seguro y Normativa y Testing.</p></div></div>
              <div className="ns-data-table-wrap"><table className="ns-data-table"><thead><tr><th>Área</th><th>Tecnología / nivel</th><th>Última aprobación</th><th>Vencimiento</th><th>Vigencia</th></tr></thead><tbody>
                {expirationCycles.length === 0 && <tr><td colSpan={5} className="ns-table-empty">No existen certificaciones aprobadas con vencimiento.</td></tr>}
                {expirationCycles.map((cycle) => <tr key={cycle.key}><td>{TYPE_LABELS[cycle.type]}</td>
                  <td>{cycle.type === 'TECHNOLOGICAL' ? `${cycle.technologyName ?? 'Tecnología'} · ${cycle.certificationLevel ?? 'Sin nivel'}` : '—'}</td>
                  <td>{formatDate(cycle.lastApprovedApplicationDate)}</td><td>{formatDate(cycle.expirationDate)}</td>
                  <td>{cycle.validityStatus ? VALIDITY_LABELS[cycle.validityStatus] : 'Aún no obtenida'}</td></tr>)}
              </tbody></table></div>
            </section>
          )}

          {activeTab === 'HISTORY' && (
            <section className="ns-card certification-panel">
              <div className="ns-card-heading"><div><h2>Historial de certificaciones</h2></div></div>
              <div className="ns-data-table-wrap"><table className="ns-data-table"><thead><tr><th>Evento</th><th>Motivo</th><th>Fecha</th></tr></thead><tbody>
                {historyLoading && <tr><td colSpan={3} className="ns-table-empty">Cargando historial…</td></tr>}
                {!historyLoading && history.content.length === 0 && <tr><td colSpan={3} className="ns-table-empty">Todavía no existen cambios registrados.</td></tr>}
                {!historyLoading && history.content.map((item) => <tr key={item.publicId}><td>{item.eventType}</td><td>{item.reason ?? '—'}</td><td>{formatDateTime(item.changedAt)}</td></tr>)}
              </tbody></table></div>
              <TablePagination compact currentPage={history.page} pageSize={history.size}
                totalElements={history.totalElements} totalPages={history.totalPages} isLoading={historyLoading}
                onPageChange={setHistoryPage}
                onPageSizeChange={(size) => { setHistorySize(size); setHistoryPage(0) }} />
            </section>
          )}
        </fieldset>

        <footer className="certification-actions">
          <button className="secondary-button" type="button" disabled={saving} onClick={() => navigate('/admin/students')}>Cancelar</button>
          {!inactiveStudent && <button className="primary-button" type="submit" disabled={saving || !dirty}>
            {saving ? 'Guardando…' : 'Guardar cambios'}
          </button>}
        </footer>
      </form>
    </main>
  )
}

function Metric({ label, value }: { label: string; value: number }) {
  return <div><span>{label}</span><strong>{value}</strong></div>
}

function CertificationSection({
  type,
  cycles,
  catalogs,
  attemptDrafts,
  fieldErrors,
  onAdd,
  onUpdate,
  onRemove,
  onSetPrimary,
  onToggleAttempt,
  onUpdateAttempt
}: {
  type: CertificationType
  cycles: EditableCycle[]
  catalogs: CertificationCatalogs
  attemptDrafts: Record<string, CertificationAttemptPayload | undefined>
  fieldErrors: Record<string, string>
  onAdd: (type: CertificationType, primary?: boolean) => void
  onUpdate: (key: string, changes: Partial<EditableCycle>) => void
  onRemove: (key: string) => void
  onSetPrimary: (key: string) => void
  onToggleAttempt: (cycle: EditableCycle, enabled: boolean) => void
  onUpdateAttempt: (key: string, changes: Partial<CertificationAttemptPayload>) => void
}) {
  const technological = type === 'TECHNOLOGICAL'
  const primaryExists = cycles.some((cycle) => cycle.primary && cycle.active !== false)
  return (
    <section className="certification-panel certification-cycle-section">
      <div className="ns-card certification-section-heading">
        <div><p className="eyebrow">Seguimiento</p><h2>{TYPE_LABELS[type]}</h2></div>
        <div className="student-management-actions">
          {technological && !primaryExists && <button className="primary-button" type="button" onClick={() => onAdd(type, true)}>Configurar principal</button>}
          <button className="secondary-button" type="button" onClick={() => onAdd(type, false)}>{technological ? 'Agregar certificación secundaria' : 'Agregar ciclo'}</button>
        </div>
      </div>
      {cycles.length === 0 && (
        <div className="ns-card certification-empty-cycle">
          <p>{technological
            ? 'La certificación tecnológica está habilitada, pero todavía no se ha configurado una certificación principal.'
            : 'El seguimiento está habilitado, pero todavía no existe un ciclo configurado.'}</p>
        </div>
      )}
      <div className="certification-cycle-list">
        {cycles.map((cycle, index) => {
          const attempt = attemptDrafts[cycle.key]
          return (
            <article className="ns-card certification-cycle-card" key={cycle.key}>
              <header>
                <div>
                  <p className="eyebrow">{technological ? cycle.primary ? 'Certificación principal' : `Certificación secundaria ${index + 1}` : `Ciclo ${index + 1}`}</p>
                  <h3>{cycle.processType === 'RECERTIFICATION' ? 'Recertificación' : cycle.processType === 'CERTIFICATION' ? 'Certificación' : 'Tipo calculado al guardar'}</h3>
                  <small>El tipo se determina automáticamente a partir del historial aprobado equivalente.</small>
                </div>
                <div className="certification-cycle-header-actions">
                  {technological && !cycle.primary && <button className="secondary-button" type="button" onClick={() => onSetPrimary(cycle.key)}>Hacer principal</button>}
                  {!cycle.publicId && <button className="danger-button" type="button" onClick={() => onRemove(cycle.key)}>Retirar</button>}
                </div>
              </header>

              <div className="certification-grid">
                {technological && (
                  <>
                    <label className="ns-field"><span>Tecnología <b>*</b></span>
                      <select value={cycle.technologyPublicId ?? ''}
                        onChange={(event) => onUpdate(cycle.key, { technologyPublicId: event.target.value || null })}
                        aria-invalid={Boolean(fieldErrors[`${cycle.key}.technologyPublicId`])}>
                        <option value="">Seleccionar tecnología</option>
                        {catalogs.technologies.map((item) => <option key={item.publicId} value={item.publicId}>{item.name}</option>)}
                      </select>
                      {fieldErrors[`${cycle.key}.technologyPublicId`] && <small className="field-error">{fieldErrors[`${cycle.key}.technologyPublicId`]}</small>}
                    </label>
                    <label className="ns-field"><span>Nivel de certificación <b>*</b></span>
                      <select value={cycle.certificationLevel ?? ''}
                        onChange={(event) => onUpdate(cycle.key, { certificationLevel: event.target.value as CertificationLevel })}
                        aria-invalid={Boolean(fieldErrors[`${cycle.key}.certificationLevel`])}>
                        {catalogs.levels.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
                      </select>
                      {fieldErrors[`${cycle.key}.certificationLevel`] && <small className="field-error">{fieldErrors[`${cycle.key}.certificationLevel`]}</small>}
                    </label>
                  </>
                )}
                <label className="ns-field"><span>Tipo de proceso</span><input disabled value={cycle.processType === 'RECERTIFICATION' ? 'Recertificación' : cycle.processType === 'CERTIFICATION' ? 'Certificación' : 'Se calculará al guardar'} /></label>
                <label className="ns-field"><span>Estado de seguimiento</span>
                  <select value={cycle.trackingStatus}
                    onChange={(event) => onUpdate(cycle.key, { trackingStatus: event.target.value as CertificationTrackingStatus })}>
                    {catalogs.trackingStatuses.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
                  </select>
                </label>
                {hasExpiration(type) && (
                  <>
                    <label className="ns-field"><span>{cycle.processType === 'RECERTIFICATION' ? 'Fecha límite inicial (histórica)' : 'Fecha límite inicial'}</span><DateField value={cycle.deadlineDate ?? ''} onChange={() => undefined} disabled ariaLabel="Fecha límite inicial" /></label>
                    <label className="ns-field"><span>Fecha programada</span><DateField value={cycle.scheduledDate ?? ''} onChange={(value) => onUpdate(cycle.key, { scheduledDate: value || null })} ariaLabel="Seleccionar fecha programada" /></label>
                    <label className="ns-field"><span>Última fecha de presentación</span><DateField value={cycle.applicationDate ?? ''}
                      onChange={(value) => onUpdate(cycle.key, { applicationDate: value || null })}
                      ariaInvalid={Boolean(fieldErrors[`${cycle.key}.applicationDate`])} ariaLabel="Seleccionar última fecha de presentación" />
                      {fieldErrors[`${cycle.key}.applicationDate`] && <small className="field-error">{fieldErrors[`${cycle.key}.applicationDate`]}</small>}
                    </label>
                    <label className="ns-field"><span>Última aplicación aprobada</span>
                      <DateField value={cycle.lastApprovedApplicationDate ?? ''} onChange={() => undefined} disabled ariaLabel="Última aplicación aprobada" />
                    </label>
                  </>
                )}
                {type !== 'ONE' && type !== 'AGILE' && type !== 'JIRA' && (
                  <label className="ns-field"><span>Aprobación</span>
                    <select value={cycle.approved === null || cycle.approved === undefined ? '' : cycle.approved ? 'true' : 'false'}
                      onChange={(event) => onUpdate(cycle.key, { approved: event.target.value === '' ? null : event.target.value === 'true' })}>
                      <option value="">Pendiente</option><option value="true">Aprobada</option><option value="false">No aprobada</option>
                    </select>
                  </label>
                )}
                {hasExpiration(type) && (
                  <>
                    <label className="ns-field"><span>Fecha de vencimiento</span><DateField value={cycle.expirationDate ?? ''} onChange={() => undefined} disabled ariaLabel="Fecha de vencimiento" /></label>
                    <label className="ns-field"><span>Estado de vigencia</span><input disabled value={cycle.validityStatus ? VALIDITY_LABELS[cycle.validityStatus] : 'Aún no obtenida'} /></label>
                    <label className="ns-field"><span>Último resultado de examen</span>
                      <input disabled value={cycle.latestExamStatus ? EXAM_LABELS[cycle.latestExamStatus] : 'Sin información'} />
                    </label>
                    <label className="ns-field"><span>Promedio más reciente</span>
                      <input disabled value={cycle.latestScore ?? 'Sin información'} />
                    </label>
                    <label className="ns-field"><span>Reprobaciones administrativas del Excel</span>
                      <input disabled value={cycle.importedFailureCount ?? 'Sin información'} />
                    </label>
                    <label className="ns-field"><span>Origen del resultado</span>
                      <input disabled value={cycle.resultSource === 'IMPORT' ? 'Importación Excel' : cycle.resultSource === 'MANUAL' ? 'Captura manual' : 'Sin información'} />
                    </label>
                  </>
                )}
                {type === 'DEVELOPMENT_SECURITY' && (
                  <label className="ns-field certification-wide"><span>Gestión Softtek</span><textarea maxLength={1000} value={cycle.softtekManagement ?? ''} onChange={(event) => onUpdate(cycle.key, { softtekManagement: event.target.value })} /></label>
                )}
                {type === 'NORMATIVE_TESTING' && (
                  <label className="ns-field certification-wide"><span>Acciones a realizar</span><textarea maxLength={1000} value={cycle.actionsToTake ?? ''} onChange={(event) => onUpdate(cycle.key, { actionsToTake: event.target.value })} /></label>
                )}
                <label className="ns-field certification-wide"><span>Observaciones</span><textarea maxLength={1000} value={cycle.observations ?? ''} onChange={(event) => onUpdate(cycle.key, { observations: event.target.value })} /></label>
              </div>

              {supportsAttempts(type) && (
                <div className="attempt-draft">
                  <label className="certification-toggle">
                    <input type="checkbox" checked={Boolean(attempt)} onChange={(event) => onToggleAttempt(cycle, event.target.checked)} />
                    <span>{attempt?.publicId ? 'Editar el intento seleccionado al guardar' : 'Registrar un nuevo intento al guardar'}</span>
                  </label>
                  {attempt && (
                    <div className="certification-grid">
                      <label className="ns-field"><span>Fecha programada</span><DateField value={attempt.scheduledDate ?? ''} onChange={(value) => onUpdateAttempt(cycle.key, { scheduledDate: value || null })} ariaLabel="Seleccionar fecha programada del intento" /></label>
                      <label className="ns-field"><span>Fecha de aplicación</span><DateField value={attempt.applicationDate ?? ''}
                        onChange={(value) => onUpdateAttempt(cycle.key, { applicationDate: value || null })}
                        ariaInvalid={Boolean(fieldErrors[`${cycle.key}.attempt.applicationDate`])} ariaLabel="Seleccionar fecha de aplicación del intento" />
                        {fieldErrors[`${cycle.key}.attempt.applicationDate`] && <small className="field-error">{fieldErrors[`${cycle.key}.attempt.applicationDate`]}</small>}
                      </label>
                      <label className="ns-field"><span>Estado del examen</span><select value={attempt.examStatus}
                        onChange={(event) => onUpdateAttempt(cycle.key, { examStatus: event.target.value as CertificationExamStatus })}>
                        {catalogs.examStatuses.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
                      </select></label>
                      <label className="ns-field"><span>Promedio</span><input type="number" min="0" max="100" step="0.01" value={attempt.score ?? ''}
                        onChange={(event) => onUpdateAttempt(cycle.key, { score: event.target.value === '' ? null : Number(event.target.value) })}
                        aria-invalid={Boolean(fieldErrors[`${cycle.key}.attempt.score`])} />
                        {fieldErrors[`${cycle.key}.attempt.score`] && <small className="field-error">{fieldErrors[`${cycle.key}.attempt.score`]}</small>}
                      </label>
                      <label className="ns-field"><span>Resultado</span><input maxLength={1000} value={attempt.result ?? ''} onChange={(event) => onUpdateAttempt(cycle.key, { result: event.target.value })} /></label>
                      <label className="ns-field"><span>Aprobado</span><select value={attempt.approved === null || attempt.approved === undefined ? '' : attempt.approved ? 'true' : 'false'} onChange={(event) => onUpdateAttempt(cycle.key, { approved: event.target.value === '' ? null : event.target.value === 'true' })}><option value="">Pendiente</option><option value="true">Sí</option><option value="false">No</option></select></label>
                      <label className="ns-field certification-wide"><span>Observaciones</span><textarea maxLength={1000} value={attempt.observations ?? ''} onChange={(event) => onUpdateAttempt(cycle.key, { observations: event.target.value })} /></label>
                    </div>
                  )}
                </div>
              )}
            </article>
          )
        })}
      </div>
    </section>
  )
}
