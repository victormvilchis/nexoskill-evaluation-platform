import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { getPath, updatePath } from '../features/paths/api/pathApi'
import { PathBuilder } from '../features/paths/components/PathBuilder'
import { ApiRequestError } from '../shared/api/apiClient'
import { BackButton } from '../shared/components/BackButton'
import { LoadingScreen } from '../shared/components/LoadingScreen'
import { useToast } from '../shared/components/ToastProvider'
import { useSaveNavigation } from '../shared/hooks/useSaveNavigation'
import type { PathDetail, PathPayload } from '../shared/types/paths'

export function PathDetailPage({ readOnly = false }: { readOnly?: boolean }) {
  const { publicId = '' } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const completeSave = useSaveNavigation('/admin/paths')
  const [path, setPath] = useState<PathDetail>()
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string>()
  useEffect(() => {
    let active = true
    setLoading(true); setError(undefined)
    getPath(publicId).then((response) => { if (active) setPath(response) }).catch((requestError: unknown) => {
      if (active) setError(requestError instanceof ApiRequestError ? requestError.message : 'No fue posible consultar el Path.')
    }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [publicId])
  async function save(payload: PathPayload) {
    if (saving) return
    setSaving(true)
    try {
      await updatePath(publicId, payload)
      completeSave({ title: 'Path actualizado correctamente.', message: 'La composición y el orden de las Colecciones quedaron guardados.' })
    } catch (requestError) {
      toast.error('No fue posible actualizar el Path', requestError instanceof ApiRequestError ? requestError.message : 'Revisa la información e intenta nuevamente.')
    } finally { setSaving(false) }
  }
  if (loading) return <LoadingScreen />
  if (!path || error) return <main className="content-page"><BackButton fallback="/admin/paths" /><section className="inline-error-panel" role="alert"><div><strong>No fue posible cargar el Path</strong><p>{error ?? 'El Path solicitado no está disponible.'}</p></div></section></main>
  return <main className="content-page path-editor-page"><BackButton fallback="/admin/paths" /><div className="lc-page-header lc-page-header-compact"><div><p className="eyebrow">Paths</p><h1>{readOnly ? 'Ver Path' : 'Editar Path'}</h1><p>{path.name} · {path.contentScope === 'GLOBAL' ? 'Global' : path.organizationName}</p></div></div><PathBuilder initial={path} saving={saving} readOnly={readOnly} submitLabel="Guardar cambios" onCancel={() => navigate('/admin/paths')} onSubmit={save} /></main>
}
