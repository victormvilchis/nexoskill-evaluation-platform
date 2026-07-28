import { useEffect, useMemo, useState } from 'react'
import { searchOrganizations } from '../../organizations/api/organizationApi'
import type { OrganizationSummary } from '../../organizations/types/organizations'
import type { QuestionDetail, QuestionPayload } from '../../../shared/types/questions'
import { ApiRequestError } from '../../../shared/api/apiClient'
import { useDebouncedValue } from '../../../shared/hooks/useDebouncedValue'
import { QuestionEditor } from './QuestionEditor'
import {
  getQuestionAvailability,
  type QuestionAvailability,
  type QuestionAvailabilityMode
} from '../api/questionAvailabilityApi'

interface Props {
  initial?: QuestionDetail
  globalAdministrator: boolean
  submitLabel: string
  onSubmit: (payload: QuestionPayload, availability?: QuestionAvailability) => Promise<void>
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
  const [mode, setMode] = useState<QuestionAvailabilityMode>('GLOBAL')
  const [selected, setSelected] = useState<string[]>([])
  const [organizations, setOrganizations] = useState<OrganizationSummary[]>([])
  const [query, setQuery] = useState('')
  const debouncedQuery = useDebouncedValue(query, 300)
  const [loadingOrganizations, setLoadingOrganizations] = useState(false)
  const [error, setError] = useState<string>()

  useEffect(() => {
    if (!globalAdministrator || !initial?.publicId) return
    const controller = new AbortController()
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
          studentCount: 0,
          updatedAt: ''
        }))))
      })
      .catch(() => { if (!controller.signal.aborted) setError('No fue posible consultar la disponibilidad actual.') })
    return () => controller.abort()
  }, [globalAdministrator, initial?.publicId])

  useEffect(() => {
    if (!globalAdministrator || mode !== 'SELECTED_ORGANIZATIONS') return
    const controller = new AbortController()
    setLoadingOrganizations(true)
    searchOrganizations({ query: debouncedQuery, status: 'ACTIVE', page: 0, size: 25, sort: 'name', direction: 'ASC', signal: controller.signal })
      .then((page) => setOrganizations((current) => mergeOrganizations(current,
        page.content.filter((item) => item.organizationType === 'CUSTOMER'))))
      .catch(() => { if (!controller.signal.aborted) setError('No fue posible consultar las organizaciones.') })
      .finally(() => { if (!controller.signal.aborted) setLoadingOrganizations(false) })
    return () => controller.abort()
  }, [debouncedQuery, globalAdministrator, mode])

  const visibleOrganizations = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase('es-MX')
    if (!normalized) return organizations
    return organizations.filter((organization) =>
      organization.name.toLocaleLowerCase('es-MX').includes(normalized)
      || organization.code.toLocaleLowerCase('es-MX').includes(normalized))
  }, [organizations, query])

  function toggle(publicId: string) {
    setSelected((current) => current.includes(publicId)
      ? current.filter((value) => value !== publicId)
      : [...current, publicId])
  }

  async function submit(payload: QuestionPayload) {
    setError(undefined)
    if (globalAdministrator && mode === 'SELECTED_ORGANIZATIONS' && selected.length === 0) {
      setError('Selecciona al menos una organización para esta pregunta.')
      throw new ApiRequestError('Selecciona al menos una organización para esta pregunta.', 'QUESTION_ORGANIZATIONS_REQUIRED', 400)
    }
    const cleanPayload: QuestionPayload = {
      ...payload,
      difficultyCode: normalizedDifficulty(payload.difficultyCode),
      technologyPublicId: undefined,
      levelCode: undefined
    }
    await onSubmit(cleanPayload, globalAdministrator ? {
      mode,
      organizations: organizations
        .filter((organization) => selected.includes(organization.publicId))
        .map((organization) => ({ publicId: organization.publicId, code: organization.code, name: organization.name }))
    } : undefined)
  }

  return (
    <div className="question-editor-v2-shell">
      {globalAdministrator && (
        <section className="editor-card question-availability-card">
          <div className="section-heading">
            <div><p className="eyebrow">Disponibilidad</p><h2>Organizaciones con acceso</h2></div>
            <span className="availability-count">{mode === 'GLOBAL' ? 'Todas' : `${selected.length} seleccionadas`}</span>
          </div>
          <div className="availability-mode" role="radiogroup" aria-label="Disponibilidad de la pregunta">
            <label>
              <input type="radio" name="availabilityMode" checked={mode === 'GLOBAL'} onChange={() => { setMode('GLOBAL'); setSelected([]) }} />
              <span><strong>Disponible para todas las organizaciones</strong><small>La pregunta seguirá siendo propiedad de GLOBAL y no se copiará.</small></span>
            </label>
            <label>
              <input type="radio" name="availabilityMode" checked={mode === 'SELECTED_ORGANIZATIONS'} onChange={() => setMode('SELECTED_ORGANIZATIONS')} />
              <span><strong>Disponible solo para organizaciones seleccionadas</strong><small>Solo las organizaciones habilitadas podrán consultarla y utilizarla.</small></span>
            </label>
          </div>
          {mode === 'SELECTED_ORGANIZATIONS' && (
            <div className="organization-multiselect">
              <label htmlFor="organization-search">Buscar organizaciones</label>
              <input id="organization-search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Nombre o código" />
              <div className="selected-organization-chips" aria-label="Organizaciones seleccionadas">
                {organizations.filter((organization) => selected.includes(organization.publicId)).slice(0, 4).map((organization) => (
                  <button key={organization.publicId} type="button" onClick={() => toggle(organization.publicId)}>#{organization.code} ×</button>
                ))}
                {selected.length > 4 && <span>+{selected.length - 4}</span>}
              </div>
              <div className="organization-options" role="group" aria-label="Organizaciones disponibles" aria-busy={loadingOrganizations}>
                {visibleOrganizations.map((organization) => (
                  <label key={organization.publicId}>
                    <input type="checkbox" checked={selected.includes(organization.publicId)} onChange={() => toggle(organization.publicId)} />
                    <span><strong>{organization.name}</strong><small>{organization.code}</small></span>
                  </label>
                ))}
                {!loadingOrganizations && visibleOrganizations.length === 0 && <p className="muted">No se encontraron organizaciones activas.</p>}
              </div>
            </div>
          )}
          {error && <p className="error-message" role="alert">{error}</p>}
        </section>
      )}
      <QuestionEditor initial={initial} onSubmit={submit} submitLabel={submitLabel} />
    </div>
  )
}
