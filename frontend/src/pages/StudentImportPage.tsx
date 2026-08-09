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
import { ConfirmDialog } from '../shared/components/ConfirmDialog'
import { FormActions } from '../shared/components/FormActions'
import { Icon } from '../shared/components/Icon'
import { SelectField } from '../shared/components/SelectField'
import { useToast } from '../shared/components/ToastProvider'
import { formatPersonName } from '../shared/utils/personNames'

type LowAction = 'KEEP' | 'DEACTIVATE' | 'IGNORE'
type NewState = NewStudentPreview & {
  selected: boolean
  email: string
  studentCode: string
  corporateUser: string
}
type ChangeState = ChangedStudentPreview & { selected: boolean; selectedFields: Set<string> }

type ConflictDecisions = Record<string, StudentImportConflictActionValue | ''>
type ConflictTab = 'PENDING' | 'REUSED'
type ChangeTab = 'PENDING' | 'REUSED'

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
  const [conflictTab, setConflictTab] = useState<ConflictTab>('PENDING')
  const [changeTab, setChangeTab] = useState<ChangeTab>('PENDING')
  const [loading, setLoading] = useState(false)
  const [applying, setApplying] = useState(false)
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
    ? `/admin/collaborators?organization=${encodeURIComponent(selectedOrganization)}`
    : '/admin/collaborators'

  const selectedNew = useMemo(() => newRows.filter((row) => row.selected), [newRows])
  const newRowKeys = useMemo(() => new Set(newRows.map((row) => row.rowKey)), [newRows])
  const selectableRowKeys = useMemo(() => new Set([
    ...newRows.map((row) => row.rowKey),
    ...changedRows.map((row) => row.rowKey)
  ]), [newRows, changedRows])
  const selectedRowKeys = useMemo(() => new Set([
    ...newRows.filter((row) => row.selected).map((row) => row.rowKey),
    ...changedRows.filter((row) => row.selected).map((row) => row.rowKey)
  ]), [newRows, changedRows])
  const activeConflicts = useMemo(
    () => preview?.conflicts.filter((conflict) => !selectableRowKeys.has(conflict.rowKey)
      || selectedRowKeys.has(conflict.rowKey)) ?? [],
    [preview, selectableRowKeys, selectedRowKeys]
  )
  const actionableConflictRowKeys = useMemo(
    () => new Set(activeConflicts
      .filter((conflict) => !conflict.reusedDecision
        && conflict.applyResolution
        && Boolean(conflictDecisions[conflict.id])
        && conflictDecisions[conflict.id] !== 'OMIT_ROW')
      .map((conflict) => conflict.rowKey)),
    [activeConflicts, conflictDecisions]
  )
  const pendingChangeCount = useMemo(
    () => changedRows.filter((row) => row.selected).reduce((total, row) =>
      total + row.changes.filter((change) => !change.reusedDecision).length, 0),
    [changedRows]
  )
  const reusedChangeCount = useMemo(
    () => changedRows.filter((row) => row.selected).reduce((total, row) =>
      total + row.changes.filter((change) => change.reusedDecision).length, 0),
    [changedRows]
  )
  const excludedRows = useMemo(
    () => newRows.filter((row) => !row.selected).length + changedRows.filter((row) => !row.selected).length,
    [newRows, changedRows]
  )
  const selectedLows = useMemo(
    () => Object.values(lowActions).filter((action) => action === 'DEACTIVATE').length,
    [lowActions]
  )
  const omittedRows = useMemo(() => new Set(
    activeConflicts
      .filter((conflict) => conflictDecisions[conflict.id] === 'OMIT_ROW')
      .map((conflict) => conflict.rowKey)
  ), [activeConflicts, conflictDecisions])
  const pendingConflicts = useMemo(
    () => activeConflicts.filter((conflict) => !conflict.reusedDecision),
    [activeConflicts]
  )
  const reusedConflicts = useMemo(
    () => activeConflicts.filter((conflict) => conflict.reusedDecision),
    [activeConflicts]
  )
  const unresolvedConflicts = useMemo(
    () => pendingConflicts.filter((conflict) => !conflictDecisions[conflict.id]).length,
    [pendingConflicts, conflictDecisions]
  )
  const newRowValidationErrors = useMemo(() => {
    const errors = new Map<string, string>()
    const emails = new Map<string, NewState[]>()
    const codes = new Map<string, NewState[]>()
    const corporateUsers = new Map<string, NewState[]>()

    const addGroup = (groups: Map<string, NewState[]>, value: string, row: NewState) => {
      groups.set(value, [...(groups.get(value) ?? []), row])
    }

    for (const row of selectedNew) {
      if (omittedRows.has(row.rowKey)) continue
      const email = row.email.trim().toLowerCase()
      if (!validEmail(email)) {
        errors.set(row.rowKey, `Captura un correo válido para ${row.collaborator || `la fila ${row.row}`}.`)
      } else {
        addGroup(emails, email, row)
      }

      if (preview?.manualStudentCode) {
        const code = row.studentCode.trim().toUpperCase()
        if (!code) {
          errors.set(row.rowKey, `Captura el Código a nivel organización para ${row.collaborator || `la fila ${row.row}`}.`)
        } else if (!/^[A-Z0-9_-]+$/.test(code) || code.length > 80) {
          errors.set(row.rowKey, `El Código a nivel organización de ${row.collaborator || `la fila ${row.row}`} no es válido.`)
        } else {
          addGroup(codes, code, row)
        }
      }

      const corporateUser = row.corporateUser.trim().toUpperCase()
      if (corporateUser) {
        if (!row.admissionDate) {
          errors.set(row.rowKey, `El Usuario corporativo de ${row.collaborator || `la fila ${row.row}`} requiere una Fecha de alta.`)
        } else if (corporateUser.length > 100) {
          errors.set(row.rowKey, `El Usuario corporativo de ${row.collaborator || `la fila ${row.row}`} no puede superar 100 caracteres.`)
        } else {
          addGroup(corporateUsers, corporateUser, row)
        }
      }
    }

    const markDuplicates = (groups: Map<string, NewState[]>, label: string) => {
      groups.forEach((rows, value) => {
        if (rows.length < 2) return
        rows.forEach((row) => errors.set(row.rowKey, `${label} ${value} está repetido entre los colaboradores nuevos.`))
      })
    }
    markDuplicates(emails, 'El correo')
    markDuplicates(codes, 'El Código a nivel organización')
    markDuplicates(corporateUsers, 'El Usuario corporativo')
    return errors
  }, [selectedNew, omittedRows, preview?.manualStudentCode])

  const selectedNewCount = useMemo(
    () => selectedNew.filter((row) => !omittedRows.has(row.rowKey) && !newRowValidationErrors.has(row.rowKey)).length,
    [selectedNew, omittedRows, newRowValidationErrors]
  )
  const selectedChangeRowKeys = useMemo(() => {
    const rowKeys = new Set(changedRows
      .filter((row) => row.selected
        && !omittedRows.has(row.rowKey)
        && row.changes.some((change) => !change.reusedDecision && row.selectedFields.has(change.key)))
      .map((row) => row.rowKey))
    actionableConflictRowKeys.forEach((rowKey) => {
      if (!newRowKeys.has(rowKey) && !omittedRows.has(rowKey)) rowKeys.add(rowKey)
    })
    return rowKeys
  }, [changedRows, actionableConflictRowKeys, newRowKeys, omittedRows])
  const selectedChangeCount = selectedChangeRowKeys.size
  const selectedForApplyCount = selectedNewCount + selectedChangeCount + selectedLows
  const newRowError = useMemo(
    () => newRowValidationErrors.values().next().value as string | undefined,
    [newRowValidationErrors]
  )

  const rowSelectionByNumber = useMemo(() => new Map<number, boolean>([
    ...newRows.map((row) => [row.row, row.selected] as const),
    ...changedRows.map((row) => [row.row, row.selected] as const)
  ]), [newRows, changedRows])
  const visibleWarnings = useMemo(
    () => preview?.warnings.filter((issue) => issue.row <= 0 || rowSelectionByNumber.get(issue.row) !== false) ?? [],
    [preview, rowSelectionByNumber]
  )
  const visibleErrors = useMemo(
    () => preview?.errors.filter((issue) => issue.row <= 0 || rowSelectionByNumber.get(issue.row) !== false) ?? [],
    [preview, rowSelectionByNumber]
  )
  const errorRowCount = useMemo(() => {
    const rows = new Set(visibleErrors.filter((issue) => issue.row > 0).map((issue) => issue.row))
    selectedNew.forEach((row) => {
      if (newRowValidationErrors.has(row.rowKey)) rows.add(row.row)
    })
    return rows.size
  }, [visibleErrors, selectedNew, newRowValidationErrors])

  const pendingToResolve = pendingChangeCount + unresolvedConflicts
  const hasNewDecisionWork = pendingChangeCount > 0 || pendingConflicts.length > 0
  const hasAnythingToConfirm = selectedForApplyCount > 0 || hasNewDecisionWork
  const blockingMessage = newRowError
    ?? (unresolvedConflicts > 0
      ? 'Resuelve los conflictos de los colaboradores seleccionados antes de confirmar la importación.'
      : undefined)

  function resetPreviewState() {
    setPreview(undefined)
    setNewRows([])
    setChangedRows([])
    setLowActions({})
    setConflictDecisions({})
    setConflictTab('PENDING')
    setChangeTab('PENDING')
    setResult(undefined)
  }

  async function analyze() {
    if (!file || loading || applying) return
    setLoading(true)
    setResult(undefined)
    try {
      if (administrator && !selectedOrganization) {
        toast.warning('Organización requerida', 'Selecciona la organización que recibirá la carga antes de analizar el archivo.')
        return
      }
      const previousToken = preview?.token
      const response = await previewStudentImport(file, administrator ? selectedOrganization : undefined)
      setPreview(response)
      if (previousToken && previousToken !== response.token) {
        void discardStudentImport(previousToken).catch(() => undefined)
      }
      const previouslyOmittedRows = new Set(response.conflicts
        .filter((conflict) => conflict.resolvedAction === 'OMIT_ROW')
        .map((conflict) => conflict.rowKey))
      setNewRows(response.newStudents.map((row) => ({
        ...row,
        selected: !previouslyOmittedRows.has(row.rowKey),
        email: row.suggestedEmail ?? '',
        studentCode: '',
        corporateUser: ''
      })))
      setChangedRows(response.changedStudents.map((row) => ({
        ...row,
        selected: !previouslyOmittedRows.has(row.rowKey),
        selectedFields: new Set(row.changes
          .filter((change) => !change.reusedDecision && change.selected)
          .map((change) => change.key))
      })))
      setLowActions(Object.fromEntries(response.possibleLows.map((row) => [row.studentPublicId, 'KEEP'])))
      setConflictDecisions(Object.fromEntries(response.conflicts.map((conflict) => [
        conflict.id,
        conflict.resolvedAction ?? ''
      ])))
      setConflictTab('PENDING')
      setChangeTab('PENDING')
    } catch (requestError) {
      if (!(requestError instanceof ApiRequestError && (requestError.status === 0 || requestError.status >= 500))) {
        const message = requestError instanceof ApiRequestError
          ? requestError.message
          : 'No fue posible analizar el archivo.'
        toast.error('No fue posible analizar el archivo', message)
      }
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
      toast.warning('Archivo no compatible', 'Selecciona un archivo con formato .xlsx.')
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

  function setImportRowSelected(rowKey: string, selected: boolean) {
    setNewRows((current) => current.map((row) => row.rowKey === rowKey ? { ...row, selected } : row))
    setChangedRows((current) => current.map((row) => row.rowKey === rowKey ? { ...row, selected } : row))
    if (!selected && preview) {
      setConflictDecisions((current) => {
        const next = { ...current }
        preview.conflicts.filter((conflict) => conflict.rowKey === rowKey).forEach((conflict) => {
          next[conflict.id] = conflict.resolvedAction ?? ''
        })
        return next
      })
    }
  }

  function setConflictDecision(conflictId: string, action: StudentImportConflictActionValue) {
    if (!preview) return
    const conflict = preview.conflicts.find((item) => item.id === conflictId)
    if (!conflict) return
    if (action === 'OMIT_ROW') {
      setImportRowSelected(conflict.rowKey, false)
      return
    }
    setConflictDecisions((current) => ({ ...current, [conflictId]: action }))
  }

  function applyEquivalentDecision(conflictId: string) {
    if (!preview) return
    const source = preview.conflicts.find((conflict) => conflict.id === conflictId)
    const action = conflictDecisions[conflictId]
    if (!source || !action) return
    const equivalents = activeConflicts.filter((conflict) => conflict.groupKey === source.groupKey
      && !conflict.reusedDecision
      && conflict.actions.some((option) => option.value === action))
    if (action === 'OMIT_ROW') {
      new Set(equivalents.map((conflict) => conflict.rowKey)).forEach((rowKey) => setImportRowSelected(rowKey, false))
      return
    }
    setConflictDecisions((current) => {
      const next = { ...current }
      equivalents.forEach((conflict) => { next[conflict.id] = action })
      return next
    })
  }

  function setChangeDecision(rowKey: string, fieldKey: string, action: 'KEEP_PLATFORM' | 'APPLY_EXCEL') {
    setChangedRows((current) => current.map((row) => {
      if (row.rowKey !== rowKey) return row
      const selectedFields = new Set(row.selectedFields)
      if (action === 'APPLY_EXCEL') selectedFields.add(fieldKey)
      else selectedFields.delete(fieldKey)
      return { ...row, selectedFields }
    }))
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
    if (!hasAnythingToConfirm) {
      setConfirmOpen(false)
      toast.info('Sin cambios nuevos', 'El archivo no contiene cambios nuevos para aplicar.')
      return
    }
    setApplying(true)
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
          rowKey: row.rowKey,
          fields: [...row.selectedFields],
          selected: row.selected && (row.changes.some((change) => !change.reusedDecision)
            || activeConflicts.some((conflict) => conflict.rowKey === row.rowKey && !conflict.reusedDecision))
        })),
        possibleLows: preview.possibleLows.map((row) => ({
          studentPublicId: row.studentPublicId,
          action: lowActions[row.studentPublicId] ?? 'KEEP'
        })),
        conflicts: activeConflicts.map((conflict) => ({
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
      if (!(requestError instanceof ApiRequestError && (requestError.status === 0 || requestError.status >= 500))) {
        const message = requestError instanceof ApiRequestError
          ? requestError.message
          : 'No fue posible aplicar la importación.'
        toast.error('No fue posible aplicar la importación', message)
      }
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
      <BackButton fallback={studentsPath} />
      <header className="page-heading compact">
        <div><p className="eyebrow">Colaboradores</p><h1>Importar colaboradores</h1><p>Valida, completa y resuelve cada registro antes de aplicar la carga.</p></div>
      </header>


      <section className="editor-card ns-import-source-card">
        {administrator && <label className="field"><span>Organización destino</span><SelectField value={selectedOrganization}
          disabled={organizationsLoading || loading || applying} onChange={(nextValue) => void changeOrganization(nextValue)}
          ariaLabel="Organización destino"
          options={[
            { value: '', label: organizationsLoading ? 'Cargando organizaciones…' : 'Selecciona una organización' },
            ...organizations.map((organization) => ({ value: organization.publicId, label: `${organization.name} · ${organization.code}` }))
          ]} /></label>}
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
            onClick={(event) => { event.stopPropagation(); void changeFile(undefined) }} aria-label="Quitar archivo">
            <Icon name="close" size={17} />
          </button></div>}
        <div className="ns-import-analyze-actions"><p><strong>Vista previa obligatoria.</strong> Ningún cambio se guarda antes de confirmar.</p>
          <button type="button" className="primary-button ns-import-analyze-button"
            disabled={!file || loading || applying || (administrator && !selectedOrganization)} onClick={() => void analyze()}>
            {loading ? 'Analizando archivo…' : 'Validar y comparar'}
          </button></div>
      </section>

      {preview && <>
        <section className="editor-card ns-import-context"><div><strong>Archivo</strong><span>{preview.fileName}</span></div><div><strong>Hoja</strong><span>{preview.sheetName}</span></div><div><strong>Organización</strong><span>{preview.organizationName} · {preview.organizationCode}</span></div></section>
        <section className="ns-import-summary-panel" aria-label="Resumen de la vista previa">
          <div className="ns-import-summary-grid">
            <article><strong>{preview.totalRows}</strong><span>Filas analizadas</span></article>
            <article><strong>{selectedForApplyCount}</strong><span>Seleccionados para aplicar</span></article>
            <article><strong>{excludedRows}</strong><span>Excluidos</span></article>
            <article><strong>{pendingToResolve}</strong><span>Pendientes por resolver</span></article>
            <article><strong>{errorRowCount}</strong><span>Filas con error</span></article>
          </div>
          <div className="ns-import-change-breakdown" aria-label="Desglose de cambios seleccionados">
            <span><strong>{selectedNewCount}</strong> Nuevos por crear</span>
            <span><strong>{selectedChangeCount}</strong> Colaboradores por actualizar</span>
            <span><strong>{selectedLows}</strong> Posibles bajas</span>
          </div>
        </section>

        {preview.newStudents.length > 0 && <section className="editor-card"><div className="section-heading"><div><p className="eyebrow">Altas</p><h2>Nuevos colaboradores pendientes de completar</h2></div></div>
          <p className="muted">El correo es obligatorio. El Usuario corporativo es opcional y solo se habilita cuando existe Fecha de alta. {preview.manualStudentCode ? 'Captura también el Código a nivel organización.' : 'El Código a nivel organización se generará automáticamente al confirmar.'}</p>
          <div className="ns-data-table-wrap"><table className="ns-data-table"><thead><tr><th>Crear</th><th>Nombre completo</th><th>Perfil</th><th>Tecnología principal</th><th>Fecha de alta</th><th>Código a nivel organización</th><th>Usuario corporativo</th><th>Correo</th></tr></thead><tbody>
            {newRows.map((row) => <tr key={row.rowKey} className={!row.selected || omittedRows.has(row.rowKey) ? 'is-muted' : undefined}>
              <td><input type="checkbox" checked={row.selected} onChange={(event) => setImportRowSelected(row.rowKey, event.target.checked)} /></td>
              <td><strong>{formatPersonName(row.collaborator)}</strong>
                {row.warnings.map((warning) => <small key={warning} className="warning-text">{warning}</small>)}</td>
              <td>{row.profile || 'N/A'}</td><td>{row.primaryTechnology || 'N/A'}</td><td>{row.admissionDate || 'N/A'}</td>
              <td>{preview.manualStudentCode ? <label className="ns-import-email-field"><span className="sr-only">Código a nivel organización de la fila {row.row}</span><input
                value={row.studentCode} disabled={!row.selected || omittedRows.has(row.rowKey)} placeholder="Código obligatorio" autoComplete="off" maxLength={80}
                onChange={(event) => setNewRows((current) => current.map((item) => item.rowKey === row.rowKey ? { ...item, studentCode: event.target.value.toUpperCase() } : item))} /></label> : <span className="muted">Se generará automáticamente</span>}</td>
              <td><label className="ns-import-email-field"><span className="sr-only">Usuario corporativo de la fila {row.row}</span><input
                value={row.corporateUser} disabled={!row.selected || omittedRows.has(row.rowKey) || !row.admissionDate}
                placeholder={row.admissionDate ? 'Opcional' : 'Requiere Fecha de alta'} autoComplete="off" maxLength={100}
                onChange={(event) => setNewRows((current) => current.map((item) => item.rowKey === row.rowKey ? { ...item, corporateUser: event.target.value.toUpperCase() } : item))} /></label></td>
              <td><label className="ns-import-email-field"><span className="sr-only">Correo de la fila {row.row}</span><input
                className={`ns-import-email-input${row.selected && !omittedRows.has(row.rowKey) && row.email && !validEmail(row.email) ? ' is-invalid' : ''}`}
                type="email" value={row.email} disabled={!row.selected || omittedRows.has(row.rowKey)} placeholder="nombre@dominio.com" autoComplete="off"
                onChange={(event) => setNewRows((current) => current.map((item) => item.rowKey === row.rowKey ? { ...item, email: event.target.value } : item))} /></label></td>
            </tr>)}
          </tbody></table></div>{newRowError && <div className="error-message" role="alert">{newRowError}</div>}
        </section>}

        {preview.changedStudents.length > 0 && <section className="editor-card"><div className="section-heading"><div><p className="eyebrow">Actualizaciones</p><h2>Colaboradores con cambios</h2></div></div>
          <div className="ns-import-conflict-tabs" role="tablist" aria-label="Clasificación de actualizaciones">
            <button type="button" role="tab" aria-selected={changeTab === 'PENDING'} className={changeTab === 'PENDING' ? 'is-active' : undefined} onClick={() => setChangeTab('PENDING')}>
              Actualizaciones pendientes <span>{pendingChangeCount}</span>
            </button>
            {reusedChangeCount > 0 && <button type="button" role="tab" aria-selected={changeTab === 'REUSED'} className={changeTab === 'REUSED' ? 'is-active' : undefined} onClick={() => setChangeTab('REUSED')}>
              Actualizadas previamente <span>{reusedChangeCount}</span>
            </button>}
          </div>
          {changedRows.map((row) => {
            const visibleChanges = row.changes.filter((change) => change.reusedDecision === (changeTab === 'REUSED'))
            if (visibleChanges.length === 0) return null
            if (changeTab === 'REUSED' && (!row.selected || omittedRows.has(row.rowKey))) return null
            return <article className={`ns-import-change-card${!row.selected || omittedRows.has(row.rowKey) ? ' is-muted' : ''}`} key={`${changeTab}-${row.rowKey}`}>
              <div className="ns-import-change-selection">{changeTab === 'PENDING' && <label><input type="checkbox" checked={row.selected} onChange={(event) => setImportRowSelected(row.rowKey, event.target.checked)} /><span>Incluir colaborador</span></label>}<h3>{formatPersonName(row.collaborator)}</h3></div>
              {row.warnings.map((warning) => <p className="warning-text" key={warning}>{warning}</p>)}<div className="ns-data-table-wrap"><table className="ns-data-table"><thead><tr><th>Campo</th><th>Valor actual</th><th>Valor del Excel o calculado</th><th>Decisión</th></tr></thead><tbody>
              {visibleChanges.map((change) => <tr key={change.key}><td>{change.field}</td><td>{change.currentValue}</td><td>{change.excelValue}</td><td>{change.reusedDecision
                ? <div className="ns-import-reused-decision compact"><strong>Decisión aplicada previamente</strong><span>{change.resolvedAction === 'APPLY_EXCEL' ? 'Aplicar información del Excel' : 'Mantener información de la plataforma'}</span><small>No se procesará nuevamente; se conserva el estado actual de la plataforma.</small></div>
                : <SelectField value={row.selectedFields.has(change.key) ? 'APPLY_EXCEL' : 'KEEP_PLATFORM'} disabled={!row.selected || omittedRows.has(row.rowKey)} onChange={(nextValue) => setChangeDecision(row.rowKey, change.key, nextValue as 'KEEP_PLATFORM' | 'APPLY_EXCEL')} ariaLabel={`Decisión para ${change.field}`} options={[{ value: 'KEEP_PLATFORM', label: 'Mantener información de la plataforma' }, { value: 'APPLY_EXCEL', label: 'Aplicar información del Excel' }]} />}</td></tr>)}
            </tbody></table></div></article>
          })}
        </section>}

        {activeConflicts.length > 0 && <section className="editor-card"><div className="section-heading"><div><p className="eyebrow">Validación</p><h2>Conflictos</h2></div></div>
          <div className="ns-import-conflict-tabs" role="tablist" aria-label="Clasificación de conflictos">
            <button type="button" role="tab" aria-selected={conflictTab === 'PENDING'} className={conflictTab === 'PENDING' ? 'is-active' : undefined} onClick={() => setConflictTab('PENDING')}>
              Conflictos pendientes <span>{pendingConflicts.length}</span>
            </button>
            {reusedConflicts.length > 0 && <button type="button" role="tab" aria-selected={conflictTab === 'REUSED'} className={conflictTab === 'REUSED' ? 'is-active' : undefined} onClick={() => setConflictTab('REUSED')}>
              Resueltos previamente <span>{reusedConflicts.length}</span>
            </button>}
          </div>
          <div className="ns-import-conflict-list">{(conflictTab === 'PENDING' ? pendingConflicts : reusedConflicts).map((conflict) => {
            const decision = conflictDecisions[conflict.id] ?? ''
            const selectedAction = conflict.actions.find((action) => action.value === decision)
            return <article className="ns-import-conflict-card" key={conflict.id}>
              <div className="ns-import-conflict-heading"><div><span>Fila {conflict.row}</span><h3>{conflict.title}</h3><p>{formatPersonName(conflict.collaborator)}</p></div><span className={`status-badge ${decision ? 'active' : 'warning'}`}>{conflict.reusedDecision ? 'Resuelto previamente' : decision ? 'Resuelto' : 'Pendiente'}</span></div>
              <div className="ns-import-conflict-values"><div><strong>Excel</strong><span>{conflict.excelValue}</span></div><div><strong>Plataforma actual</strong><span>{conflict.currentValue}</span></div><div><strong>Cálculo de la plataforma</strong><span>{conflict.calculatedValue}</span></div></div>
              <p className="ns-import-conflict-reason">{conflict.reason}</p>
              {conflict.reusedDecision
                ? <div className="ns-import-reused-decision"><strong>{conflict.applyResolution ? 'Decisión aplicada automáticamente' : 'Decisión atendida previamente'}</strong><span>{selectedAction?.label || 'Decisión anterior aplicada'}</span><small>{conflict.applyResolution ? selectedAction?.description : 'Se conserva la modificación manual actual y no se reaplica el resultado anterior.'}</small></div>
                : <label className="field"><span>Selecciona cómo proceder</span><SelectField value={decision}
                  onChange={(nextValue) => setConflictDecision(conflict.id, nextValue as StudentImportConflictActionValue)}
                  ariaLabel="Selecciona cómo proceder"
                  options={[{ value: '', label: 'Selecciona una decisión' }, ...conflict.actions.map((action) => ({ value: action.value, label: action.label }))]} /></label>}
              {!conflict.reusedDecision && decision && <p className="muted">{selectedAction?.description} Puedes cambiar esta decisión antes de confirmar la importación.</p>}
              {!conflict.reusedDecision && decision && activeConflicts.filter((item) => item.groupKey === conflict.groupKey && !item.reusedDecision).length > 1 && <button type="button" className="secondary-button compact-button" onClick={() => applyEquivalentDecision(conflict.id)}>Aplicar esta decisión a casos equivalentes</button>}
            </article>
          })}</div>
        </section>}

        {visibleWarnings.length > 0 && <section className="editor-card"><div className="section-heading"><div><p className="eyebrow">Información</p><h2>Advertencias</h2></div></div><p className="muted">Estas situaciones no impiden continuar.</p><ul className="ns-import-issues warning-list">{visibleWarnings.map((issue, index) => <li key={`${issue.code}-${issue.row}-${index}`}><strong>{issue.row > 0 ? `Fila ${issue.row}: ` : ''}</strong>{issue.message}</li>)}</ul></section>}

        {visibleErrors.length > 0 && <section className="editor-card"><div className="section-heading"><div><p className="eyebrow">Corrección requerida</p><h2>Filas con error</h2></div></div><p className="muted">El error afecta únicamente al colaborador indicado y no detiene los demás registros válidos.</p><ul className="ns-import-issues">{visibleErrors.map((issue, index) => <li key={`${issue.code}-${issue.row}-${index}`}><strong>Fila {issue.row}: </strong>{issue.message}</li>)}</ul></section>}

        {preview.possibleLows.length > 0 && <section className="editor-card"><div className="section-heading"><div><p className="eyebrow">Revisión</p><h2>Posibles bajas</h2></div></div><p className="muted">La desactivación solo se ejecuta cuando la seleccionas expresamente.</p><div className="ns-data-table-wrap"><table className="ns-data-table"><thead><tr><th>Colaborador</th><th>Correo</th><th>Acción</th></tr></thead><tbody>{preview.possibleLows.map((row) => <tr key={row.studentPublicId}><td>{formatPersonName(row.collaborator)}</td><td>{row.email}</td><td><SelectField value={lowActions[row.studentPublicId] ?? 'KEEP'} onChange={(nextValue) => setLowActions((current) => ({ ...current, [row.studentPublicId]: nextValue as LowAction }))} ariaLabel={`Acción para ${formatPersonName(row.collaborator)}`} options={[{ value: 'KEEP', label: 'Mantener activo' }, { value: 'IGNORE', label: 'Ignorar' }, { value: 'DEACTIVATE', label: 'Desactivar' }]} /></td></tr>)}</tbody></table></div></section>}

        <FormActions className="ns-import-final-actions" sticky>
          <button type="button" className="secondary-button" disabled={applying} onClick={() => void changeFile(undefined)}>
            Descartar
          </button>
          <button type="button" className="primary-button" disabled={Boolean(blockingMessage) || applying} onClick={() => {
            if (!hasAnythingToConfirm) {
              toast.info('Sin cambios nuevos', 'El archivo no contiene cambios nuevos para aplicar.')
              return
            }
            setConfirmOpen(true)
          }}>
            Aplicar cambios seleccionados
          </button>
        </FormActions>
        {blockingMessage && <div className="error-message" role="alert">{blockingMessage}</div>}
      </>}

      <ConfirmDialog
        open={confirmOpen && Boolean(preview)}
        title="Confirmar importación"
        description="Revisa el alcance de la operación antes de aplicar los cambios."
        confirmLabel="Confirmar importación"
        busy={applying}
        confirmDisabled={Boolean(blockingMessage)}
        onCancel={() => setConfirmOpen(false)}
        onConfirm={() => void applyChanges()}
      >
        {preview && (
          <div className="ns-import-confirm-summary">
            <p>
              Se crearán <strong>{selectedNewCount}</strong> colaboradores,
              se actualizarán <strong>{selectedChangeCount}</strong> y
              se desactivarán <strong>{selectedLows}</strong> posibles bajas.
            </p>
            <p className="muted">
              Se excluirán {excludedRows} colaboradores.
              Las advertencias no bloquean el proceso.
            </p>
          </div>
        )}
      </ConfirmDialog>
    </main>
  )
}
