import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../features/authentication/context/AuthContext'
import { searchOrganizations } from '../features/organizations/api/organizationApi'
import type { OrganizationSummary } from '../features/organizations/types/organizations'
import { applyStudentImport, discardStudentImport, previewStudentImport } from '../features/students/api/studentImportApi'
import type {
  ChangedStudentPreview,
  NewStudentPreview,
  StudentImportApplyResult,
  StudentImportCredential,
  StudentImportPreview
} from '../features/students/types/studentImport'
import { ApiRequestError } from '../shared/api/apiClient'
import { BackButton } from '../shared/components/BackButton'
import { useToast } from '../shared/components/ToastProvider'

type LowAction = 'KEEP' | 'DEACTIVATE' | 'IGNORE'

type NewState = NewStudentPreview & { selected: boolean; email: string }
type ChangeState = ChangedStudentPreview & { selectedFields: Set<string> }

function validEmail(value: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim())
}

function credentialText(value: StudentImportCredential) {
  return `Organización: ${value.organizationCode || value.organization}\nCorreo: ${value.email}\nContraseña temporal: ${value.temporaryPassword}`
}

async function copyText(value: string) {
  await navigator.clipboard.writeText(value)
}

export function StudentImportPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { user } = useAuth()
  const toast = useToast()
  const administrator = Boolean(user?.roles.includes('ADMINISTRATOR'))
  const inputRef = useRef<HTMLInputElement>(null)
  const [organizations, setOrganizations] = useState<OrganizationSummary[]>([])
  const [organizationsLoading, setOrganizationsLoading] = useState(false)
  const [selectedOrganization, setSelectedOrganization] = useState(searchParams.get('organization') ?? '')
  const [file, setFile] = useState<File>()
  const [preview, setPreview] = useState<StudentImportPreview>()
  const [newRows, setNewRows] = useState<NewState[]>([])
  const [changedRows, setChangedRows] = useState<ChangeState[]>([])
  const [lowActions, setLowActions] = useState<Record<string, LowAction>>({})
  const [loading, setLoading] = useState(false)
  const [applying, setApplying] = useState(false)
  const [error, setError] = useState<string>()
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [result, setResult] = useState<StudentImportApplyResult>()


  useEffect(() => {
    if (!administrator) return
    const controller = new AbortController()
    setOrganizationsLoading(true)
    searchOrganizations({ status: 'ACTIVE', page: 0, size: 100, signal: controller.signal })
      .then((response) => setOrganizations(response.content.filter((item) => item.organizationType === 'CUSTOMER')))
      .catch(() => { if (!controller.signal.aborted) setOrganizations([]) })
      .finally(() => { if (!controller.signal.aborted) setOrganizationsLoading(false) })
    return () => controller.abort()
  }, [administrator])

  const studentsPath = administrator && selectedOrganization
    ? `/admin/students?organization=${encodeURIComponent(selectedOrganization)}`
    : '/admin/students'

  useEffect(() => () => {
    if (preview?.token && !result) void discardStudentImport(preview.token).catch(() => undefined)
  }, [preview?.token, result])

  const selectedNew = useMemo(() => newRows.filter((row) => row.selected), [newRows])
  const selectedChanges = useMemo(
    () => changedRows.filter((row) => row.selectedFields.size > 0),
    [changedRows]
  )
  const selectedLows = useMemo(
    () => Object.values(lowActions).filter((action) => action === 'DEACTIVATE').length,
    [lowActions]
  )
  const emailError = useMemo(() => {
    const normalized = new Map<string, number>()
    for (const row of selectedNew) {
      const email = row.email.trim().toLowerCase()
      if (!validEmail(email)) return `Captura un correo válido para ${row.collaborator}.`
      normalized.set(email, (normalized.get(email) ?? 0) + 1)
    }
    const duplicate = [...normalized.entries()].find(([, count]) => count > 1)
    return duplicate ? `El correo ${duplicate[0]} está repetido entre los colaboradores nuevos.` : undefined
  }, [selectedNew])

  async function analyze() {
    if (!file || loading) return
    setLoading(true)
    setError(undefined)
    setResult(undefined)
    try {
      if (administrator && !selectedOrganization) {
        setError('Selecciona la organización que recibirá la carga antes de analizar el archivo.')
        return
      }
      const response = await previewStudentImport(file, administrator ? selectedOrganization : undefined)
      setPreview(response)
      setNewRows(response.newStudents.map((row) => ({
        ...row,
        selected: true,
        email: row.suggestedEmail ?? ''
      })))
      setChangedRows(response.changedStudents.map((row) => ({
        ...row,
        selectedFields: new Set(row.changes.filter((change) => change.selected).map((change) => change.key))
      })))
      setLowActions(Object.fromEntries(response.possibleLows.map((row) => [row.studentPublicId, 'KEEP'])))
    } catch (requestError) {
      setPreview(undefined)
      setError(requestError instanceof ApiRequestError
        ? requestError.message
        : 'No fue posible analizar el archivo.')
    } finally {
      setLoading(false)
    }
  }

  async function changeOrganization(next: string) {
    if (preview?.token && !result) await discardStudentImport(preview.token).catch(() => undefined)
    setSelectedOrganization(next)
    setPreview(undefined)
    setNewRows([])
    setChangedRows([])
    setLowActions({})
    setResult(undefined)
    setError(undefined)
  }

  async function changeFile(next?: File) {
    if (preview?.token && !result) await discardStudentImport(preview.token).catch(() => undefined)
    setFile(next)
    setPreview(undefined)
    setNewRows([])
    setChangedRows([])
    setLowActions({})
    setResult(undefined)
    setError(undefined)
  }

  async function applyChanges() {
    if (!preview || applying || emailError) return
    setApplying(true)
    setError(undefined)
    try {
      const response = await applyStudentImport({
        token: preview.token,
        newStudents: newRows.map((row) => ({ rowKey: row.rowKey, email: row.email.trim(), selected: row.selected })),
        changedStudents: changedRows.map((row) => ({
          studentPublicId: row.studentPublicId,
          fields: [...row.selectedFields]
        })),
        possibleLows: preview.possibleLows.map((row) => ({
          studentPublicId: row.studentPublicId,
          action: lowActions[row.studentPublicId] ?? 'KEEP'
        }))
      })
      setResult(response)
      setConfirmOpen(false)
      toast.success('Importación aplicada', `Se crearon ${response.created} y se actualizaron ${response.updated} colaboradores.`)
    } catch (requestError) {
      setConfirmOpen(false)
      setError(requestError instanceof ApiRequestError
        ? requestError.message
        : 'No fue posible aplicar la importación.')
    } finally {
      setApplying(false)
    }
  }

  function closeResult() {
    setResult(undefined)
    navigate(studentsPath, { replace: true })
  }

  if (result) {
    const all = result.credentials.map(credentialText).join('\n\n')
    return (
      <main className="content-page student-import-page">
        <header className="page-heading compact"><div><p className="eyebrow">Colaboradores</p><h1>Resultado de la importación</h1></div></header>
        <section className="ns-import-summary-grid">
          <article><strong>{result.created}</strong><span>Creados</span></article>
          <article><strong>{result.updated}</strong><span>Actualizados</span></article>
          <article><strong>{result.possibleLowsProcessed}</strong><span>Desactivados</span></article>
          <article><strong>{result.errors.length}</strong><span>Errores</span></article>
        </section>
        {result.errors.length > 0 && <section className="editor-card"><h2>Errores al aplicar</h2>
          <ul className="ns-import-issues">{result.errors.map((issue, index) => <li key={`${issue.code}-${index}`}>{issue.message}</li>)}</ul>
        </section>}
        {result.credentials.length > 0 && <section className="editor-card ns-credential-once">
          <div className="section-heading"><div><p className="eyebrow">Accesos generados</p><h2>Credenciales temporales</h2></div></div>
          <div className="warning-message" role="alert">{result.credentialsNotice}</div>
          <div className="ns-import-copy-actions">
            <button type="button" className="secondary-button" onClick={() => void copyText(all)}>Copiar todos los accesos</button>
            <button type="button" className="secondary-button" onClick={() => void copyText(result.credentials.map((item) => item.email).join('\n'))}>Copiar correos</button>
            <button type="button" className="secondary-button" onClick={() => void copyText(result.credentials.map((item) => item.temporaryPassword).join('\n'))}>Copiar contraseñas</button>
          </div>
          <div className="ns-data-table-wrap"><table className="ns-data-table"><thead><tr><th>Organización</th><th>Colaborador</th><th>Correo</th><th>Contraseña temporal</th><th>Acciones</th></tr></thead><tbody>
            {result.credentials.map((credential) => <tr key={credential.email}><td>{credential.organization}<button type="button" className="link-button compact-copy" onClick={() => void copyText(credential.organizationCode || credential.organization)}>Copiar</button></td><td>{credential.collaborator}</td><td>{credential.email}<button type="button" className="link-button compact-copy" onClick={() => void copyText(credential.email)}>Copiar</button></td><td><code>{credential.temporaryPassword}</code><button type="button" className="link-button compact-copy" onClick={() => void copyText(credential.temporaryPassword)}>Copiar</button></td><td><button type="button" className="secondary-button compact-button" onClick={() => void copyText(credentialText(credential))}>Copiar credenciales</button></td></tr>)}
          </tbody></table></div>
        </section>}
        <div className="form-actions"><button type="button" className="primary-button" onClick={closeResult}>Cerrar resultado</button></div>
      </main>
    )
  }

  return (
    <main className="content-page student-import-page">
      <BackButton fallback={studentsPath} />
      <header className="page-heading compact"><div><p className="eyebrow">Colaboradores</p><h1>Cargar Excel</h1><p className="muted">Se procesará exclusivamente la primera hoja del archivo. El Excel no se guarda y ningún cambio se aplica sin confirmación.</p></div></header>
      {error && <div className="error-message" role="alert">{error}</div>}
      <section className="editor-card ns-import-file-card">
        <div className="section-heading"><div><p className="eyebrow">Paso 1</p><h2>Seleccionar archivo</h2></div></div>
        {administrator && <label className="field-group ns-import-organization-field"><span>Organización destino</span><select value={selectedOrganization} disabled={organizationsLoading || loading || applying} onChange={(event) => void changeOrganization(event.target.value)}><option value="">Selecciona una organización</option>{organizations.map((item) => <option key={item.publicId} value={item.publicId}>{item.name} · {item.code}</option>)}</select><small>La carga y cualquier catálogo nuevo pertenecerán únicamente a la organización seleccionada.</small></label>}
        <input ref={inputRef} type="file" accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
          onChange={(event) => void changeFile(event.target.files?.[0])} />
        {file && <div className="ns-selected-file"><strong>{file.name}</strong><span>{Math.ceil(file.size / 1024)} KB</span></div>}
        <button type="button" className="primary-button" disabled={!file || loading || (administrator && !selectedOrganization)} onClick={() => void analyze()}>{loading ? 'Analizando…' : 'Validar y comparar'}</button>
      </section>

      {preview && <>
        <section className="editor-card ns-import-context"><div><strong>Archivo</strong><span>{preview.fileName}</span></div><div><strong>Hoja detectada</strong><span>{preview.sheetName}</span></div><div><strong>Organización actual</strong><span>{preview.organizationName} · {preview.organizationCode}</span></div></section>
        <section className="ns-import-summary-grid">
          <article><strong>{preview.totalRows}</strong><span>Filas analizadas</span></article>
          <article><strong>{preview.newStudents.length}</strong><span>Nuevos</span></article>
          <article><strong>{preview.changedStudents.length}</strong><span>Con cambios</span></article>
          <article><strong>{preview.possibleLows.length}</strong><span>Posibles bajas</span></article>
          <article><strong>{preview.conflicts.length}</strong><span>Conflictos</span></article>
          <article><strong>{preview.errors.length}</strong><span>Errores</span></article>
        </section>

        {preview.newStudents.length > 0 && <section className="editor-card"><div className="section-heading"><div><p className="eyebrow">Altas</p><h2>Nuevos colaboradores</h2></div></div>
          <p className="muted">Completa el correo de cada cuenta seleccionada. No se generan direcciones automáticamente.</p>
          <div className="ns-data-table-wrap"><table className="ns-data-table"><thead><tr><th>Crear</th><th>Colaborador</th><th>Perfil</th><th>Tecnología principal</th><th>Correo</th></tr></thead><tbody>
            {newRows.map((row, index) => <tr key={row.rowKey}><td><input type="checkbox" checked={row.selected} onChange={(event) => setNewRows((current) => current.map((item, itemIndex) => itemIndex === index ? { ...item, selected: event.target.checked } : item))} /></td><td><strong>{row.collaborator}</strong>{row.warnings.map((warning) => <small key={warning} className="warning-text">{warning}</small>)}</td><td>{row.profile || '—'}</td><td>{row.primaryTechnology || '—'}</td><td><input type="email" value={row.email} disabled={!row.selected} placeholder="correo@dominio.com" onChange={(event) => setNewRows((current) => current.map((item, itemIndex) => itemIndex === index ? { ...item, email: event.target.value } : item))} /></td></tr>)}
          </tbody></table></div>{emailError && <div className="error-message" role="alert">{emailError}</div>}
        </section>}

        {preview.changedStudents.length > 0 && <section className="editor-card"><div className="section-heading"><div><p className="eyebrow">Actualizaciones</p><h2>Colaboradores con cambios</h2></div></div>
          {changedRows.map((row, rowIndex) => <article className="ns-import-change-card" key={row.studentPublicId}><h3>{row.collaborator}</h3>{row.warnings.map((warning) => <p className="warning-text" key={warning}>{warning}</p>)}<div className="ns-data-table-wrap"><table className="ns-data-table"><thead><tr><th>Aplicar</th><th>Campo</th><th>Valor actual</th><th>Valor del Excel</th></tr></thead><tbody>
            {row.changes.map((change) => <tr key={change.key}><td><input type="checkbox" checked={row.selectedFields.has(change.key)} onChange={(event) => setChangedRows((current) => current.map((item, itemIndex) => { if (itemIndex !== rowIndex) return item; const selectedFields = new Set(item.selectedFields); if (event.target.checked) selectedFields.add(change.key); else selectedFields.delete(change.key); return { ...item, selectedFields } }))} /></td><td>{change.field}</td><td>{change.currentValue}</td><td>{change.excelValue}</td></tr>)}
          </tbody></table></div><div className="ns-import-row-actions"><button type="button" className="secondary-button compact-button" onClick={() => setChangedRows((current) => current.map((item, itemIndex) => itemIndex === rowIndex ? { ...item, selectedFields: new Set(item.changes.map((change) => change.key)) } : item))}>Seleccionar todos</button><button type="button" className="secondary-button compact-button" onClick={() => setChangedRows((current) => current.map((item, itemIndex) => itemIndex === rowIndex ? { ...item, selectedFields: new Set() } : item))}>Descartar todos</button></div></article>)}
        </section>}

        {preview.possibleLows.length > 0 && <section className="editor-card"><div className="section-heading"><div><p className="eyebrow">Revisión</p><h2>Posibles bajas</h2></div></div><p className="muted">No se elimina a nadie desde la importación. La desactivación solo se ejecuta cuando la seleccionas expresamente.</p><div className="ns-data-table-wrap"><table className="ns-data-table"><thead><tr><th>Colaborador</th><th>Correo</th><th>Acción</th></tr></thead><tbody>{preview.possibleLows.map((row) => <tr key={row.studentPublicId}><td>{row.collaborator}</td><td>{row.email}</td><td><select value={lowActions[row.studentPublicId] ?? 'KEEP'} onChange={(event) => setLowActions((current) => ({ ...current, [row.studentPublicId]: event.target.value as LowAction }))}><option value="KEEP">Mantener activo</option><option value="IGNORE">Ignorar</option><option value="DEACTIVATE">Desactivar</option></select></td></tr>)}</tbody></table></div></section>}

        {(preview.conflicts.length > 0 || preview.errors.length > 0) && <section className="editor-card"><div className="section-heading"><div><p className="eyebrow">Validación</p><h2>Conflictos y errores</h2></div></div><ul className="ns-import-issues">{[...preview.conflicts, ...preview.errors].map((issue, index) => <li key={`${issue.code}-${index}`}><strong>{issue.row > 0 ? `Fila ${issue.row}: ` : ''}</strong>{issue.message}</li>)}</ul></section>}

        <div className="form-actions ns-import-final-actions"><button type="button" className="secondary-button" onClick={() => void changeFile(undefined)}>Descartar</button><button type="button" className="primary-button" disabled={Boolean(emailError) || applying} onClick={() => setConfirmOpen(true)}>Aplicar cambios seleccionados</button></div>
      </>}

      {confirmOpen && preview && <div className="modal-backdrop" role="presentation"><section className="modal-card" role="dialog" aria-modal="true" aria-labelledby="student-import-confirm-title"><h2 id="student-import-confirm-title">Confirmar importación</h2><p>Se crearán <strong>{selectedNew.length}</strong> colaboradores, se actualizarán <strong>{selectedChanges.length}</strong> y se desactivarán <strong>{selectedLows}</strong> posibles bajas.</p><p className="muted">Los registros con conflictos o errores no se aplicarán.</p><div className="modal-actions"><button type="button" className="secondary-button" disabled={applying} onClick={() => setConfirmOpen(false)}>Cancelar</button><button type="button" className="primary-button" disabled={applying || Boolean(emailError)} onClick={() => void applyChanges()}>{applying ? 'Aplicando…' : 'Confirmar importación'}</button></div></section></div>}
    </main>
  )
}
