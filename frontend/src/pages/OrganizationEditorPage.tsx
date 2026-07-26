import { type FormEvent, useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useAuth } from '../features/authentication/context/AuthContext'
import {
  createOrganization,
  getOrganization,
  updateOrganization
} from '../features/organizations/api/organizationApi'
import type {
  ContentMode,
  OrganizationDetail,
  OrganizationPayload
} from '../features/organizations/types/organizations'
import { ApiRequestError } from '../shared/api/apiClient'
import { Icon } from '../shared/components/Icon'
import { useToast } from '../shared/components/ToastProvider'

const CONTENT_MODE_HELP: Record<ContentMode, string> = {
  GLOBAL_CATALOG: 'La organización podrá consumir el catálogo global habilitado por NexoSkill.',
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
  const [yearValue, monthValue, dayValue] = value.split('-').map(Number)
  const year = yearValue ?? new Date().getFullYear()
  const month = (monthValue ?? 1) - 1
  const day = dayValue ?? 1
  const target = new Date(year, month + 1, 1)
  const lastDay = new Date(target.getFullYear(), target.getMonth() + 1, 0).getDate()
  target.setDate(Math.min(day, lastDay))
  return localDateValue(target)
}

function initialModel(): OrganizationPayload {
  const today = localDateValue(new Date())
  return {
    name: '',
    code: '',
    contentMode: 'CLEAN',
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
    validFrom: detail.validFrom,
    expiresOn: detail.expiresOn,
    contractedSeats: detail.contractedSeats,
    includedReplacements: detail.includedReplacements,
    additionalReplacements: detail.additionalReplacements,
    standardReleaseHours: detail.standardReleaseHours,
    exhaustedReleaseDays: detail.exhaustedReleaseDays,
    cycleStartsOn: detail.cycleStartsOn,
    cycleEndsOn: detail.cycleEndsOn,
    version: detail.version
  }
}

function sanitizeCode(value: string) {
  return value
    .toUpperCase()
    .replace(/[^A-Z0-9_]+/g, '_')
    .replace(/^_+/, '')
    .slice(0, 80)
}

