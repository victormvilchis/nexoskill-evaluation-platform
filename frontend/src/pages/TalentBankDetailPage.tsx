import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useAuth } from '../features/authentication/context/AuthContext'
import { downloadTalentCv, getTalent, getTalentCv, getTalentHistory, uploadTalentCv } from '../features/talent-bank/api/talentBankApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { BackButton } from '../shared/components/BackButton'
import { Icon } from '../shared/components/Icon'
import { LoadingScreen } from '../shared/components/LoadingScreen'
import { TablePagination } from '../shared/components/TablePagination'
import { useToast } from '../shared/components/ToastProvider'
import type { TalentCvMetadata, TalentHistoryPage, TalentSummary, TalentType } from '../shared/types/talentBank'

const typeLabels: Record<TalentType, string> = { ACADEMY: 'Academia', PROSPECT: 'Prospecto', BBVA_EXIT: 'Baja de BBVA' }
function formatDate(value?: string | null) {
  return value ? new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium' }).format(new Date(`${value}T12:00:00`)) : 'N/A'
}
function formatBytes(value: number) {
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / (1024 * 1024)).toFixed(1)} MB`
}

export function TalentBankDetailPage() {
  const { publicId } = useParams()
  const navigate = useNavigate()
  const { user } = useAuth()
  const permissions = new Set(user?.permissions ?? [])
  const canUpdate = permissions.has('STUDENT_UPDATE')
  const canConvert = permissions.has('STUDENT_CREATE') && permissions.has('STUDENT_UPDATE')
  const toast = useToast()
  const [talent, setTalent] = useState<TalentSummary>()
  const [cv, setCv] = useState<TalentCvMetadata | null>(null)
  const [loading, setLoading] = useState(true)
  const [uploading, setUploading] = useState(false)
  const [history, setHistory] = useState<TalentHistoryPage>({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 })
  const [historyPage, setHistoryPage] = useState(0)
  const [historySize, setHistorySize] = useState(10)
  const [historyLoading, setHistoryLoading] = useState(false)
  const [error, setError] = useState<string>()

  useEffect(() => {
    if (!publicId) return
    let active = true
    Promise.all([getTalent(publicId), getTalentCv(publicId)]).then(([detail, metadata]) => {
      if (active) { setTalent(detail); setCv(metadata) }
    }).catch((requestError) => { if (active) setError(requestError instanceof ApiRequestError ? requestError.message : 'No fue posible cargar el talento.') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [publicId])

  useEffect(() => {
    if (!publicId) return
    const controller = new AbortController()
    setHistoryLoading(true)
    getTalentHistory(publicId, historyPage, historySize, controller.signal)
      .then((result) => setHistory({ ...result, content: result.content ?? [] }))
      .catch(() => { if (!controller.signal.aborted) setHistory((current) => ({ ...current, content: [] })) })
      .finally(() => { if (!controller.signal.aborted) setHistoryLoading(false) })
    return () => controller.abort()
  }, [historyPage, historySize, publicId])

  async function upload(file?: File) {
    if (!file || !publicId || uploading) return
    setUploading(true)
    try { setCv(await uploadTalentCv(publicId, file)); toast.success('El CV se guardó correctamente.') }
    catch (requestError) { toast.error('No fue posible guardar el CV.', requestError instanceof ApiRequestError ? requestError.message : undefined) }
    finally { setUploading(false) }
  }

  if (loading) return <LoadingScreen />
  if (!talent || error) return <main className="content-page"><BackButton fallback="/admin/talent-bank" /><div className="error-message">{error ?? 'El talento no existe.'}</div></main>
  return <main className="content-page editor-page talent-detail-page">
    <BackButton fallback="/admin/talent-bank" />
    <div className="ns-list-action-bar">
      {canUpdate && <Link className="secondary-button button-link" to={`/admin/talent-bank/${talent.publicId}/edit`}><Icon name="edit" size={15} /> Editar</Link>}
      {canConvert && talent.talentType !== 'BBVA_EXIT' && <Link className="primary-button button-link" to={`/admin/talent-bank/${talent.publicId}/convert`}><Icon name="chevronRight" size={15} /> Convertir a colaborador</Link>}
    </div>
    <section className="editor-card"><div className="section-heading"><div><p className="eyebrow">Talent Bank</p><h1>{talent.displayName}</h1><p className="muted">{typeLabels[talent.talentType]} · {talent.organization.name}</p></div></div>
      <div className="talent-detail-grid">
        <div><span>Correo</span><strong>{talent.email}</strong></div><div><span>Código</span><strong>{talent.studentCode}</strong></div>
        <div><span>Organización</span><strong>{talent.organization.name} · {talent.organization.code}</strong></div><div><span>Tipo</span><strong>{typeLabels[talent.talentType]}</strong></div>
        <div><span>Perfil</span><strong>{talent.profileCode ?? 'N/A'}</strong></div><div><span>Tecnología</span><strong>{talent.technology?.name ?? 'N/A'}</strong></div>
        <div><span>Inicio de vigencia</span><strong>{formatDate(talent.validFrom)}</strong></div><div><span>Vencimiento</span><strong>{formatDate(talent.expiresAt)}</strong></div>
        <div><span>Contratación en organización</span><strong>{formatDate(talent.organizationHiredOn)}</strong></div><div><span>Traslado a Talent Bank</span><strong>{talent.movedAt ? new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(talent.movedAt)) : 'N/A'}</strong></div>
      </div>
    </section>
    <section className="editor-card"><div className="section-heading"><div><p className="eyebrow">Documentación</p><h2>Currículum vitae</h2></div></div>
      {cv ? <div className="talent-cv-row"><div><strong>{cv.fileName}</strong><small>{formatBytes(cv.fileSize)} · actualizado {new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium' }).format(new Date(cv.updatedAt))}</small></div><button className="secondary-button" type="button" onClick={() => void downloadTalentCv(talent.publicId)}>Descargar</button></div> : <p className="muted">No existe un CV registrado.</p>}
      {canUpdate && <label className="form-field"><span>{cv ? 'Sustituir CV' : 'Cargar CV'}</span><input type="file" accept=".pdf,.doc,.docx,.ppt,.pptx" disabled={uploading} onChange={(event) => void upload(event.target.files?.[0])} /><small>PDF, Word o PowerPoint; máximo 15 MB.</small></label>}
    </section>
    <section className="editor-card"><div className="section-heading"><div><p className="eyebrow">Historial</p><h2>Movimientos relevantes</h2></div></div>
      <div className="ns-data-table-wrap"><table className="ns-data-table"><thead><tr><th>Evento</th><th>Descripción</th><th>Fecha</th></tr></thead><tbody>
        {historyLoading && <tr><td colSpan={3} className="ns-table-empty">Cargando historial…</td></tr>}
        {!historyLoading && history.content.length === 0 && <tr><td colSpan={3} className="ns-table-empty">Todavía no existen movimientos relevantes.</td></tr>}
        {!historyLoading && history.content.map((item) => <tr key={item.publicId}><td><strong>{item.eventType}</strong></td><td>{item.description}</td><td>{new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(item.occurredAt))}</td></tr>)}
      </tbody></table></div>
      <TablePagination compact currentPage={history.page} pageSize={history.size} totalElements={history.totalElements} totalPages={history.totalPages} isLoading={historyLoading} onPageChange={setHistoryPage} onPageSizeChange={(nextSize) => { setHistorySize(nextSize); setHistoryPage(0) }} />
    </section>
    <button className="secondary-button" type="button" onClick={() => navigate('/admin/talent-bank')}>Volver al listado</button>
  </main>
}
