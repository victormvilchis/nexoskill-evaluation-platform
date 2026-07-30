import { FormEvent, useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ApiRequestError, apiClient } from '../shared/api/apiClient'
import { BackButton } from '../shared/components/BackButton'
import { useSaveNavigation } from '../shared/hooks/useSaveNavigation'
import type { FormDetail, FormMode, FormPayload } from '../shared/types/forms'

const initial: FormPayload = {
  title: '', description: '', modeCode: 'ASSESSMENT', passingScore: 80,
  retryUntilPassed: true, acceptResponses: false, showResults: true,
  showCorrectAnswers: false, randomizeQuestions: false, randomizeOptions: false,
  showProgress: true, hideQuestionNumbers: false, allowSaveResume: true,
  oneActiveAttempt: true, thankYouMessage: 'Gracias por completar el formulario.', sections: []
}

function Toggle({ checked, onChange, title, description, disabled = false }: { checked: boolean; onChange: (value: boolean) => void; title: string; description?: string; disabled?: boolean }) {
  return <label className="ns-toggle-row">
    <input type="checkbox" checked={checked} disabled={disabled} onChange={e => onChange(e.target.checked)} />
    <span className="ns-toggle-control" aria-hidden="true" />
    <span className="ns-toggle-copy"><strong>{title}</strong>{description && <small>{description}</small>}</span>
  </label>
}

