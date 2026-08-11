import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { createPath } from '../features/paths/api/pathApi'
import { PathBuilder } from '../features/paths/components/PathBuilder'
import { ApiRequestError } from '../shared/api/apiClient'
import { BackButton } from '../shared/components/BackButton'
import { useToast } from '../shared/components/ToastProvider'
import { useSaveNavigation } from '../shared/hooks/useSaveNavigation'
import type { PathPayload } from '../shared/types/paths'

export function CreatePathPage() {
  const navigate = useNavigate()
  const toast = useToast()
  const completeSave = useSaveNavigation('/admin/paths')
  const [saving, setSaving] = useState(false)
  async function save(payload: PathPayload) {
    if (saving) return
    setSaving(true)
    try {
      await createPath(payload)
      completeSave({ title: 'Path creado correctamente.', message: 'La ruta quedó disponible para completar su configuración y activación.' })
    } catch (error) {
      toast.error('No fue posible crear el Path', error instanceof ApiRequestError ? error.message : 'Revisa la información e intenta nuevamente.')
    } finally { setSaving(false) }
  }
  return <main className="content-page path-editor-page"><BackButton fallback="/admin/paths" /><div className="lc-page-header lc-page-header-compact"><div><p className="eyebrow">Paths</p><h1>Nuevo Path</h1><p>Construye una ruta utilizando Colecciones existentes, sin duplicar su contenido.</p></div></div><PathBuilder saving={saving} submitLabel="Crear Path" onCancel={() => navigate('/admin/paths')} onSubmit={save} /></main>
}
