import { useEffect, useMemo, useState } from 'react'
import { getAllOrganizations } from '../../organizations/api/organizationApi'
import type { OrganizationSummary } from '../../organizations/types/organizations'
import type { ContentScope, QuestionDetail, QuestionPayload } from '../../../shared/types/questions'
import { ApiRequestError } from '../../../shared/api/apiClient'
import { SelectField } from '../../../shared/components/SelectField'
import { QuestionEditor } from './QuestionEditor'
import { getQuestionAvailability, type QuestionAvailabilityMode } from '../api/questionAvailabilityApi'

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
  const [targetScope, setTargetScope] = useState<ContentScope>(
    initial?.ownership.scope ?? (globalAdministrator ? 'GLOBAL' : 'ORGANIZATION')
  )
  const [ownerOrganizationPublicId, setOwnerOrganizationPublicId] = useState(
    initial?.ownership.organizationPublicId ?? ''
  )
  const [mode, setMode] = useState<QuestionAvailabilityMode>('NONE')
  const [selected, setSelected] = useState<string[]>([])
  const [organizations, setOrganizations] = useState<OrganizationSummary[]>([])
  const [loadingOrganizations, setLoadingOrganizations] = useState(false)
  const globalQuestion = targetScope === 'GLOBAL'

  useEffect(() => {
    if (!globalAdministrator) return
    const controller = new AbortController()
    setLoadingOrganizations(true)
    getAllOrganizations({
      status: 'ACTIVE',
      sort: 'name',
      direction: 'ASC',
      signal: controller.signal
    })
      .then((items) => setOrganizations((current) => mergeOrganizations(current,
        items.filter((item) => item.organizationType === 'CUSTOMER'))))
      .catch(() => undefined)
      .finally(() => { if (!controller.signal.aborted) setLoadingOrganizations(false) })
    return () => controller.abort()
  }, [globalAdministrator])

  useEffect(() => {
    if (!globalAdministrator || !initial || initial.ownership.scope !== 'GLOBAL') return
    const controller = new AbortController()
    getQuestionAvailability(initial.publicId, controller.signal)
      .then((availability) => {
        if (controller.signal.aborted) return
        setMode(availability.mode)
        setSelected(availability.organizations.map((organization) => organization.publicId))
      })
      .catch(() => undefined)
    return () => controller.abort()
  }, [globalAdministrator, initial])

  const selectedOrganizations = useMemo(() => organizations.filter(
    (organization) => selected.includes(organization.publicId)
  ), [organizations, selected])

  function changeTargetScope(scope: ContentScope) {
    if (editing) return
    setTargetScope(scope)
    setOwnerOrganizationPublicId('')
    setSelected([])
    setMode('NONE')
  }

  function toggle(publicId: string) {
    setSelected((current) => current.includes(publicId)
      ? current.filter((value) => value !== publicId)
      : [...current, publicId])
  }

  async function submit(payload: QuestionPayload) {
    if (globalAdministrator && !editing && targetScope === 'ORGANIZATION' && !ownerOrganizationPublicId) {
      const message = 'Selecciona la organización propietaria de la pregunta.'
      throw new ApiRequestError(message, 'QUESTION_OWNER_ORGANIZATION_REQUIRED', 400)
    }
    if (globalAdministrator && !editing && globalQuestion && mode === 'SELECTED_ORGANIZATIONS' && selected.length === 0) {
      const message = 'Selecciona al menos una organización para esta pregunta.'
      throw new ApiRequestError(message, 'QUESTION_ORGANIZATIONS_REQUIRED', 400)
    }
    const cleanPayload: QuestionPayload = {
      ...payload,
      difficultyCode: normalizedDifficulty(payload.difficultyCode),
      contentScope: globalAdministrator && !editing ? targetScope : undefined,
      organizationPublicId: globalAdministrator && !editing && targetScope === 'ORGANIZATION'
        ? ownerOrganizationPublicId
        : undefined,
      availabilityMode: globalAdministrator && globalQuestion ? mode : undefined,
      availabilityOrganizationPublicIds: globalAdministrator && globalQuestion && mode === 'SELECTED_ORGANIZATIONS'
        ? selected
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
              <SelectField
                value={ownerOrganizationPublicId}
                onChange={setOwnerOrganizationPublicId}
                disabled={loadingOrganizations}
                required
                ariaLabel="Organización propietaria"
                options={[
                  { value: '', label: 'Selecciona una organización' },
                  ...organizations.map((organization) => ({
                    value: organization.publicId,
                    label: `${organization.name} · ${organization.code}`
                  }))
                ]}
              />
            </label>
          )}
        </section>
      )}

      {globalAdministrator && globalQuestion && (
        <section className="editor-card question-availability-card">
          <div className="section-heading">
            <div><p className="eyebrow">Disponibilidad</p><h2>Disponibilidad para organizaciones</h2></div>
            <span className="availability-count">
              {mode === 'NONE' ? 'Solo GLOBAL' : mode === 'GLOBAL' ? 'Todas' : `${selected.length} seleccionadas`}
            </span>
          </div>
          <div className="availability-mode" role="radiogroup" aria-label="Disponibilidad de la pregunta">
            <label>
              <input type="radio" name="availabilityMode" checked={mode === 'NONE'} onChange={() => { setMode('NONE'); setSelected([]) }} />
              <span><strong>Solo GLOBAL</strong><small>La pregunta no estará disponible para organizaciones.</small></span>
            </label>
            <label>
              <input type="radio" name="availabilityMode" checked={mode === 'GLOBAL'} onChange={() => { setMode('GLOBAL'); setSelected([]) }} />
              <span><strong>Todas las organizaciones</strong><small>La misma pregunta GLOBAL estará disponible por referencia para todas las organizaciones comerciales.</small></span>
            </label>
            <label>
              <input type="radio" name="availabilityMode" checked={mode === 'SELECTED_ORGANIZATIONS'} onChange={() => setMode('SELECTED_ORGANIZATIONS')} />
              <span><strong>Organizaciones seleccionadas</strong><small>La misma pregunta GLOBAL estará disponible únicamente para las organizaciones elegidas.</small></span>
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
      <QuestionEditor
        initial={initial}
        onSubmit={submit}
        submitLabel={submitLabel}
        targetScope={editing ? initial?.ownership.scope : targetScope}
        organizationPublicId={editing
          ? initial?.ownership.organizationPublicId
          : targetScope === 'ORGANIZATION' ? ownerOrganizationPublicId : undefined}
        catalogContextReady={!globalAdministrator || editing || targetScope === 'GLOBAL' || Boolean(ownerOrganizationPublicId)}
      />
    </div>
  )
}
