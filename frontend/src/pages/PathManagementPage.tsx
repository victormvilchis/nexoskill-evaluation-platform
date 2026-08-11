import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { changePathStatus, getPath } from '../features/paths/api/pathApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { BackButton } from '../shared/components/BackButton'
import { ConfirmDialog } from '../shared/components/ConfirmDialog'
import { LoadingScreen } from '../shared/components/LoadingScreen'
import { useToast } from '../shared/components/ToastProvider'
import type { PathDetail, PathStatus } from '../shared/types/paths'

function label(status: PathStatus) { return ({ DRAFT: 'Borrador', ACTIVE: 'Activo', INACTIVE: 'Inactivo', ARCHIVED: 'Archivado' } as const)[status] }

export function PathManagementPage() {
  const { publicId = '' } = useParams()
  const toast = useToast()
  const [path, setPath] = useState<PathDetail>()
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()
  const [pending, setPending] = useState<PathStatus>()
  const load = useCallback(async () => {
    setLoading(true); setError(undefined)
    try { setPath(await getPath(publicId)) }
    catch (requestError) { setError(requestError instanceof ApiRequestError ? requestError.message : 'No fue posible consultar el Path.') }
    finally { setLoading(false) }
  }, [publicId])
  useEffect(() => { void load() }, [load])
  async function applyStatus() {
    if (!pending || busy) return
    setBusy(true)
    try {
      const updated = await changePathStatus(publicId, pending)
      setPath(updated); setPending(undefined)
      toast.success('Estado actualizado', `El Path ahora está ${label(updated.status).toLowerCase()}.`)
    } catch (requestError) { toast.error('No fue posible cambiar el estado', requestError instanceof ApiRequestError ? requestError.message : 'Intenta nuevamente.') }
    finally { setBusy(false) }
  }
  if (loading) return <LoadingScreen />
  if (!path || error) return <main className="content-page"><BackButton fallback="/admin/paths" /><section className="inline-error-panel" role="alert"><strong>No fue posible cargar el Path</strong><p>{error}</p></section></main>
  return <main className="content-page path-management-page"><BackButton fallback="/admin/paths" /><div className="lc-page-header lc-page-header-compact"><div><p className="eyebrow">Paths</p><h1>Administrar Path</h1><p>{path.name}</p></div></div>
    <section className="ns-card path-management-summary"><div><span>Estado actual</span><strong><span className={`status-badge status-${path.status.toLowerCase()}`}>{label(path.status)}</span></strong></div><div><span>Colecciones</span><strong>{path.collections.length}</strong></div><div><span>Formularios derivados</span><strong>{path.collections.reduce((total, item) => total + item.formCount, 0)}</strong></div><div><span>Alcance</span><strong>{path.contentScope === 'GLOBAL' ? 'Global' : path.organizationName}</strong></div></section>
    <section className="ns-card path-editor-card"><div className="ns-card-heading"><div><span className="ns-step">1</span><h2>Estado y disponibilidad</h2></div></div><p className="muted">Cambiar el estado no copia ni elimina Colecciones, Formularios, Preguntas, resultados o intentos históricos.</p><div className="student-management-actions">
      {path.status !== 'ACTIVE' && path.status !== 'ARCHIVED' && <button className="primary-button" type="button" onClick={() => setPending('ACTIVE')}>Activar</button>}
      {path.status === 'ACTIVE' && <button className="secondary-button" type="button" onClick={() => setPending('INACTIVE')}>Inactivar</button>}
      {path.status !== 'ARCHIVED' && <button className="secondary-button" type="button" onClick={() => setPending('ARCHIVED')}>Archivar</button>}
    </div></section>
    <ConfirmDialog open={Boolean(pending)} title={`Cambiar estado a ${pending ? label(pending) : ''}`} description="La relación y el historial se conservarán. La disponibilidad para nuevas actividades dependerá del estado resultante." confirmLabel="Confirmar cambio" busy={busy} onCancel={() => setPending(undefined)} onConfirm={() => void applyStatus()} />
  </main>
}
