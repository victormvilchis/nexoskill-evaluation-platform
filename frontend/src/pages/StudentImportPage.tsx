import { useEffect, useMemo, useRef, useState, type DragEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../features/authentication/context/AuthContext'
import { searchOrganizations } from '../features/organizations/api/organizationApi'
import type { OrganizationSummary } from '../features/organizations/types/organizations'
import { applyStudentImport, discardStudentImport, previewStudentImport } from '../features/students/api/studentImportApi'
import type {
  ChangedStudentPreview,
  NewStudentPreview,
  StudentImportApplyResult,
  StudentImportConflictActionValue,
  StudentImportCredential,
  StudentImportPreview
} from '../features/students/types/studentImport'
import { ApiRequestError } from '../shared/api/apiClient'
import { BackButton } from '../shared/components/BackButton'
import { Icon } from '../shared/components/Icon'
import { useToast } from '../shared/components/ToastProvider'

type LowAction = 'KEEP' | 'DEACTIVATE' | 'IGNORE'
type NewState = NewStudentPreview & {
  selected: boolean
  email: string
  studentCode: string
  corporateUser: string
}
type ChangeState = ChangedStudentPreview & { selectedFields: Set<string> }

type ConflictDecisions = Record<string, StudentImportConflictActionValue | ''>

function validEmail(value: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim())
}

function credentialText(value: StudentImportCredential) {
  return `Colaborador: ${value.collaborator}\nOrganización: ${value.organizationCode || value.organization}\nCódigo a nivel organización: ${value.studentCode}\nUsuario corporativo: ${value.corporateUser || 'N/A'}\nCorreo: ${value.email}\nContraseña temporal: ${value.temporaryPassword}`
}

async function copyText(value: string) {
  if (!value) throw new Error('No hay contenido para copiar.')
  if (navigator.clipboard?.writeText && window.isSecureContext) {
    await navigator.clipboard.writeText(value)
    return
  }
  const textarea = document.createElement('textarea')
  textarea.value = value
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  textarea.style.pointerEvents = 'none'
  document.body.appendChild(textarea)
  textarea.select()
  const copied = document.execCommand('copy')
  document.body.removeChild(textarea)
  if (!copied) throw new Error('El navegador bloqueó el acceso al portapapeles.')
}

function CopyButtonContent({ copied, label }: { copied: boolean; label: string }) {
  return <><Icon name={copied ? 'check' : 'copy'} size={15} />{copied ? 'Copiado' : label}</>
}

function formatFileSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.ceil(bytes / 1024)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export function StudentImportPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { user } = useAuth()
  const toast = useToast()
  const administrator = Boolean(user?.roles.includes('ADMINISTRATOR'))
  const inputRef = useRef<HTMLInputElement>(null)
  const copyResetTimer = useRef<number | undefined>(undefined)
  const [copiedKey, setCopiedKey] = useState<string>()
  const [organizations, setOrganizations] = useState<OrganizationSummary[]>([])
  const [organizationsLoading, setOrganizationsLoading] = useState(false)
  const [selectedOrganization, setSelectedOrganization] = useState(searchParams.get('organization') ?? '')
  const [file, setFile] = useState<File>()
  const [dragging, setDragging] = useState(false)
  const [preview, setPreview] = useState<StudentImportPreview>()
  const [newRows, setNewRows] = useState<NewState[]>([])
  const [changedRows, setChangedRows] = useState<ChangeState[]>([])
  const [lowActions, setLowActions] = useState<Record<string, LowAction>>({})
  const [conflictDecisions, setConflictDecisions] = useState<ConflictDecisions>({})
  const [loading, setLoading] = useState(false)
  const [applying, setApplying] = useState(false)
  const [error, setError] = useState<string>()
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [result, setResult] = useState<StudentImportApplyResult>()

  useEffect(() => () => {
    if (copyResetTimer.current !== undefined) window.clearTimeout(copyResetTimer.current)
  }, [])

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

  const selectedNew = useMemo(() => newRows.filter((row) => row.selected), [newRows])
  const selectedChanges = useMemo(() => changedRows.filter((row) => row.selectedFields.size > 0), [changedRows])
  const selectedLows = useMemo(
    () => Object.values(lowActions).filter((action) => action === 'DEACTIVATE').length,
    [lowActions]
  )
  const omittedRows = useMemo(() => new Set(
    preview?.conflicts
      .filter((conflict) => conflictDecisions[conflict.id] === 'OMIT_ROW')
      .map((conflict) => conflict.rowKey) ?? []
  ), [preview, conflictDecisions])
  const unresolvedConflicts = useMemo(
    () => preview?.conflicts.filter((conflict) => !conflictDecisions[conflict.id]).length ?? 0,
    [preview, conflictDecisions]
  )
  const resolvedConflicts = (preview?.conflicts.length ?? 0) - unresolvedConflicts

  const newRowError = useMemo(() => {
    const normalizedEmails = new Map<string, number>()
    const normalizedCodes = new Map<string, number>()
    const normalizedCorporateUsers = new Map<string, number>()
    for (const row of selectedNew) {
      if (omittedRows.has(row.rowKey)) continue
      const email = row.email.trim().toLowerCase()
      if (!validEmail(email)) return `Captura un correo válido para ${row.collaborator || `la fila ${row.row}`}.`
      normalizedEmails.set(email, (normalizedEmails.get(email) ?? 0) + 1)
      if (preview?.manualStudentCode) {
        const code = row.studentCode.trim().toUpperCase()
        if (!code) return `Captura el Código a nivel organización para ${row.collaborator || `la fila ${row.row}`}.`
        if (!/^[A-Z0-9_-]+$/.test(code) || code.length > 80) {
          return `El Código a nivel organización de ${row.collaborator || `la fila ${row.row}`} no es válido.`
        }
        normalizedCodes.set(code, (normalizedCodes.get(code) ?? 0) + 1)
      }
      const corporateUser = row.corporateUser.trim().toUpperCase()
      if (corporateUser) {
        if (!row.admissionDate) return `El Usuario corporativo de ${row.collaborator || `la fila ${row.row}`} requiere una Fecha de alta.`
        if (corporateUser.length > 100) return `El Usuario corporativo de ${row.collaborator || `la fila ${row.row}`} no puede superar 100 caracteres.`
        normalizedCorporateUsers.set(corporateUser, (normalizedCorporateUsers.get(corporateUser) ?? 0) + 1)
      }
    }
    const duplicateEmail = [...normalizedEmails.entries()].find(([, count]) => count > 1)
    if (duplicateEmail) return `El correo ${duplicateEmail[0]} está repetido entre los colaboradores nuevos.`
    const duplicateCode = [...normalizedCodes.entries()].find(([, count]) => count > 1)
    if (duplicateCode) return `El Código a nivel organización ${duplicateCode[0]} está repetido entre los colaboradores nuevos.`
    const duplicateCorporateUser = [...normalizedCorporateUsers.entries()].find(([, count]) => count > 1)
    return duplicateCorporateUser
      ? `El Usuario corporativo ${duplicateCorporateUser[0]} está repetido entre los colaboradores nuevos.`
      : undefined
  }, [selectedNew, omittedRows, preview?.manualStudentCode])

  const visibleErrors = useMemo(() => {
    return preview?.errors ?? []
  }, [preview])

  const blockingMessage = newRowError
    ?? (unresolvedConflicts > 0 ? `Resuelve los ${unresolvedConflicts} conflictos pendientes antes de confirmar.` : undefined)

  function resetPreviewState() {
    setPreview(undefined)
    setNewRows([])
    setChangedRows([])
    setLowActions({})
    setConflictDecisions({})
    setResult(undefined)
    setError(undefined)
  }

  async function analyze() {
    if (!file || loading || applying) return
    setLoading(true)
    setError(undefined)
    setResult(undefined)
    try {
      if (administrator && !selectedOrganization) {
        setError('Selecciona la organización que recibirá la carga antes de analizar el archivo.')
        return
      }
      const previousToken = preview?.token
      const response = await previewStudentImport(file, administrator ? selectedOrganization : undefined)
      setPreview(response)
      if (previousToken && previousToken !== response.token) {
        void discardStudentImport(previousToken).catch(() => undefined)
      }
      setNewRows(response.newStudents.map((row) => ({
        ...row,
        selected: true,
        email: row.suggestedEmail ?? '',
        studentCode: '',
        corporateUser: ''
      })))
      setChangedRows(response.changedStudents.map((row) => ({
        ...row,
        selectedFields: new Set(row.changes.filter((change) => change.selected).map((change) => change.key))
      })))
      setLowActions(Object.fromEntries(response.possibleLows.map((row) => [row.studentPublicId, 'KEEP'])))
      setConflictDecisions(Object.fromEntries(response.conflicts.map((conflict) => [
        conflict.id,
        conflict.resolvedAction ?? ''
      ])))
    } catch (requestError) {
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
    resetPreviewState()
  }

  async function changeFile(next?: File) {
    if (loading || applying) return
    if (next && !next.name.toLowerCase().endsWith('.xlsx')) {
      setError('Selecciona un archivo con formato .xlsx.')
      return
    }
    if (preview?.token && !result) await discardStudentImport(preview.token).catch(() => undefined)
    setFile(next)
    resetPreviewState()
  }

  function handleDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault()
    setDragging(false)
    if (loading || applying) return
    const next = event.dataTransfer.files?.[0]
    if (next) void changeFile(next)
  }

  function openFilePicker() {
    if (!loading && !applying) inputRef.current?.click()
  }

  function applyEquivalentDecision(conflictId: string) {
    if (!preview) return
    const source = preview.conflicts.find((conflict) => conflict.id === conflictId)
    const action = conflictDecisions[conflictId]
    if (!source || !action) return
    setConflictDecisions((current) => {
      const next = { ...current }
      preview.conflicts
        .filter((conflict) => conflict.groupKey === source.groupKey
          && !conflict.reusedDecision
          && conflict.actions.some((option) => option.value === action))
        .forEach((conflict) => { next[conflict.id] = action })
      return next
    })
  }

  async function copyValue(value: string, description: string, key: string) {
    try {
      await copyText(value)
      setCopiedKey(key)
      if (copyResetTimer.current !== undefined) window.clearTimeout(copyResetTimer.current)
      copyResetTimer.current = window.setTimeout(() => {
        setCopiedKey((current) => current === key ? undefined : current)
      }, 1800)
      toast.success('Copiado al portapapeles', `${description} se copió correctamente.`)
    } catch {
      toast.error('No se pudo copiar', 'El navegador bloqueó el portapapeles. Selecciona el texto y cópialo manualmente.')
    }
  }

  async function applyChanges() {
    if (!preview || applying || blockingMessage) return
    setApplying(true)
    setError(undefined)
    try {
      const response = await applyStudentImport({
        token: preview.token,
        newStudents: newRows.map((row) => ({
          rowKey: row.rowKey,
          email: row.email.trim(),
          studentCode: preview.manualStudentCode ? row.studentCode.trim() : undefined,
          corporateUser: row.admissionDate && row.corporateUser.trim() ? row.corporateUser.trim() : undefined,
          selected: row.selected
        })),
        changedStudents: changedRows.map((row) => ({
          studentPublicId: row.studentPublicId,
          fields: [...row.selectedFields]
        })),
        possibleLows: preview.possibleLows.map((row) => ({
          studentPublicId: row.studentPublicId,
          action: lowActions[row.studentPublicId] ?? 'KEEP'
        })),
        conflicts: preview.conflicts.map((conflict) => ({
          conflictId: conflict.id,
          action: conflictDecisions[conflict.id] as StudentImportConflictActionValue
        }))
      })
      setResult(response)
      setConfirmOpen(false)
      if (response.errors.length === 0) {
        toast.success('Importación aplicada', `Se crearon ${response.created} y se actualizaron ${response.updated} colaboradores.`)
      } else {
        toast.warning('Importación finalizada con errores', `Se procesaron los registros válidos y ${response.errors.length} filas no pudieron aplicarse.`)
      }
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
        {result.errors.length > 0 && <section className="editor-card"><h2>Filas que no pudieron aplicarse</h2>
          <p className="muted">Los demás colaboradores fueron procesados de forma independiente.</p>
          <ul className="ns-import-issues">{result.errors.map((issue, index) => (
            <li key={`${issue.code}-${index}`}><strong>{issue.row > 0 ? `Fila ${issue.row}: ` : ''}</strong>{issue.message}</li>
          ))}</ul>
        </section>}
        {result.credentials.length > 0 && <section className="editor-card ns-credential-once">
          <div className="section-heading"><div><p className="eyebrow">Nuevas altas</p><h2>Accesos generados</h2></div></div>
          <div className="warning-message" role="alert">{result.credentialsNotice}</div>
          <div className="ns-import-copy-actions">
            <button type="button" className={`secondary-button ns-copy-action-button${copiedKey === 'all' ? ' is-copied' : ''}`}
              onClick={() => void copyValue(all, 'Todos los accesos', 'all')}>
              <CopyButtonContent copied={copiedKey === 'all'} label="Copiar todos los accesos" />
            </button>
            <button type="button" className={`secondary-button ns-copy-action-button${copiedKey === 'emails' ? ' is-copied' : ''}`}
              onClick={() => void copyValue(result.credentials.map((item) => item.email).join('\n'), 'Los correos', 'emails')}>
              <CopyButtonContent copied={copiedKey === 'emails'} label="Copiar correos" />
            </button>
          </div>
          <div className="ns-data-table-wrap"><table className="ns-data-table ns-credential-table"><thead><tr><th>Colaborador</th><th>Correo</th><th>Código a nivel organización</th><th>Usuario corporativo</th><th>Organización</th><th>Resultado</th><th>Contraseña temporal</th><th>Acciones</th></tr></thead><tbody>
            {result.credentials.map((credential) => {
              const key = `credential:${credential.email}`
              return <tr key={credential.email}>
                <td><strong>{credential.collaborator}</strong></td>
                <td>{credential.email}</td>
                <td><code>{credential.studentCode}</code></td>
                <td>{credential.corporateUser || 'N/A'}</td>
                <td>{credential.organization}</td>
                <td><span className="status-badge active">Creado</span></td>
                <td><code>{credential.temporaryPassword}</code></td>
                <td><button type="button" className={`ns-copy-inline-button${copiedKey === key ? ' is-copied' : ''}`}
                  onClick={() => void copyValue(credentialText(credential), `El acceso de ${credential.collaborator}`, key)}>
                  <CopyButtonContent copied={copiedKey === key} label="Copiar acceso" />
                </button></td>
              </tr>
            })}
          </tbody></table></div>
        </section>}
        <div className="form-actions"><button type="button" className="primary-button" onClick={closeResult}>Volver a colaboradores</button></div>
      </main>
    )
  }

  return (
    <main className="content-page student-import-page">
      <header className="page-heading compact">
        <div><p className="eyebrow">Colaboradores</p><h1>Importar colaboradores</h1><p>Valida, completa y resuelve cada registro antes de aplicar la carga.</p></div>
        <BackButton fallback={studentsPath} label="Volver" />
      </header>

      {error && <div className="error-message" role="alert">{error}</div>}

      <section className="editor-card ns-import-source-card">
        {administrator && <label className="field"><span>Organización destino</span><select value={selectedOrganization}
          disabled={organizationsLoading || loading || applying} onChange={(event) => void changeOrganization(event.target.value)}>
          <option value="">{organizationsLoading ? 'Cargando organizaciones…' : 'Selecciona una organización'}</option>
          {organizations.map((organization) => <option key={organization.publicId} value={organization.publicId}>{organization.name} · {organization.code}</option>)}
        </select></label>}
        <input ref={inputRef} type="file" accept=".xlsx" hidden onChange={(event) => void changeFile(event.target.files?.[0])} />
        <div className={`ns-import-dropzone${dragging ? ' is-dragging' : ''}${file ? ' has-file' : ''}`}
          role="button" tabIndex={0} aria-label="Seleccionar o arrastrar archivo Excel"
          onClick={openFilePicker}
          onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); openFilePicker() } }}
          onDragEnter={(event) => { event.preventDefault(); setDragging(true) }}
          onDragOver={(event) => { event.preventDefault(); setDragging(true) }}
          onDragLeave={(event) => { if (event.currentTarget === event.target) setDragging(false) }}
          onDrop={handleDrop}>
          <span className="ns-import-dropzone-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none"><path d="M12 16V4m0 0L7.5 8.5M12 4l4.5 4.5M5 14v5h14v-5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg></span>
          <div className="ns-import-dropzone-copy"><strong>{file ? 'Archivo listo para analizar' : 'Arrastra tu archivo Excel aquí'}</strong>
            <span>{file ? 'Haz clic para reemplazarlo' : 'o haz clic para seleccionarlo desde tu equipo'}</span>
            <small>Formato .xlsx · Se procesa la primera hoja</small></div>
          <span className="ns-import-select-file">{file ? 'Cambiar archivo' : 'Seleccionar archivo'}</span>
        </div>
        {file && <div className="ns-selected-file"><div><strong>{file.name}</strong><span>{formatFileSize(file.size)}</span></div>
          <button type="button" className="ns-selected-file-remove" disabled={loading || applying}
            onClick={(event) => { event.stopPropagation(); void changeFile(undefined) }} aria-label="Quitar archivo">×</button></div>}
        <div className="ns-import-analyze-actions"><p><strong>Vista previa obligatoria.</strong> Ningún cambio se guarda antes de confirmar.</p>
          <button type="button" className="primary-button ns-import-analyze-button"
            disabled={!file || loading || applying || (administrator && !selectedOrganization)} onClick={() => void analyze()}>
            {loading ? 'Analizando archivo…' : 'Validar y comparar'}
          </button></div>
      </section>

      {preview && <>
        <section className="editor-card ns-import-context"><div><strong>Archivo</strong><span>{preview.fileName}</span></div><div><strong>Hoja</strong><span>{preview.sheetName}</span></div><div><strong>Organización</strong><span>{preview.organizationName} · {preview.organizationCode}</span></div></section>
        <section className="ns-import-summary-grid">
          <article><strong>{preview.totalRows}</strong><span>Filas analizadas</span></article>
          <article><strong>{selectedNew.filter((row) => !omittedRows.has(row.rowKey)).length}</strong><span>Nuevos por crear</span></article>
          <article><strong>{selectedChanges.length}</strong><span>Por actualizar</span></article>
          <article><strong>{preview.possibleLows.length}</strong><span>Posibles bajas</span></article>
          <article><strong>{unresolvedConflicts}</strong><span>Conflictos pendientes</span></article>
          <article><strong>{resolvedConflicts}</strong><span>Conflictos resueltos</span></article>
          <article><strong>{preview.warnings.length}</strong><span>Advertencias</span></article>
          <article><strong>{visibleErrors.length}</strong><span>Filas con error</span></article>
          <article><strong>{omittedRows.size + newRows.filter((row) => !row.selected).length}</strong><span>Filas omitidas</span></article>
        </section>

        {preview.newStudents.length > 0 && <section className="editor-card"><div className="section-heading"><div><p className="eyebrow">Altas</p><h2>Nuevos colaboradores pendientes de completar</h2></div></div>
          <p className="muted">El correo es obligatorio. El Usuario corporativo es opcional y solo se habilita cuando existe Fecha de alta. {preview.manualStudentCode ? 'Captura también el Código a nivel organización.' : 'El Código a nivel organización se generará automáticamente al confirmar.'}</p>
          <div className="ns-data-table-wrap"><table className="ns-data-table"><thead><tr><th>Crear</th><th>Nombre completo</th><th>Perfil</th><th>Tecnología principal</th><th>Fecha de alta</th><th>Correo</th>{preview.manualStudentCode && <th>Código a nivel organización</th>}<th>Usuario corporativo</th>{!preview.manualStudentCode && <th>Código a nivel organización</th>}</tr></thead><tbody>
            {newRows.map((row, index) => <tr key={row.rowKey} className={omittedRows.has(row.rowKey) ? 'is-muted' : undefined}>
              <td><input type="checkbox" checked={row.selected} disabled={omittedRows.has(row.rowKey)} onChange={(event) => setNewRows((current) => current.map((item, itemIndex) => itemIndex === index ? { ...item, selected: event.target.checked } : item))} /></td>
              <td><strong>{row.collaborator}</strong>
                {row.warnings.map((warning) => <small key={warning} className="warning-text">{warning}</small>)}</td>
              <td>{row.profile || 'N/A'}</td><td>{row.primaryTechnology || 'N/A'}</td><td>{row.admissionDate || 'N/A'}</td>
              <td><label className="ns-import-email-field"><span className="sr-only">Correo de la fila {row.row}</span><input
                className={`ns-import-email-input${row.selected && !omittedRows.has(row.rowKey) && row.email && !validEmail(row.email) ? ' is-invalid' : ''}`}
                type="email" value={row.email} disabled={!row.selected || omittedRows.has(row.rowKey)} placeholder="nombre@dominio.com" autoComplete="off"
                onChange={(event) => setNewRows((current) => current.map((item, itemIndex) => itemIndex === index ? { ...item, email: event.target.value } : item))} /></label></td>
              {preview.manualStudentCode && <td><label className="ns-import-email-field"><span className="sr-only">Código a nivel organización de la fila {row.row}</span><input
                value={row.studentCode} disabled={!row.selected || omittedRows.has(row.rowKey)} placeholder="Código obligatorio" autoComplete="off" maxLength={80}
                onChange={(event) => setNewRows((current) => current.map((item, itemIndex) => itemIndex === index ? { ...item, studentCode: event.target.value.toUpperCase() } : item))} /></label></td>}
              <td><label className="ns-import-email-field"><span className="sr-only">Usuario corporativo de la fila {row.row}</span><input
                value={row.corporateUser} disabled={!row.selected || omittedRows.has(row.rowKey) || !row.admissionDate}
                placeholder={row.admissionDate ? 'Opcional' : 'Requiere Fecha de alta'} autoComplete="off" maxLength={100}
                onChange={(event) => setNewRows((current) => current.map((item, itemIndex) => itemIndex === index ? { ...item, corporateUser: event.target.value.toUpperCase() } : item))} /></label></td>
              {!preview.manualStudentCode && <td><span className="muted">Se generará automáticamente</span></td>}
            </tr>)}
          </tbody></table></div>{newRowError && <div className="error-message" role="alert">{newRowError}</div>}
        </section>}

        {preview.changedStudents.length > 0 && <section className="editor-card"><div className="section-heading"><div><p className="eyebrow">Actualizaciones</p><h2>Colaboradores con cambios</h2></div></div>
          {changedRows.map((row, rowIndex) => <article className={`ns-import-change-card${omittedRows.has(row.rowKey) ? ' is-muted' : ''}`} key={row.studentPublicId}><h3>{row.collaborator}</h3>{row.warnings.map((warning) => <p className="warning-text" key={warning}>{warning}</p>)}<div className="ns-data-table-wrap"><table className="ns-data-table"><thead><tr><th>Aplicar</th><th>Campo</th><th>Valor actual</th><th>Valor del Excel o calculado</th></tr></thead><tbody>
            {row.changes.map((change) => <tr key={change.key}><td><input type="checkbox" disabled={omittedRows.has(row.rowKey)} checked={!omittedRows.has(row.rowKey) && row.selectedFields.has(change.key)} onChange={(event) => setChangedRows((current) => current.map((item, itemIndex) => { if (itemIndex !== rowIndex) return item; const selectedFields = new Set(item.selectedFields); if (event.target.checked) selectedFields.add(change.key); else selectedFields.delete(change.key); return { ...item, selectedFields } }))} /></td><td>{change.field}</td><td>{change.currentValue}</td><td>{change.excelValue}</td></tr>)}
          </tbody></table></div></article>)}
        </section>}

        {preview.conflicts.length > 0 && <section className="editor-card"><div className="section-heading"><div><p className="eyebrow">Validación</p><h2>Conflictos</h2></div><span className="muted">{unresolvedConflicts} pendientes</span></div>
          <div className="ns-import-conflict-list">{preview.conflicts.map((conflict) => {
            const decision = conflictDecisions[conflict.id] ?? ''
            const selectedAction = conflict.actions.find((action) => action.value === decision)
            return <article className="ns-import-conflict-card" key={conflict.id}>
              <div className="ns-import-conflict-heading"><div><span>Fila {conflict.row}</span><h3>{conflict.title}</h3><p>{conflict.collaborator}</p></div><span className={`status-badge ${decision ? 'active' : 'warning'}`}>{conflict.reusedDecision ? 'Resuelto previamente' : decision ? 'Resuelto' : 'Pendiente'}</span></div>
              <div className="ns-import-conflict-values"><div><strong>Excel</strong><span>{conflict.excelValue}</span></div><div><strong>Plataforma actual</strong><span>{conflict.currentValue}</span></div><div><strong>Cálculo de la plataforma</strong><span>{conflict.calculatedValue}</span></div></div>
              <p className="ns-import-conflict-reason">{conflict.reason}</p>
              {conflict.reusedDecision
                ? <div className="ns-import-reused-decision"><strong>Decisión aplicada automáticamente</strong><span>{selectedAction?.label}</span></div>
                : <label className="field"><span>Selecciona cómo proceder</span><select value={decision}
                  onChange={(event) => setConflictDecisions((current) => ({ ...current, [conflict.id]: event.target.value as StudentImportConflictActionValue }))}>
                  <option value="">Selecciona una decisión</option>
                  {conflict.actions.map((action) => <option key={action.value} value={action.value}>{action.label}</option>)}
                </select></label>}
              {decision && <p className="muted">{selectedAction?.description}</p>}
              {!conflict.reusedDecision && decision && preview.conflicts.filter((item) => item.groupKey === conflict.groupKey && !item.reusedDecision).length > 1 && <button type="button" className="secondary-button compact-button" onClick={() => applyEquivalentDecision(conflict.id)}>Aplicar esta decisión a casos equivalentes</button>}
            </article>
          })}</div>
        </section>}

        {preview.warnings.length > 0 && <section className="editor-card"><div className="section-heading"><div><p className="eyebrow">Información</p><h2>Advertencias</h2></div></div><p className="muted">Estas situaciones no impiden continuar.</p><ul className="ns-import-issues warning-list">{preview.warnings.map((issue, index) => <li key={`${issue.code}-${issue.row}-${index}`}><strong>{issue.row > 0 ? `Fila ${issue.row}: ` : ''}</strong>{issue.message}</li>)}</ul></section>}

        {visibleErrors.length > 0 && <section className="editor-card"><div className="section-heading"><div><p className="eyebrow">Corrección requerida</p><h2>Filas con error</h2></div></div><p className="muted">El error afecta únicamente al colaborador indicado y no detiene los demás registros válidos.</p><ul className="ns-import-issues">{visibleErrors.map((issue, index) => <li key={`${issue.code}-${issue.row}-${index}`}><strong>Fila {issue.row}: </strong>{issue.message}</li>)}</ul></section>}

        {preview.possibleLows.length > 0 && <section className="editor-card"><div className="section-heading"><div><p className="eyebrow">Revisión</p><h2>Posibles bajas</h2></div></div><p className="muted">La desactivación solo se ejecuta cuando la seleccionas expresamente.</p><div className="ns-data-table-wrap"><table className="ns-data-table"><thead><tr><th>Colaborador</th><th>Correo</th><th>Acción</th></tr></thead><tbody>{preview.possibleLows.map((row) => <tr key={row.studentPublicId}><td>{row.collaborator}</td><td>{row.email}</td><td><select value={lowActions[row.studentPublicId] ?? 'KEEP'} onChange={(event) => setLowActions((current) => ({ ...current, [row.studentPublicId]: event.target.value as LowAction }))}><option value="KEEP">Mantener activo</option><option value="IGNORE">Ignorar</option><option value="DEACTIVATE">Desactivar</option></select></td></tr>)}</tbody></table></div></section>}

        <div className="form-actions ns-import-final-actions"><button type="button" className="secondary-button" disabled={applying} onClick={() => void changeFile(undefined)}>Descartar</button><button type="button" className="primary-button" disabled={Boolean(blockingMessage) || applying} onClick={() => setConfirmOpen(true)}>Aplicar cambios seleccionados</button></div>
        {blockingMessage && <div className="error-message" role="alert">{blockingMessage}</div>}
      </>}

      {confirmOpen && preview && <div className="modal-backdrop" role="presentation"><section className="modal-card" role="dialog" aria-modal="true" aria-labelledby="student-import-confirm-title"><h2 id="student-import-confirm-title">Confirmar importación</h2><p>Se crearán <strong>{selectedNew.filter((row) => !omittedRows.has(row.rowKey)).length}</strong> colaboradores, se actualizarán <strong>{selectedChanges.filter((row) => !omittedRows.has(row.rowKey)).length}</strong> y se desactivarán <strong>{selectedLows}</strong> posibles bajas.</p><p className="muted">Se omitirán {omittedRows.size + newRows.filter((row) => !row.selected).length} filas. Las advertencias no bloquean el proceso.</p><div className="modal-actions"><button type="button" className="secondary-button" disabled={applying} onClick={() => setConfirmOpen(false)}>Cancelar</button><button type="button" className="primary-button" disabled={applying || Boolean(blockingMessage)} onClick={() => void applyChanges()}>{applying ? 'Aplicando…' : 'Confirmar importación'}</button></div></section></div>}
    </main>
  )
}