export function FormBuilderPage({ readOnly = false }: { readOnly?: boolean }) {
  const { id } = useParams()
  const editing = Boolean(id)
  const navigate = useNavigate()
  const completeSave = useSaveNavigation('/admin/forms')
  const [model, setModel] = useState<FormPayload>(initial)
  const [saving, setSaving] = useState(false)
  const [loading, setLoading] = useState(editing)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!id) return
    setLoading(true)
    apiClient.get<FormDetail>(`/api/v1/admin/forms/${id}`)
      .then((form: FormDetail) => setModel({ ...form, description: form.description ?? '', thankYouMessage: form.thankYouMessage ?? '', sections: form.sections ?? [] }))
      .catch(() => setError('No fue posible cargar el formulario.'))
      .finally(() => setLoading(false))
  }, [id])

  const readiness = useMemo(() => {
    const issues: string[] = []
    if (model.title.trim().length < 3) issues.push('Agrega un título de al menos 3 caracteres.')
    if (model.passingScore < 0 || model.passingScore > 100) issues.push('El puntaje mínimo debe estar entre 0 y 100.')
    if (model.durationMinutes !== undefined && model.durationMinutes < 1) issues.push('La duración debe ser mayor a cero.')
    return issues
  }, [model])

  function set<K extends keyof FormPayload>(key: K, value: FormPayload[K]) {
    setModel(current => ({ ...current, [key]: value }))
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (readOnly) return
    if (saving) return
    const firstReadinessError = readiness[0]

    if (firstReadinessError) {
      setError(firstReadinessError)
      return
    }

    setSaving(true)
    setError('')
    try {
      if (editing) {
        await apiClient.put<FormDetail>(`/api/v1/admin/forms/${id}`, model)
        completeSave({ title: 'Formulario actualizado correctamente.' })
      } else {
        await apiClient.post<FormDetail>('/api/v1/admin/forms', model)
        completeSave({ title: 'Formulario creado correctamente.' })
      }
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError
        ? requestError.message
        : 'No fue posible guardar el formulario. Revisa la configuración e intenta nuevamente.')
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <main className="content-page"><div className="ns-loading-card">Cargando formulario…</div></main>

  if (readOnly) {
    return <main className="content-page ns-form-builder">
      <BackButton fallback="/admin/forms" label="Volver a formularios" />
      <header className="ns-page-header"><div><p className="eyebrow">Formularios</p><h1>Ver formulario</h1><p className="muted">Consulta la configuración sin modificarla.</p></div></header>
      {error && <div className="ns-inline-alert" role="alert"><strong>No fue posible abrir el formulario</strong><span>{error}</span></div>}
      {!error && <div className="ns-builder-layout">
        <div className="ns-builder-main">
          <section className="ns-card"><div className="ns-card-heading"><h2>Información general</h2></div>
            <div className="catalog-readonly-grid">
              <div><span>Título</span><strong>{model.title}</strong></div>
              <div><span>Modalidad</span><strong>{model.modeCode === 'PRACTICE' ? 'Práctica' : 'Evaluación'}</strong></div>
              <div><span>Puntaje mínimo</span><strong>{model.passingScore}%</strong></div>
              <div className="catalog-readonly-wide"><span>Descripción</span><strong>{model.description || 'Sin descripción'}</strong></div>
            </div>
          </section>
          <section className="ns-card"><div className="ns-card-heading"><h2>Contenido</h2></div><p className="muted">{model.sections.length} {model.sections.length === 1 ? 'sección configurada' : 'secciones configuradas'}.</p></section>
        </div>
        <aside className="ns-builder-sidebar"><section className="ns-card ns-settings-card"><div className="ns-card-heading"><h2>Configuración</h2></div>
          <div className="catalog-readonly-grid">
            <div><span>Aceptar respuestas</span><strong>{model.acceptResponses ? 'Sí' : 'No'}</strong></div>
            <div><span>Reintentar hasta aprobar</span><strong>{model.retryUntilPassed ? 'Sí' : 'No'}</strong></div>
            <div><span>Mostrar resultados</span><strong>{model.showResults ? 'Sí' : 'No'}</strong></div>
            <div><span>Mostrar respuestas correctas</span><strong>{model.showCorrectAnswers ? 'Sí' : 'No'}</strong></div>
            <div><span>Duración</span><strong>{model.durationMinutes ? `${model.durationMinutes} min` : 'Sin límite'}</strong></div>
            <div><span>Intentos máximos</span><strong>{model.retryUntilPassed ? 'Hasta aprobar' : model.maxAttempts ?? 'Sin límite'}</strong></div>
          </div>
        </section></aside>
      </div>}
    </main>
  }

  return <main className="content-page ns-form-builder">
    <BackButton fallback="/admin/forms" label="Volver a formularios" />
    <header className="ns-page-header">
      <div><p className="eyebrow">Constructor de formularios</p><h1>{editing ? 'Editar formulario' : 'Nuevo formulario'}</h1><p className="muted">Configura la experiencia, reglas de aprobación y disponibilidad.</p></div>
      <div className="ns-header-actions">
        <button className="secondary-button" type="button" onClick={() => navigate('/admin/forms')}>Cancelar</button>
        <button className="primary-button" disabled={saving || readiness.length > 0} form="form-builder" type="submit">{saving ? 'Guardando…' : 'Guardar formulario'}</button>
      </div>
    </header>
    {error && <div className="ns-inline-alert" role="alert"><strong>Revisa el formulario</strong><span>{error}</span></div>}
    <form id="form-builder" onSubmit={submit}>
      <div className="ns-builder-layout">
        <div className="ns-builder-main">
          <section className="ns-card">
            <div className="ns-card-heading"><div><span className="ns-step">1</span><h2>Información general</h2></div><p>Define cómo se identificará el formulario.</p></div>
            <div className="ns-form-grid">
              <label className="ns-field ns-field-wide"><span>Título <b>*</b></span><input required minLength={3} value={model.title} onChange={e => set('title', e.target.value)} placeholder="Ej. Certificación APX — Nivel 1" /><small>Usa un nombre claro para administradores y participantes.</small></label>
              <label className="ns-field ns-field-wide"><span>Descripción</span><textarea rows={4} value={model.description} onChange={e => set('description', e.target.value)} placeholder="Describe el objetivo y alcance del formulario." /></label>
              <label className="ns-field"><span>Modalidad</span><select value={model.modeCode} onChange={e => set('modeCode', e.target.value as FormMode)}><option value="ASSESSMENT">Evaluación</option><option value="PRACTICE">Práctica</option></select><small>{model.modeCode === 'PRACTICE' ? 'Permite aprender y recibir retroalimentación.' : 'Califica el desempeño al finalizar.'}</small></label>
              <label className="ns-field"><span>Puntaje mínimo</span><div className="ns-input-suffix"><input type="number" min="0" max="100" value={model.passingScore} onChange={e => set('passingScore', Number(e.target.value))} /><span>%</span></div><small>Porcentaje requerido para aprobar.</small></label>
            </div>
          </section>
          <section className="ns-card">
            <div className="ns-card-heading ns-card-heading-row"><div><div><span className="ns-step">2</span><h2>Contenido</h2></div><p>Organiza secciones, preguntas, colecciones y pools aleatorios.</p></div><button className="secondary-button" disabled={!editing} type="button">+ Agregar sección</button></div>
            <div className="ns-builder-empty"><div className="ns-empty-icon">▤</div><h3>{editing ? 'Agrega la primera sección' : 'Guarda primero la configuración general'}</h3><p>{editing ? 'En la siguiente entrega podrás incorporar preguntas, colecciones y pools.' : 'Después podrás estructurar el contenido del formulario.'}</p></div>
          </section>
        </div>
        <aside className="ns-builder-sidebar">
          <section className="ns-card ns-settings-card">
            <div className="ns-card-heading"><h2>Configuración</h2><p>Los cambios se aplican al guardar.</p></div>
            <div className="ns-settings-group"><h3>Disponibilidad</h3><Toggle checked={model.acceptResponses} onChange={v => set('acceptResponses', v)} title="Aceptar respuestas" description="Habilita el acceso de participantes cuando el formulario esté activo." /></div>
            <div className="ns-settings-group"><h3>Calificación e intentos</h3>
              <Toggle checked={model.retryUntilPassed} onChange={v => set('retryUntilPassed', v)} title="Reintentar hasta aprobar" />
              <Toggle checked={model.showResults} onChange={v => set('showResults', v)} title="Mostrar resultados automáticamente" />
              <Toggle checked={model.showCorrectAnswers} onChange={v => set('showCorrectAnswers', v)} title="Mostrar respuestas correctas" />
              <div className="ns-inline-fields"><label className="ns-field"><span>Duración</span><div className="ns-input-suffix"><input type="number" min="1" value={model.durationMinutes ?? ''} onChange={e => set('durationMinutes', e.target.value ? Number(e.target.value) : undefined)} /><span>min</span></div></label><label className="ns-field"><span>Intentos máximos</span><input type="number" min="1" disabled={model.retryUntilPassed} value={model.maxAttempts ?? ''} onChange={e => set('maxAttempts', e.target.value ? Number(e.target.value) : undefined)} /></label></div>
            </div>
            <div className="ns-settings-group"><h3>Presentación</h3>
              <Toggle checked={model.randomizeQuestions} onChange={v => set('randomizeQuestions', v)} title="Orden aleatorio de preguntas" />
              <Toggle checked={model.randomizeOptions} onChange={v => set('randomizeOptions', v)} title="Orden aleatorio de opciones" />
              <Toggle checked={model.showProgress} onChange={v => set('showProgress', v)} title="Mostrar barra de progreso" />
              <Toggle checked={model.hideQuestionNumbers} onChange={v => set('hideQuestionNumbers', v)} title="Ocultar número de pregunta" />
              <Toggle checked={model.allowSaveResume} onChange={v => set('allowSaveResume', v)} title="Guardar y continuar después" />
            </div>
            <div className="ns-settings-group"><label className="ns-field"><span>Mensaje de agradecimiento</span><textarea rows={5} value={model.thankYouMessage} onChange={e => set('thankYouMessage', e.target.value)} /></label></div>
          </section>
        </aside>
      </div>
    </form>
  </main>
}
