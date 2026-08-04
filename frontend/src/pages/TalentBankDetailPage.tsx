import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { downloadTalentCv, getTalent, getTalentCv, getTalentHistory, viewTalentCv } from '../features/talent-bank/api/talentBankApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { BackButton } from '../shared/components/BackButton'
import { LoadingScreen } from '../shared/components/LoadingScreen'
import { TablePagination } from '../shared/components/TablePagination'
import { useToast } from '../shared/components/ToastProvider'
import type { TalentCvMetadata, TalentHistoryPage, TalentSummary, TalentType } from '../shared/types/talentBank'

const typeLabels: Record<TalentType, string> = { ACADEMY: 'Academia', PROSPECT: 'Prospecto', BBVA_EXIT: 'Baja' }
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
  const toast = useToast()
  const [talent, setTalent] = useState<TalentSummary>()
  const [cv, setCv] = useState<TalentCvMetadata | null>(null)
  const [loading, setLoading] = useState(true)
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
    }).catch((requestError) => {
      if (active) setError(requestError instanceof ApiRequestError ? requestError.message : 'No fue posible cargar el talento.')
    }).finally(() => { if (active) setLoading(false) })
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

  async function viewCv() {
    if (!publicId) return
    try { await viewTalentCv(publicId) }
    catch (requestError) { toast.error('No fue posible ver el CV.', requestError instanceof Error ? requestError.message : undefined) }
  }

  async function downloadCv() {
    if (!publicId) return
    try { await downloadTalentCv(publicId) }
    catch (requestError) { toast.error('No fue posible descargar el CV.', requestError instanceof Error ? requestError.message : undefined) }
  }

  if (loading) return <LoadingScreen />
  if (!talent || error) return <main className="content-page"><BackButton fallback="/admin/talent-bank" /><div className="error-message">{error ?? 'El talento no existe.'}</div></main>

  return <main className="content-page editor-page talent-detail-page">
    <BackButton fallback="/admin/talent-bank" />
    <section className="editor-card">
      <div className="talent-detail-grid">
        <div><span>Persona</span><strong>{talent.displayName}</strong></div>
        <div><span>Correo</span><strong>{talent.email}</strong></div>
        <div><span>Código</span><strong>{talent.studentCode}</strong></div>
        <div><span>Organización</span><strong>{talent.organization.name} · {talent.organization.code}</strong></div>
        <div><span>Tipo</span><strong>{typeLabels[talent.talentType]}</strong></div>
        <div><span>Perfil</span><strong>{talent.profileCode ?? 'N/A'}</strong></div>
        <div><span>Tecnología</span><strong>{talent.technology?.name ?? 'N/A'}</strong></div>
        <div><span>Fecha de contratación</span><strong>{formatDate(talent.organizationHiredOn)}</strong></div>
        <div><span>Fecha de alta previa</span><strong>{formatDate(talent.admissionDate)}</strong></div>
        <div><span>Ingreso a Talent Bank</span><strong>{talent.movedAt ? new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(talent.movedAt)) : 'N/A'}</strong></div>
      </div>
    </section>
    <section className="editor-card">
      <div className="section-heading"><div><h2>Currículum vitae</h2></div></div>
      {cv ? <div className="talent-cv-row"><div><strong>{cv.fileName}</strong><small>{formatBytes(cv.fileSize)} · actualizado {new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium' }).format(new Date(cv.updatedAt))}</small></div><div className="talent-cv-actions"><button className="secondary-button" type="button" onClick={() => void viewCv()}>Ver CV</button><button className="secondary-button" type="button" onClick={() => void downloadCv()}>Descargar CV</button></div></div> : <p className="muted">N/A · No existe un CV registrado.</p>}
    </section>
    <section className="editor-card">
      <div className="section-heading"><div><h2>Historial</h2></div></div>
      <div className="ns-data-table-wrap"><table className="ns-data-table"><thead><tr><th>Evento</th><th>Descripción</th><th>Fecha</th></tr></thead><tbody>
        {historyLoading && <tr><td colSpan={3} className="ns-table-empty">Cargando historial…</td></tr>}
        {!historyLoading && history.content.length === 0 && <tr><td colSpan={3} className="ns-table-empty">Todavía no existen movimientos relevantes.</td></tr>}
        {!historyLoading && history.content.map((item) => <tr key={item.publicId}><td><strong>{item.eventType}</strong></td><td>{item.description}</td><td>{new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(item.occurredAt))}</td></tr>)}
      </tbody></table></div>
      <TablePagination compact currentPage={history.page} pageSize={history.size} totalElements={history.totalElements} totalPages={history.totalPages} isLoading={historyLoading} onPageChange={setHistoryPage} onPageSizeChange={(nextSize) => { setHistorySize(nextSize); setHistoryPage(0) }} />
    </section>
    <BackButton fallback="/admin/talent-bank" label="Regresar" />
  </main>
}
