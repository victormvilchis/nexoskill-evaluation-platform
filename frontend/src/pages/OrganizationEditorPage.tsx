import { type FormEvent, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  createOrganization,
  getOrganization,
  updateOrganization
} from '../features/organizations/api/organizationApi'
import type {
  ContentMode,
  OrganizationDetail,
  OrganizationPayload,
  OrganizationType
} from '../features/organizations/types/organizations'
import { ApiRequestError } from '../shared/api/apiClient'
import { BackButton } from '../shared/components/BackButton'
import { Icon } from '../shared/components/Icon'
import { useSaveNavigation } from '../shared/hooks/useSaveNavigation'

const CONTENT_MODE_HELP: Record<ContentMode, string> = {
  GLOBAL_CATALOG: 'La organización podrá consumir el catálogo global habilitado por Valtieris Talent Platform.',
  CLEAN: 'La organización iniciará sin contenido heredado y construirá su propio catálogo.',
  CUSTOM: 'La organización combinará contenido global autorizado con contenido propio.'
}

function localDateValue(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function nextMonth(value: string) {
  const date = new Date(`${value}T12:00:00`)
  date.setMonth(date.getMonth() + 1)
  return localDateValue(date)
}

function initialModel(): OrganizationPayload {
  const today = localDateValue(new Date())
  return {
    name: '',
    code: '',
    contentMode: 'CLEAN',
    appliesCertifications: false,
    manualStudentCode: false,
    contractedSeats: 10,
    includedReplacements: 2,
    additionalReplacements: 0,
    standardReleaseHours: 24,
    exhaustedReleaseDays: 7,
    cycleStartsOn: today,
    cycleEndsOn: nextMonth(today)
  }
}

function detailToPayload(detail: OrganizationDetail): OrganizationPayload {
  return {
    name: detail.name,
    code: detail.code,
    contentMode: detail.contentMode,
    appliesCertifications: detail.appliesCertifications,
    manualStudentCode: detail.manualStudentCode,
    expiresOn: detail.expiresOn,
    contractedSeats: detail.contractedSeats ?? 0,
    includedReplacements: detail.includedReplacements ?? 0,
    additionalReplacements: detail.additionalReplacements ?? 0,
    standardReleaseHours: detail.standardReleaseHours ?? 24,
    exhaustedReleaseDays: detail.exhaustedReleaseDays ?? 7,
    cycleStartsOn: detail.cycleStartsOn,
    cycleEndsOn: detail.cycleEndsOn,
    version: detail.version
  }
}

function sanitizeCode(value: string) {
  return value.toUpperCase().replace(/[^A-Z0-9_]+/g, '_').replace(/^_+/, '').slice(0, 80)
}

function validate(model: OrganizationPayload, editing: boolean) {
  const errors: Record<string, string> = {}
  const name = model.name.trim()
  const code = model.code?.trim() ?? ''
  const today = localDateValue(new Date())

  if (!name) errors.name = 'El nombre es obligatorio.'
  else if (name.length > 200) errors.name = 'El nombre no puede superar 200 caracteres.'

  if (!editing && !code) errors.code = 'El código es obligatorio.'
  else if (!editing && !/^[A-Z0-9_]+$/.test(code)) {
    errors.code = 'Usa únicamente letras, números y guion bajo.'
  }

  if (model.expiresOn && !editing && model.expiresOn < today) {
    errors.expiresOn = 'El vencimiento no puede ser anterior al día de creación.'
  }

  const numericFields: Array<[keyof OrganizationPayload, number | undefined, string, number]> = [
    ['contractedSeats', model.contractedSeats, 'Los asientos contratados', 0],
    ['includedReplacements', model.includedReplacements, 'Las sustituciones incluidas', 0],
    ['additionalReplacements', model.additionalReplacements, 'Las sustituciones adicionales', 0],
    ['standardReleaseHours', model.standardReleaseHours, 'La liberación estándar', 1],
    ['exhaustedReleaseDays', model.exhaustedReleaseDays, 'El bloqueo antifraude', 0]
  ]

  numericFields.forEach(([key, value, label, minimum]) => {
    if (value === undefined || !Number.isInteger(value) || value < minimum) {
      errors[String(key)] = `${label} debe ser un número entero mayor o igual a ${minimum}.`
    }
  })

  if (!model.cycleStartsOn) errors.cycleStartsOn = 'El inicio de ciclo es obligatorio.'
  if (!model.cycleEndsOn) errors.cycleEndsOn = 'El fin de ciclo es obligatorio.'
  if (model.cycleStartsOn && model.cycleEndsOn && model.cycleEndsOn <= model.cycleStartsOn) {
    errors.cycleEndsOn = 'El fin de ciclo debe ser posterior al inicio.'
  }
  return errors
}

function FieldError({ message }: { message?: string }) {
  return message ? <small className="org-field-error">{message}</small> : null
}

type OrganizationEditorMode = 'create' | 'view' | 'edit'

export function OrganizationEditorPage({ mode }: { mode: OrganizationEditorMode }) {
  const { publicId } = useParams()
  const editing = mode !== 'create'
  const readOnly = mode === 'view'
  const navigate = useNavigate()
  const completeSave = useSaveNavigation('/admin/organizations')
  const [model, setModel] = useState<OrganizationPayload>(() => initialModel())
  const [organizationType, setOrganizationType] = useState<OrganizationType>('CUSTOMER')
  const [validFrom, setValidFrom] = useState<string>()
  const [loading, setLoading] = useState(editing)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [status, setStatus] = useState<OrganizationDetail['status']>()
  const global = organizationType === 'GLOBAL'
  const formLocked = readOnly || global

  useEffect(() => {
    if (!publicId) return
    const controller = new AbortController()
    setLoading(true)
    setError('')
    getOrganization(publicId, controller.signal)
      .then((detail) => {
        setModel(detailToPayload(detail))
        setStatus(detail.status)
        setOrganizationType(detail.organizationType)
        setValidFrom(detail.validFrom)
      })
      .catch((requestError: unknown) => {
        if (!controller.signal.aborted) {
          setError(requestError instanceof ApiRequestError
            ? requestError.message
            : 'No fue posible cargar la organización.')
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [publicId])

  function set<K extends keyof OrganizationPayload>(key: K, value: OrganizationPayload[K]) {
    setModel((current) => ({ ...current, [key]: value }))
    setFieldErrors((current) => {
      if (!current[String(key)]) return current
      const next = { ...current }
      delete next[String(key)]
      return next
    })
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (formLocked || saving || loading) return
    const validationErrors = validate(model, editing)
    if (Object.keys(validationErrors).length > 0) {
      setFieldErrors(validationErrors)
      setError('Revisa los campos marcados antes de guardar.')
      return
    }
    setSaving(true)
    setError('')
    setFieldErrors({})
    const payload: OrganizationPayload = {
      ...model,
      name: model.name.trim(),
      code: editing ? model.code : sanitizeCode(model.code ?? ''),
      expiresOn: model.expiresOn || undefined
    }
    try {
      if (editing && publicId) {
        await updateOrganization(publicId, payload)
        completeSave({
          title: 'Organización actualizada correctamente.',
          message: 'Los cambios quedaron guardados.'
        })
      } else {
        await createOrganization(payload)
        completeSave({
          title: 'Organización creada correctamente.',
          message: 'La nueva organización ya aparece en el listado de activas.'
        })
      }
    } catch (requestError: unknown) {
      if (requestError instanceof ApiRequestError) {
        setError(requestError.message)
        setFieldErrors(requestError.fieldErrors ?? {})
      } else setError('No fue posible guardar la organización.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <main className="content-page org-editor-page">
      <BackButton fallback="/admin/organizations" />

      <header className="ns-page-header org-editor-header">
        <div>
          <p className="eyebrow">Multiorganización</p>
          <h1>{readOnly ? 'Detalle de organización' : editing ? 'Editar organización' : 'Nueva organización'}</h1>
          <p className="muted">
            {global
              ? 'Contexto interno global de Valtieris Talent Platform. No utiliza licenciamiento comercial.'
              : 'Configura identidad, contenido, vencimiento y política comercial.'}
          </p>
        </div>
        {editing && status && <span className={`status-badge status-${status.toLowerCase()}`}>{status}</span>}
      </header>

      {error && (
        <section className="inline-error-panel org-editor-error" role="alert">
          <div className="inline-error-icon"><Icon name="error" size={20} /></div>
          <div><strong>No fue posible completar la operación</strong><p>{error}</p></div>
        </section>
      )}

      {loading ? <section className="ns-loading-card">Cargando configuración…</section> : (
        <form className="org-editor-form" noValidate onSubmit={submit}>
          <fieldset className="org-editor-fieldset" disabled={formLocked || saving}>
            <section className="ns-card">
              <div className="ns-card-heading">
                <div><span className="ns-step">1</span><h2>Información general</h2></div>
                <p>La fecha de inicio se genera automáticamente al crear la organización.</p>
              </div>
              <div className="org-form-grid">
                <label className="ns-field org-field-wide-tablet">
                  <span>Nombre <b>*</b></span>
                  <input aria-invalid={Boolean(fieldErrors.name)} maxLength={200} value={model.name}
                    onChange={(event) => set('name', event.target.value)} />
                  <FieldError message={fieldErrors.name} />
                </label>
                <label className="ns-field">
                  <span>Código <b>*</b></span>
                  <input aria-invalid={Boolean(fieldErrors.code)} disabled={editing} maxLength={80}
                    placeholder="EJEMPLO_CORP" value={model.code ?? ''}
                    onChange={(event) => set('code', sanitizeCode(event.target.value))} />
                  <FieldError message={fieldErrors.code} />
                </label>
                <label className="ns-field">
                  <span>Tipo</span>
                  <input disabled value={global ? 'GLOBAL' : 'Organización comercial'} />
                </label>
                <label className="ns-field">
                  <span>Modalidad de contenido <b>*</b></span>
                  <select value={model.contentMode}
                    onChange={(event) => set('contentMode', event.target.value as ContentMode)}>
                    <option value="GLOBAL_CATALOG">Catálogo global completo</option>
                    <option value="CLEAN">Organización en limpio</option>
                    <option value="CUSTOM">Configuración personalizada</option>
                  </select>
                  <small className="org-field-help">{CONTENT_MODE_HELP[model.contentMode]}</small>
                </label>
                {editing && (
                  <label className="ns-field">
                    <span>Vigencia desde</span>
                    <input disabled type="date" value={validFrom ?? ''} />
                  </label>
                )}
                {!global && (
                  <label className="ns-field org-certification-toggle">
                    <span>Gestión de certificaciones</span>
                    <span className="org-check-row">
                      <input
                        type="checkbox"
                        checked={model.appliesCertifications}
                        onChange={(event) => set('appliesCertifications', event.target.checked)}
                      />
                      Aplica certificaciones
                    </span>
                    <small className="org-field-help">
                      Habilita el seguimiento independiente de certificaciones para Gestores y Supervisores.
                    </small>
                  </label>
                )}
                {!global && (
                  <label className="ns-field org-certification-toggle">
                    <span>Código de colaborador</span>
                    <span className="org-check-row">
                      <input
                        type="checkbox"
                        checked={model.manualStudentCode}
                        onChange={(event) => set('manualStudentCode', event.target.checked)}
                      />
                      Código de colaborador manual
                    </span>
                    <small className="org-field-help">
                      Al habilitarlo, el Código a nivel organización deberá capturarse para cada colaborador.
                    </small>
                  </label>
                )}
                {!global && (
                  <label className="ns-field">
                    <span>Fecha de vencimiento</span>
                    <input aria-invalid={Boolean(fieldErrors.expiresOn)} min={validFrom ?? localDateValue(new Date())}
                      type="date" value={model.expiresOn ?? ''}
                      onChange={(event) => set('expiresOn', event.target.value || undefined)} />
                    <small className="org-field-help">Déjalo vacío para vigencia indefinida.</small>
                    <FieldError message={fieldErrors.expiresOn} />
                  </label>
                )}
              </div>
            </section>

            {!global && (
              <section className="ns-card">
                <div className="ns-card-heading">
                  <div><span className="ns-step">2</span><h2>Política de licenciamiento</h2></div>
                  <p>Estos campos aplican únicamente a organizaciones comerciales.</p>
                </div>
                <div className="org-form-grid org-license-grid">
                  <label className="ns-field"><span>Asientos contratados <b>*</b></span><input min="0" step="1" type="number" value={model.contractedSeats} onChange={(event) => set('contractedSeats', Number(event.target.value))} /><FieldError message={fieldErrors.contractedSeats} /></label>
                  <label className="ns-field"><span>Sustituciones incluidas <b>*</b></span><input min="0" step="1" type="number" value={model.includedReplacements} onChange={(event) => set('includedReplacements', Number(event.target.value))} /><FieldError message={fieldErrors.includedReplacements} /></label>
                  <label className="ns-field"><span>Sustituciones adicionales <b>*</b></span><input min="0" step="1" type="number" value={model.additionalReplacements ?? 0} onChange={(event) => set('additionalReplacements', Number(event.target.value))} /><FieldError message={fieldErrors.additionalReplacements} /></label>
                  <label className="ns-field"><span>Liberación estándar (horas) <b>*</b></span><input min="1" step="1" type="number" value={model.standardReleaseHours ?? 24} onChange={(event) => set('standardReleaseHours', Number(event.target.value))} /><FieldError message={fieldErrors.standardReleaseHours} /></label>
                  <label className="ns-field"><span>Bloqueo antifraude (días) <b>*</b></span><input min="0" step="1" type="number" value={model.exhaustedReleaseDays ?? 7} onChange={(event) => set('exhaustedReleaseDays', Number(event.target.value))} /><FieldError message={fieldErrors.exhaustedReleaseDays} /></label>
                  <label className="ns-field"><span>Inicio de ciclo <b>*</b></span><input type="date" value={model.cycleStartsOn ?? ''} onChange={(event) => set('cycleStartsOn', event.target.value)} /><FieldError message={fieldErrors.cycleStartsOn} /></label>
                  <label className="ns-field"><span>Fin de ciclo <b>*</b></span><input min={model.cycleStartsOn} type="date" value={model.cycleEndsOn ?? ''} onChange={(event) => set('cycleEndsOn', event.target.value)} /><FieldError message={fieldErrors.cycleEndsOn} /></label>
                </div>
              </section>
            )}
          </fieldset>

          {!readOnly && (
            <footer className="org-editor-actions">
              <button type="button" className="secondary-button" disabled={saving}
                onClick={() => navigate('/admin/organizations')}>Cancelar</button>
              {!formLocked && (
                <button type="submit" className="primary-button" disabled={saving}>
                  {saving ? 'Guardando…' : editing ? 'Guardar cambios' : 'Guardar organización'}
                </button>
              )}
            </footer>
          )}
        </form>
      )}
    </main>
  )
}