function validate(model: OrganizationPayload, editing: boolean) {
  const errors: Record<string, string> = {}
  const name = model.name.trim()
  const code = model.code?.trim() ?? ''

  if (!name) errors.name = 'El nombre es obligatorio.'
  else if (name.length > 200) errors.name = 'El nombre no puede superar 200 caracteres.'

  if (!editing && !code) errors.code = 'El código es obligatorio.'
  else if (!editing && !/^[A-Z0-9_]+$/.test(code)) {
    errors.code = 'Usa únicamente letras, números y guion bajo.'
  }

  if (model.validFrom && model.expiresOn && model.expiresOn < model.validFrom) {
    errors.expiresOn = 'El vencimiento no puede ser anterior al inicio de vigencia.'
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
  const toast = useToast()
  const { user } = useAuth()
  const permissions = useMemo(() => new Set(user?.permissions ?? []), [user])
  const [model, setModel] = useState<OrganizationPayload>(() => initialModel())
  const [loading, setLoading] = useState(editing)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [status, setStatus] = useState<OrganizationDetail['status']>()

  useEffect(() => {
    if (!publicId) return
    const controller = new AbortController()
    setLoading(true)
    setError('')

    getOrganization(publicId, controller.signal)
      .then((detail) => {
        setModel(detailToPayload(detail))
        setStatus(detail.status)
      })
      .catch((requestError: unknown) => {
        if (controller.signal.aborted) return
        setError(
          requestError instanceof ApiRequestError
            ? requestError.message
            : 'No fue posible cargar la organización.'
        )
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
    if (readOnly || saving || loading) return

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
      validFrom: model.validFrom || undefined,
      expiresOn: model.expiresOn || undefined
    }

    try {
      const saved = editing && publicId
        ? await updateOrganization(publicId, payload)
        : await createOrganization(payload)

      if (!editing) {
        navigate('/admin/organizations?created=1', { replace: true })
        return
      }

      setModel(detailToPayload(saved))
      setStatus(saved.status)
      toast.success(
        'Organización actualizada',
        'Los cambios de configuración y licenciamiento quedaron guardados.'
      )
    } catch (requestError: unknown) {
      if (requestError instanceof ApiRequestError) {
        setError(requestError.message)
        setFieldErrors(requestError.fieldErrors ?? {})
      } else {
        setError('No fue posible guardar la organización.')
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <main className="content-page org-editor-page">
      <button
        className="ns-back-button"
        type="button"
        onClick={() => navigate('/admin/organizations')}
      >
        ← Volver a organizaciones
      </button>

      <header className="ns-page-header org-editor-header">
        <div>
          <p className="eyebrow">Multiorganización</p>
          <h1>{readOnly ? 'Detalle de organización' : editing ? 'Editar organización' : 'Nueva organización'}</h1>
          <p className="muted">
            {readOnly
              ? 'Consulta la identidad, vigencia y política comercial configuradas para este tenant.'
              : 'Configura aislamiento, contenido, vigencia y base comercial del licenciamiento.'}
          </p>
        </div>
        {editing && status && (
          <span className={`status-badge status-${status.toLowerCase()}`}>
            {status === 'ACTIVE' ? 'Activa' : status === 'INACTIVE' ? 'Inactiva' : status === 'SUSPENDED' ? 'Suspendida' : status === 'EXPIRED' ? 'Vencida' : 'Eliminada'}
          </span>
        )}
      </header>

      {error && (
        <section className="inline-error-panel org-editor-error" role="alert">
          <div className="inline-error-icon"><Icon name="error" size={20} /></div>
          <div><strong>No fue posible completar la operación</strong><p>{error}</p></div>
        </section>
      )}

      {loading ? (
        <section className="ns-loading-card">Cargando configuración de la organización…</section>
      ) : (
        <form className="org-editor-form" noValidate onSubmit={submit}>
          <fieldset className="org-editor-fieldset" disabled={readOnly || saving}>
          <section className="ns-card">
            <div className="ns-card-heading">
              <div><span className="ns-step">1</span><h2>Información general</h2></div>
              <p>Identidad del tenant, modalidad de contenido y periodo de operación.</p>
            </div>

            <div className="org-form-grid">
              <label className="ns-field org-field-wide-tablet">
                <span>Nombre <b>*</b></span>
                <input
                  aria-invalid={Boolean(fieldErrors.name)}
                  autoComplete="organization"
                  maxLength={200}
                  value={model.name}
                  onChange={(event) => set('name', event.target.value)}
                />
                <FieldError message={fieldErrors.name} />
              </label>

              <label className="ns-field">
                <span>Código <b>*</b></span>
                <input
                  aria-invalid={Boolean(fieldErrors.code)}
                  disabled={editing}
                  maxLength={80}
                  placeholder="EJEMPLO_CORP"
                  spellCheck={false}
                  value={model.code ?? ''}
                  onChange={(event) => set('code', sanitizeCode(event.target.value))}
                />
                <small className="org-field-help">
                  Identificador único. Después de crear la organización no podrá modificarse.
                </small>
                <FieldError message={fieldErrors.code} />
              </label>

              <label className="ns-field">
                <span>Modalidad de contenido <b>*</b></span>
                <select
                  aria-invalid={Boolean(fieldErrors.contentMode)}
                  value={model.contentMode}
                  onChange={(event) => set('contentMode', event.target.value as ContentMode)}
                >
                  <option value="GLOBAL_CATALOG">Catálogo global completo</option>
                  <option value="CLEAN">Organización en limpio</option>
                  <option value="CUSTOM">Configuración personalizada</option>
                </select>
                <small className="org-field-help">{CONTENT_MODE_HELP[model.contentMode]}</small>
                <FieldError message={fieldErrors.contentMode} />
              </label>

              <label className="ns-field">
                <span>Vigencia desde</span>
                <input
                  aria-invalid={Boolean(fieldErrors.validFrom)}
                  type="date"
                  value={model.validFrom ?? ''}
                  onChange={(event) => set('validFrom', event.target.value || undefined)}
                />
                <FieldError message={fieldErrors.validFrom} />
              </label>

              <label className="ns-field">
                <span>Vencimiento</span>
                <input
                  aria-invalid={Boolean(fieldErrors.expiresOn)}
                  min={model.validFrom}
                  type="date"
                  value={model.expiresOn ?? ''}
                  onChange={(event) => set('expiresOn', event.target.value || undefined)}
                />
                <small className="org-field-help">Déjalo vacío para una vigencia indefinida.</small>
                <FieldError message={fieldErrors.expiresOn} />
              </label>
            </div>
          </section>

          <section className="ns-card">
            <div className="ns-card-heading">
              <div><span className="ns-step">2</span><h2>Plan y política de licenciamiento</h2></div>
              <p>
                Define la capacidad contratada y las reglas base que utilizará el módulo de licencias.
              </p>
            </div>

            <div className="org-form-grid org-license-grid">
              <label className="ns-field">
                <span>Asientos contratados <b>*</b></span>
                <input
                  aria-invalid={Boolean(fieldErrors.contractedSeats)}
                  min="0"
                  step="1"
                  type="number"
                  value={model.contractedSeats}
                  onChange={(event) => set('contractedSeats', Number(event.target.value))}
                />
                <FieldError message={fieldErrors.contractedSeats} />
              </label>

              <label className="ns-field">
                <span>Sustituciones incluidas <b>*</b></span>
                <input
                  aria-invalid={Boolean(fieldErrors.includedReplacements)}
                  min="0"
                  step="1"
                  type="number"
                  value={model.includedReplacements}
                  onChange={(event) => set('includedReplacements', Number(event.target.value))}
                />
                <FieldError message={fieldErrors.includedReplacements} />
              </label>

              <label className="ns-field">
                <span>Sustituciones adicionales <b>*</b></span>
                <input
                  aria-invalid={Boolean(fieldErrors.additionalReplacements)}
                  min="0"
                  step="1"
                  type="number"
                  value={model.additionalReplacements ?? 0}
                  onChange={(event) => set('additionalReplacements', Number(event.target.value))}
                />
                <FieldError message={fieldErrors.additionalReplacements} />
              </label>

              <label className="ns-field">
                <span>Liberación estándar (horas) <b>*</b></span>
                <input
                  aria-invalid={Boolean(fieldErrors.standardReleaseHours)}
                  min="1"
                  step="1"
                  type="number"
                  value={model.standardReleaseHours ?? 24}
                  onChange={(event) => set('standardReleaseHours', Number(event.target.value))}
                />
                <FieldError message={fieldErrors.standardReleaseHours} />
              </label>

              <label className="ns-field">
                <span>Bloqueo antifraude (días) <b>*</b></span>
                <input
                  aria-invalid={Boolean(fieldErrors.exhaustedReleaseDays)}
                  min="0"
                  step="1"
                  type="number"
                  value={model.exhaustedReleaseDays ?? 7}
                  onChange={(event) => set('exhaustedReleaseDays', Number(event.target.value))}
                />
                <FieldError message={fieldErrors.exhaustedReleaseDays} />
              </label>

              <label className="ns-field">
                <span>Inicio de ciclo <b>*</b></span>
                <input
                  aria-invalid={Boolean(fieldErrors.cycleStartsOn)}
                  type="date"
                  value={model.cycleStartsOn}
                  onChange={(event) => set('cycleStartsOn', event.target.value)}
                />
                <FieldError message={fieldErrors.cycleStartsOn} />
              </label>

              <label className="ns-field">
                <span>Fin de ciclo <b>*</b></span>
                <input
                  aria-invalid={Boolean(fieldErrors.cycleEndsOn)}
                  min={model.cycleStartsOn}
                  type="date"
                  value={model.cycleEndsOn}
                  onChange={(event) => set('cycleEndsOn', event.target.value)}
                />
                <FieldError message={fieldErrors.cycleEndsOn} />
              </label>
            </div>
          </section>
          </fieldset>

          <footer className="org-editor-actions">
            <button
              type="button"
              className="secondary-button"
              disabled={saving}
              onClick={() => navigate('/admin/organizations')}
            >
              {readOnly ? 'Volver' : 'Cancelar'}
            </button>
            {readOnly && publicId && permissions.has('ORGANIZATION_UPDATE') && status !== 'DELETED' && (
              <button
                type="button"
                className="primary-button"
                onClick={() => navigate(`/admin/organizations/${publicId}/edit`)}
              >
                Editar organización
              </button>
            )}
            {!readOnly && (
              <button type="submit" className="primary-button" disabled={saving}>
                {saving ? 'Guardando…' : editing ? 'Guardar cambios' : 'Guardar organización'}
              </button>
            )}
          </footer>
        </form>
      )}
    </main>
  )
}
