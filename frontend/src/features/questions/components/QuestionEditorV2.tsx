import { useEffect, useMemo, useState } from 'react'
import { getAllOrganizations } from '../../organizations/api/organizationApi'
import type { OrganizationSummary } from '../../organizations/types/organizations'
import type { ContentScope, QuestionDetail, QuestionPayload } from '../../../shared/types/questions'
import { ApiRequestError } from '../../../shared/api/apiClient'
import { SelectField } from '../../../shared/components/SelectField'
import { QuestionEditor } from './QuestionEditor'
import type { QuestionAvailabilityMode } from '../api/questionAvailabilityApi'

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
  const [error, setError] = useState<string>()
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
      .catch(() => { if (!controller.signal.aborted) setError('No fue posible consultar las organizaciones.') })
      .finally(() => { if (!controller.signal.aborted) setLoadingOrganizations(false) })
    return () => controller.abort()
  }, [globalAdministrator])

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
    if (globalAdministrator && !editing && targetScope === 'ORGANIZATION' && !ownerOrganizationPublicId) {
      const message = 'Selecciona la organización propietaria de la pregunta.'
      setError(message)
      throw new ApiRequestError(message, 'QUESTION_OWNER_ORGANIZATION_REQUIRED', 400)
    }
    if (globalAdministrator && !editing && globalQuestion && mode === 'SELECTED_ORGANIZATIONS' && selected.length === 0) {
      const message = 'Selecciona al menos una organización para esta pregunta.'
      setError(message)
      throw new ApiRequestError(message, 'QUESTION_ORGANIZATIONS_REQUIRED', 400)
    }
    const cleanPayload: QuestionPayload = {
      ...payload,
      difficultyCode: normalizedDifficulty(payload.difficultyCode),
      contentScope: globalAdministrator && !editing ? targetScope : undefined,
      organizationPublicId: globalAdministrator && !editing && targetScope === 'ORGANIZATION'
        ? ownerOrganizationPublicId
        : undefined,
      availabilityMode: globalAdministrator && !editing && globalQuestion ? mode : undefined,
      availabilityOrganizationPublicIds: globalAdministrator && !editing && globalQuestion && mode === 'SELECTED_ORGANIZATIONS'
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

      {globalAdministrator && !editing && globalQuestion && (
        <section className="editor-card question-availability-card">
          <div className="section-heading">
            <div><p className="eyebrow">Distribución</p><h2>Copias organizacionales</h2></div>
            <span className="availability-count">
              {mode === 'NONE' ? 'Solo GLOBAL' : mode === 'GLOBAL' ? 'Todas' : `${selected.length} seleccionadas`}
            </span>
          </div>
          <div className="availability-mode" role="radiogroup" aria-label="Distribución de la pregunta">
            <label>
              <input type="radio" name="availabilityMode" checked={mode === 'NONE'} onChange={() => { setMode('NONE'); setSelected([]) }} />
              <span><strong>Crear únicamente en GLOBAL</strong><small>No se generará ninguna copia organizacional.</small></span>
            </label>
            <label>
              <input type="radio" name="availabilityMode" checked={mode === 'GLOBAL'} onChange={() => { setMode('GLOBAL'); setSelected([]) }} />
              <span><strong>Crear para todas las organizaciones</strong><small>Se generará una copia independiente para cada organización comercial activa.</small></span>
            </label>
            <label>
              <input type="radio" name="availabilityMode" checked={mode === 'SELECTED_ORGANIZATIONS'} onChange={() => setMode('SELECTED_ORGANIZATIONS')} />
              <span><strong>Crear para organizaciones seleccionadas</strong><small>Cada destino recibirá una copia independiente de la pregunta global.</small></span>
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
        catalogContextReady={!globalAdministrator || editing || targetScope === 'GLOBAL' || Boolean(ownerOrganizationPublicId)}
      />
    </div>
  )
}
