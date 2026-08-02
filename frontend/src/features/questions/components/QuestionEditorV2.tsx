import { useEffect, useMemo, useState } from 'react'
import { searchOrganizations } from '../../organizations/api/organizationApi'
import type { OrganizationSummary } from '../../organizations/types/organizations'
import type { ContentScope, QuestionDetail, QuestionPayload } from '../../../shared/types/questions'
import { ApiRequestError } from '../../../shared/api/apiClient'
import { QuestionEditor } from './QuestionEditor'
import {
  getQuestionAvailability,
  type QuestionAvailabilityMode
} from '../api/questionAvailabilityApi'

interface Props {
  initial?: QuestionDetail
  globalAdministrator: boolean
  submitLabel: string
  onSubmit: (payload: QuestionPayload) => Promise<void>
}

function normalizedDifficulty(value?: string) {
  const normalized = (value ?? '').toUpperCase()
  if (['JR', 'STD', 'SR'].includes(normalized)) return normalized
  if (['BASIC', 'EASY', 'BEGINNER', 'BASICO'].includes(normalized)) return 'JR'
  if (['INTERMEDIATE', 'MEDIUM', 'INTERMEDIO'].includes(normalized)) return 'STD'
  if (['ADVANCED', 'HARD', 'EXPERT', 'AVANZADO'].includes(normalized)) return 'SR'
  return 'JR'
}

function mergeOrganizations(current: OrganizationSummary[], incoming: OrganizationSummary[]) {
  const values = new Map(current.map((organization) => [organization.publicId, organization]))
  incoming.forEach((organization) => values.set(organization.publicId, organization))
  return Array.from(values.values()).sort((left, right) => left.name.localeCompare(right.name, 'es-MX'))
}

