import { FormEvent, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { createOrganization, getOrganization, updateOrganization } from '../features/organizations/api/organizationApi'
import type { ContentMode, OrganizationPayload } from '../features/organizations/types/organizations'

function nextMonth(date: string) {
  const value = new Date(`${date}T00:00:00`); value.setMonth(value.getMonth() + 1); return value.toISOString().slice(0, 10)
}

export function OrganizationEditorPage() {
  const { publicId } = useParams()
  const editing = Boolean(publicId)
  const navigate = useNavigate()
  const today = new Date().toISOString().slice(0, 10)
  const [model, setModel] = useState<OrganizationPayload>({ name: '', code: '', contentMode: 'CLEAN', contractedSeats: 10, includedReplacements: 2, additionalReplacements: 0, standardReleaseHours: 24, exhaustedReleaseDays: 7, cycleStartsOn: today, cycleEndsOn: nextMonth(today) })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!publicId) return
    getOrganization(publicId).then(detail => setModel({ ...detail })).catch(() => setError('No fue posible cargar la organización.'))
  }, [publicId])

  function set<K extends keyof OrganizationPayload>(key: K, value: OrganizationPayload[K]) { setModel(current => ({ ...current, [key]: value })) }

  async function submit(event: FormEvent) {
    event.preventDefault(); setSaving(true); setError('')
    try {
      const saved = editing && publicId ? await updateOrganization(publicId, model) : await createOrganization(model)
      navigate(`/admin/organizations/${saved.publicId}/edit`, { replace: true })
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'No fue posible guardar la organización.') }
    finally { setSaving(false) }
  }

  return <main className="ns-resource-page org-editor-page">
    <button className="ns-back-button" type="button" onClick={() => navigate('/admin/organizations')}>← Volver a organizaciones</button>
    <header className="ns-page-header"><div><span className="eyebrow">MULTIORGANIZACIÓN</span><h1>{editing ? 'Editar organización' : 'Nueva organización'}</h1><p>Configura aislamiento, contenido, vigencia y base comercial del licenciamiento.</p></div></header>
    {error && <div className="state-card error-state">{error}</div>}
    <form className="org-editor-grid" onSubmit={submit}>
      <section className="ns-card"><h2>Información general</h2><div className="org-form-grid"><label><span>Nombre</span><input required value={model.name} onChange={e => set('name', e.target.value)}/></label><label><span>Código</span><input required={!editing} disabled={editing} value={model.code ?? ''} onChange={e => set('code', e.target.value)}/></label><label><span>Modalidad de contenido</span><select value={model.contentMode} onChange={e => set('contentMode', e.target.value as ContentMode)}><option value="GLOBAL_CATALOG">Catálogo global completo</option><option value="CLEAN">Organización en limpio</option><option value="CUSTOM">Configuración personalizada</option></select></label><label><span>Vigencia desde</span><input type="date" value={model.validFrom ?? ''} onChange={e => set('validFrom', e.target.value || undefined)}/></label><label><span>Vencimiento</span><input type="date" value={model.expiresOn ?? ''} onChange={e => set('expiresOn', e.target.value || undefined)}/></label></div></section>
      <section className="ns-card"><h2>Plan y política de licenciamiento</h2><p>La operación completa de asientos se implementará en Parte 3; estos campos fijan el contrato desde el núcleo.</p><div className="org-form-grid"><label><span>Asientos contratados</span><input type="number" min="0" value={model.contractedSeats} onChange={e => set('contractedSeats', Number(e.target.value))}/></label><label><span>Sustituciones incluidas</span><input type="number" min="0" value={model.includedReplacements} onChange={e => set('includedReplacements', Number(e.target.value))}/></label><label><span>Sustituciones adicionales</span><input type="number" min="0" value={model.additionalReplacements ?? 0} onChange={e => set('additionalReplacements', Number(e.target.value))}/></label><label><span>Liberación estándar (horas)</span><input type="number" min="1" value={model.standardReleaseHours ?? 24} onChange={e => set('standardReleaseHours', Number(e.target.value))}/></label><label><span>Bloqueo antifraude (días)</span><input type="number" min="0" value={model.exhaustedReleaseDays ?? 7} onChange={e => set('exhaustedReleaseDays', Number(e.target.value))}/></label><label><span>Inicio de ciclo</span><input type="date" required value={model.cycleStartsOn} onChange={e => set('cycleStartsOn', e.target.value)}/></label><label><span>Fin de ciclo</span><input type="date" required value={model.cycleEndsOn} onChange={e => set('cycleEndsOn', e.target.value)}/></label></div></section>
      <footer className="org-editor-actions"><button type="button" className="secondary-button" onClick={() => navigate('/admin/organizations')}>Cancelar</button><button type="submit" className="primary-button" disabled={saving}>{saving ? 'Guardando…' : 'Guardar organización'}</button></footer>
    </form>
  </main>
}