export function QuestionEditorV2({ initial, globalAdministrator, submitLabel, onSubmit }: Props) {
  const editing = Boolean(initial)
  const [targetScope, setTargetScope] = useState<ContentScope>(initial?.ownership.scope ?? 'GLOBAL')
  const [ownerOrganizationPublicId, setOwnerOrganizationPublicId] = useState(
    initial?.ownership.organizationPublicId ?? ''
  )
  const [mode, setMode] = useState<QuestionAvailabilityMode>('NONE')
  const [selected, setSelected] = useState<string[]>([])
  const [organizations, setOrganizations] = useState<OrganizationSummary[]>([])
  const [loadingOrganizations, setLoadingOrganizations] = useState(false)
  const [loadingAvailability, setLoadingAvailability] = useState(Boolean(globalAdministrator && initial?.ownership.scope === 'GLOBAL'))
  const [error, setError] = useState<string>()
  const globalQuestion = targetScope === 'GLOBAL'

  useEffect(() => {
    if (!globalAdministrator) return
    const controller = new AbortController()
    setLoadingOrganizations(true)
    searchOrganizations({
      status: 'ACTIVE',
      page: 0,
      size: 100,
      sort: 'name',
      direction: 'ASC',
      signal: controller.signal
    })
      .then((page) => setOrganizations((current) => mergeOrganizations(current,
        page.content.filter((item) => item.organizationType === 'CUSTOMER'))))
      .catch(() => { if (!controller.signal.aborted) setError('No fue posible consultar las organizaciones.') })
      .finally(() => { if (!controller.signal.aborted) setLoadingOrganizations(false) })
    return () => controller.abort()
  }, [globalAdministrator])

  useEffect(() => {
    if (!globalAdministrator || !globalQuestion || !initial?.publicId) {
      setLoadingAvailability(false)
      return
    }
    const controller = new AbortController()
    setLoadingAvailability(true)
    getQuestionAvailability(initial.publicId, controller.signal)
      .then((availability) => {
        setMode(availability.mode)
        setSelected(availability.organizations.map((organization) => organization.publicId))
        setOrganizations((current) => mergeOrganizations(current, availability.organizations.map((organization) => ({
          publicId: organization.publicId,
          code: organization.code,
          name: organization.name,
          organizationType: 'CUSTOMER',
          status: 'ACTIVE',
          contentMode: 'CUSTOM',
          appliesCertifications: false,
          manualStudentCode: false,
          studentCount: 0,
          activeStudentCount: 0,
          inactiveStudentCount: 0,
          expiredStudentCount: 0,
          updatedAt: ''
        }))))
      })
      .catch(() => { if (!controller.signal.aborted) setError('No fue posible consultar la disponibilidad actual.') })
      .finally(() => { if (!controller.signal.aborted) setLoadingAvailability(false) })
    return () => controller.abort()
  }, [globalAdministrator, globalQuestion, initial?.publicId])

  const selectedOrganizations = useMemo(() => organizations.filter(
    (organization) => selected.includes(organization.publicId)
  ), [organizations, selected])

  function changeTargetScope(scope: ContentScope) {
    if (editing) return
    setTargetScope(scope)
    setOwnerOrganizationPublicId('')
    setSelected([])
    setMode('NONE')
    setError(undefined)
  }

  function toggle(publicId: string) {
    setSelected((current) => current.includes(publicId)
      ? current.filter((value) => value !== publicId)
      : [...current, publicId])
  }

  async function submit(payload: QuestionPayload) {
    setError(undefined)
    if (loadingAvailability) {
      const message = 'Espera a que termine de cargarse la disponibilidad de la pregunta.'
      setError(message)
      throw new ApiRequestError(message, 'QUESTION_AVAILABILITY_LOADING', 409)
    }
    if (globalAdministrator && !editing && targetScope === 'ORGANIZATION' && !ownerOrganizationPublicId) {
      const message = 'Selecciona la organización propietaria de la pregunta.'
      setError(message)
      throw new ApiRequestError(message, 'QUESTION_OWNER_ORGANIZATION_REQUIRED', 400)
    }
    if (globalAdministrator && globalQuestion && mode === 'SELECTED_ORGANIZATIONS' && selected.length === 0) {
      const message = 'Selecciona al menos una organización para esta pregunta.'
      setError(message)
      throw new ApiRequestError(message, 'QUESTION_ORGANIZATIONS_REQUIRED', 400)
    }
    const cleanPayload: QuestionPayload = {
      ...payload,
      difficultyCode: normalizedDifficulty(payload.difficultyCode),
      technologyPublicId: undefined,
      levelCode: undefined,
      contentScope: globalAdministrator && !editing ? targetScope : undefined,
      organizationPublicId: globalAdministrator && !editing && targetScope === 'ORGANIZATION'
        ? ownerOrganizationPublicId
        : undefined,
      availabilityMode: globalAdministrator && globalQuestion ? mode : undefined,
      availabilityOrganizationPublicIds: globalAdministrator && globalQuestion && mode === 'SELECTED_ORGANIZATIONS'
        ? selectedOrganizations.map((organization) => organization.publicId)
        : undefined
    }
    await onSubmit(cleanPayload)
  }

  return (
    <div className="question-editor-v2-shell">
      {globalAdministrator && !editing && (
        <section className="editor-card question-ownership-card">
          <div className="section-heading">
            <div><p className="eyebrow">Propiedad</p><h2>Alcance de la pregunta</h2></div>
          </div>
          <div className="availability-mode" role="radiogroup" aria-label="Alcance de la pregunta">
            <label>
              <input type="radio" name="questionScope" checked={targetScope === 'GLOBAL'} onChange={() => changeTargetScope('GLOBAL')} />
              <span><strong>Pregunta GLOBAL</strong><small>Contenido maestro administrado transversalmente.</small></span>
            </label>
            <label>
              <input type="radio" name="questionScope" checked={targetScope === 'ORGANIZATION'} onChange={() => changeTargetScope('ORGANIZATION')} />
              <span><strong>Pregunta para una organización</strong><small>Contenido privado de una organización comercial.</small></span>
            </label>
          </div>
          {targetScope === 'ORGANIZATION' && (
            <label className="ns-field">
              <span>Organización propietaria</span>
              <select
                value={ownerOrganizationPublicId}
                onChange={(event) => setOwnerOrganizationPublicId(event.target.value)}
                disabled={loadingOrganizations}
                required
              >
                <option value="">Selecciona una organización</option>
                {organizations.map((organization) => (
                  <option value={organization.publicId} key={organization.publicId}>
                    {organization.name} · {organization.code}
                  </option>
                ))}
              </select>
            </label>
          )}
        </section>
      )}

      {globalAdministrator && globalQuestion && (
        <section className="editor-card question-availability-card">
          <div className="section-heading">
            <div><p className="eyebrow">Disponibilidad</p><h2>Acceso organizacional</h2></div>
            <span className="availability-count">
              {loadingAvailability ? 'Cargando…' : mode === 'NONE' ? 'Sin publicar' : mode === 'GLOBAL' ? 'Todas' : `${selected.length} seleccionadas`}
            </span>
          </div>
          {editing && initial?.forms.length ? (
            <p className="inline-warning-text">Esta pregunta global ya se utiliza en contenido. Los cambios se reflejarán en quienes consuman la referencia global, pero no modificarán copias organizacionales.</p>
          ) : null}
          <div className="availability-mode" role="radiogroup" aria-label="Disponibilidad de la pregunta">
            <label>
              <input type="radio" name="availabilityMode" disabled={loadingAvailability} checked={mode === 'NONE'} onChange={() => { setMode('NONE'); setSelected([]) }} />
              <span><strong>No disponible para organizaciones</strong><small>Permanece en el catálogo GLOBAL sin publicación organizacional.</small></span>
            </label>
            <label>
              <input type="radio" name="availabilityMode" disabled={loadingAvailability} checked={mode === 'GLOBAL'} onChange={() => { setMode('GLOBAL'); setSelected([]) }} />
              <span><strong>Disponible para todas las organizaciones</strong><small>La pregunta seguirá siendo propiedad de GLOBAL y no se copiará.</small></span>
            </label>
            <label>
              <input type="radio" name="availabilityMode" disabled={loadingAvailability} checked={mode === 'SELECTED_ORGANIZATIONS'} onChange={() => setMode('SELECTED_ORGANIZATIONS')} />
              <span><strong>Disponible solo para organizaciones seleccionadas</strong><small>Solo las organizaciones habilitadas podrán consultarla y utilizarla.</small></span>
            </label>
          </div>
          {mode === 'SELECTED_ORGANIZATIONS' && (
            <div className="organization-multiselect">
              <div className="organization-list-heading">
                <strong>Organizaciones disponibles</strong>
                <small>{loadingOrganizations ? 'Cargando…' : `${organizations.length} disponibles`}</small>
              </div>
              <div className="selected-organization-chips" aria-label="Organizaciones seleccionadas">
                {selectedOrganizations.slice(0, 4).map((organization) => (
                  <button key={organization.publicId} type="button" onClick={() => toggle(organization.publicId)}>#{organization.code} ×</button>
                ))}
                {selected.length > 4 && <span>+{selected.length - 4}</span>}
              </div>
              <div className="organization-options" role="group" aria-label="Organizaciones disponibles" aria-busy={loadingOrganizations}>
                {organizations.map((organization) => (
                  <label key={organization.publicId}>
                    <input type="checkbox" checked={selected.includes(organization.publicId)} onChange={() => toggle(organization.publicId)} />
                    <span><strong>{organization.name}</strong><small>{organization.code}</small></span>
                  </label>
                ))}
                {!loadingOrganizations && organizations.length === 0 && <p className="muted">No existen organizaciones comerciales activas.</p>}
              </div>
            </div>
          )}
        </section>
      )}
      {error && <p className="error-message" role="alert">{error}</p>}
      <QuestionEditor
        initial={initial}
        onSubmit={submit}
        submitLabel={submitLabel}
        targetScope={editing ? initial?.ownership.scope : targetScope}
        organizationPublicId={editing
          ? initial?.ownership.organizationPublicId
          : targetScope === 'ORGANIZATION' ? ownerOrganizationPublicId : undefined}
      />
    </div>
  )
}
