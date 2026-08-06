import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ApiRequestError } from '../shared/api/apiClient'
import { createRole, getPermissionCatalog, getRole, updateRole } from '../features/roles/api/roleApi'
import { BackButton } from '../shared/components/BackButton'
import { FormActions } from '../shared/components/FormActions'
import { Icon } from '../shared/components/Icon'
import { useToast } from '../shared/components/ToastProvider'
import type { PermissionCatalog, PermissionModule, RoleDetail } from '../shared/types/roles'

interface RoleEditorPageProps { mode: 'create' | 'edit' | 'view' }

interface SelectAllCheckboxProps {
  checked: boolean
  indeterminate: boolean
  onChange: (checked: boolean) => void
}

function SelectAllCheckbox({ checked, indeterminate, onChange }: SelectAllCheckboxProps) {
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (inputRef.current) inputRef.current.indeterminate = indeterminate
  }, [indeterminate])

  return <input
    ref={inputRef}
    type="checkbox"
    checked={checked}
    aria-checked={indeterminate ? 'mixed' : checked}
    onChange={(event) => onChange(event.target.checked)}
  />
}

function moduleViewPermission(module: PermissionModule) {
  return module.permissions.find((permission) => permission.viewPermission)?.code
}

function requiredViewPermission(code: string) {
  if (code.startsWith('TALENT_')) return 'TALENT_VIEW'
  if (code.startsWith('STUDENT_CERTIFICATION_')) return 'STUDENT_VIEW'
  if (code.startsWith('STUDENT_')) return 'STUDENT_VIEW'
  if (code.startsWith('FORM_')) return 'FORM_VIEW'
  if (code.startsWith('COLLECTION_')) return 'COLLECTION_VIEW'
  if (code.startsWith('QUESTION_') || code.startsWith('GLOBAL_CONTENT_')) return 'QUESTION_VIEW'
  if (code.startsWith('CATALOG_')) return 'CATALOG_VIEW'
  if (code.startsWith('PROFILE_')) return 'PROFILE_VIEW'
  return undefined
}

function configurablePermissions(module: PermissionModule) {
  return module.permissions.filter((permission) => !permission.required)
}

function basicPermissions(module: PermissionModule) {
  return module.permissions.filter((permission) => permission.required)
}

function permissionCountLabel(module: PermissionModule, selected: Set<string>, protectedRole: boolean) {
  if (protectedRole) return `Acceso completo · ${module.permissions.length} permisos incluidos`
  const configurable = configurablePermissions(module)
  const basics = basicPermissions(module)
  const selectedCount = configurable.filter((permission) => selected.has(permission.code)).length
  if (basics.length === 0) return `${selectedCount} de ${configurable.length} permisos seleccionados`
  if (configurable.length === 0) {
    return basics.length === 1 ? '1 permiso básico incluido' : `${basics.length} permisos básicos incluidos`
  }
  const configurableLabel = configurable.length === 1 ? 'permiso configurable seleccionado' : 'permisos configurables seleccionados'
  const basicLabel = basics.length === 1 ? 'permiso básico incluido' : 'permisos básicos incluidos'
  return `${selectedCount} de ${configurable.length} ${configurableLabel} · ${basics.length} ${basicLabel}`
}

export function RoleEditorPage({ mode }: RoleEditorPageProps) {
  const { roleCode } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const readOnly = mode === 'view'
  const [role, setRole] = useState<RoleDetail | null>(null)
  const [catalog, setCatalog] = useState<PermissionCatalog>({ modules: [] })
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [expanded, setExpanded] = useState<Set<string>>(new Set())
  const [query, setQuery] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [fieldError, setFieldError] = useState('')

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError('')

    async function load() {
      const currentRole = mode === 'create' ? null : await getRole(roleCode ?? '')
      const permissionCatalog = await getPermissionCatalog(currentRole?.protectedRole ? 'ADMINISTRATOR' : 'ORGANIZATIONAL')
      if (cancelled) return

      setCatalog(permissionCatalog)
      const requiredPermissions = permissionCatalog.modules.flatMap((module) =>
        basicPermissions(module).map((permission) => permission.code))
      setExpanded(new Set(permissionCatalog.modules.map((module) => module.code)))
      if (currentRole) {
        setRole(currentRole)
        setName(currentRole.name)
        setDescription(currentRole.description ?? '')
        setSelected(new Set([...currentRole.permissionCodes, ...requiredPermissions]))
      } else {
        setSelected(new Set(requiredPermissions))
      }
    }

    void load().catch((requestError) => {
      if (!cancelled) setError(requestError instanceof ApiRequestError ? requestError.message : 'No fue posible cargar el rol.')
    }).finally(() => { if (!cancelled) setLoading(false) })

    return () => { cancelled = true }
  }, [mode, roleCode])

  const visibleModules = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase('es-MX')
    if (!normalized) return catalog.modules
    return catalog.modules.filter((module) => module.name.toLocaleLowerCase('es-MX').includes(normalized)
      || module.permissions.some((permission) => `${permission.name} ${permission.description ?? ''}`.toLocaleLowerCase('es-MX').includes(normalized)))
  }, [catalog.modules, query])

  const totals = useMemo(() => {
    const configurable = catalog.modules.flatMap(configurablePermissions)
    const basics = catalog.modules.flatMap(basicPermissions)
    return {
      configurable: configurable.length,
      selectedConfigurable: configurable.filter((permission) => selected.has(permission.code)).length,
      basics: basics.length
    }
  }, [catalog.modules, selected])

  function togglePermission(module: PermissionModule, code: string, checked: boolean) {
    if (readOnly || role?.protectedRole) return
    const target = module.permissions.find((permission) => permission.code === code)
    if (!target || target.required) return
    const allPermissions = catalog.modules.flatMap((entry) => entry.permissions)
    const availableCodes = new Set(allPermissions.map((permission) => permission.code))
    setSelected((current) => {
      const next = new Set(current)
      if (checked) {
        next.add(code)
        const viewCode = requiredViewPermission(code) ?? moduleViewPermission(module)
        if (viewCode && availableCodes.has(viewCode)) next.add(viewCode)
      } else {
        next.delete(code)
        if (target.viewPermission) {
          allPermissions
            .filter((permission) => requiredViewPermission(permission.code) === code)
            .forEach((permission) => next.delete(permission.code))
        }
      }
      return next
    })
  }

  function toggleModule(module: PermissionModule, checked: boolean) {
    if (readOnly || role?.protectedRole) return
    const allPermissions = catalog.modules.flatMap((entry) => entry.permissions)
    const availableCodes = new Set(allPermissions.map((permission) => permission.code))
    setSelected((current) => {
      const next = new Set(current)
      configurablePermissions(module).forEach((permission) => {
        if (checked) {
          next.add(permission.code)
          const viewCode = requiredViewPermission(permission.code)
          if (viewCode && availableCodes.has(viewCode)) next.add(viewCode)
        } else {
          next.delete(permission.code)
          if (permission.viewPermission) {
            allPermissions
              .filter((candidate) => requiredViewPermission(candidate.code) === permission.code)
              .forEach((candidate) => next.delete(candidate.code))
          }
        }
      })
      basicPermissions(module).forEach((permission) => next.add(permission.code))
      return next
    })
  }

  function selectAll(checked: boolean) {
    if (readOnly || role?.protectedRole) return
    const basics = catalog.modules.flatMap((module) => basicPermissions(module).map((permission) => permission.code))
    const configurable = catalog.modules.flatMap((module) => configurablePermissions(module).map((permission) => permission.code))
    setSelected(new Set(checked ? [...basics, ...configurable] : basics))
  }

  async function save() {
    if (saving || readOnly) return
    const normalizedName = name.trim()
    if (!normalizedName) {
      setFieldError('El nombre del rol es obligatorio.')
      return
    }
    setFieldError('')
    setSaving(true)
    try {
      const configurableCodes = new Set(catalog.modules.flatMap((module) =>
        configurablePermissions(module).map((permission) => permission.code)))
      const request = {
        name: normalizedName,
        description: description.trim() || undefined,
        permissionCodes: Array.from(selected).filter((code) => configurableCodes.has(code))
      }
      if (mode === 'create') {
        await createRole(request)
        toast.success('Rol creado', 'El rol y sus permisos se crearon correctamente.')
      } else {
        await updateRole(roleCode ?? '', request)
        toast.success('Rol actualizado', 'El rol y sus permisos se actualizaron correctamente.')
      }
      navigate('/admin/roles', { replace: true })
    } catch (requestError) {
      if (requestError instanceof ApiRequestError && requestError.fieldErrors?.name) setFieldError(requestError.fieldErrors.name)
      toast.error('No fue posible guardar el rol.', requestError instanceof ApiRequestError ? requestError.message : undefined)
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <main className="content-page"><section className="editor-card">Cargando configuración del rol…</section></main>
  if (error) return <main className="content-page"><section className="inline-error-panel" role="alert"><Icon name="error" /><div><strong>No fue posible abrir el rol</strong><p>{error}</p></div><BackButton fallback="/admin/roles" label="Regresar" /></section></main>

  const protectedRole = role?.protectedRole ?? false
  return <main className="content-page role-editor-page">
    <div className="editor-page-navigation"><BackButton fallback="/admin/roles" label="Regresar" /></div>

    <section className="editor-card role-basic-card">
      {readOnly || protectedRole ? (
        <dl className="role-readonly-summary">
          <div><dt>Nombre del rol</dt><dd>{name}</dd></div>
          <div><dt>Estado</dt><dd>{protectedRole ? 'Protegido' : role?.status === 'ACTIVE' ? 'Activo' : 'Inactivo'}</dd></div>
          <div className="role-readonly-summary-wide"><dt>Descripción</dt><dd>{description || 'Sin descripción'}</dd></div>
        </dl>
      ) : (
        <div className="form-grid two-columns">
          <label className="field-group"><span>Nombre del rol</span><input value={name} maxLength={100} onChange={(event) => setName(event.target.value)} aria-invalid={Boolean(fieldError)} />{fieldError && <small className="field-error">{fieldError}</small>}</label>
          <label className="field-group"><span>Descripción</span><input value={description} maxLength={500} onChange={(event) => setDescription(event.target.value)} placeholder="Describe el propósito del rol" /></label>
        </div>
      )}
      {protectedRole && <div className="protected-role-banner"><Icon name="lock" /><div><strong>Rol protegido</strong><p>Acceso completo a todos los módulos, vistas, botones y acciones. Esta configuración es exclusivamente de consulta.</p></div></div>}
    </section>

    <section className="editor-card permission-matrix-card">
      <header className="permission-matrix-header"><div><h2>Permisos por módulo</h2><p>Las acciones particulares se muestran únicamente donde existen.</p><small className="permission-total-summary">{protectedRole ? `Acceso completo a ${catalog.modules.reduce((total, module) => total + module.permissions.length, 0)} permisos` : `${totals.selectedConfigurable} de ${totals.configurable} permisos configurables seleccionados · ${totals.basics} permisos básicos incluidos`}</small></div>{!readOnly && !protectedRole && <div className="permission-global-actions"><button className="secondary-button compact-button" type="button" onClick={() => selectAll(true)}>Seleccionar todos</button><button className="secondary-button compact-button" type="button" onClick={() => selectAll(false)}>Limpiar todos</button></div>}</header>
      <div className="permission-search"><Icon name="search" size={17} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Buscar módulo o permiso" aria-label="Buscar módulo o permiso" /></div>
      <div className="permission-module-list">
        {visibleModules.length === 0 && <div className="empty-state"><strong>No se encontraron permisos</strong><p>Prueba con otro término de búsqueda.</p></div>}
        {visibleModules.map((module) => {
          const open = expanded.has(module.code)
          const configurable = configurablePermissions(module)
          const selectedCount = configurable.filter((permission) => selected.has(permission.code)).length
          const allSelected = configurable.length > 0 && selectedCount === configurable.length
          const partiallySelected = selectedCount > 0 && selectedCount < configurable.length
          return <article className="permission-module" key={module.code}>
            <header><button className="permission-module-trigger" type="button" aria-expanded={open} onClick={() => setExpanded((current) => { const next = new Set(current); if (next.has(module.code)) next.delete(module.code); else next.add(module.code); return next })}><Icon name={open ? 'chevronDown' : 'chevronRight'} size={17} /><span><strong>{module.name}</strong><small>{permissionCountLabel(module, selected, protectedRole)}</small></span></button>
              {!readOnly && !protectedRole && configurable.length > 0 && <label className="permission-select-all"><SelectAllCheckbox checked={allSelected} indeterminate={partiallySelected} onChange={(checked) => toggleModule(module, checked)} /> Todos</label>}
            </header>
            {open && <div className="permission-options">{module.permissions.map((permission) => {
              const granted = protectedRole || selected.has(permission.code)
              const typeLabel = permission.required ? 'Básico' : permission.actionType === 'SPECIAL' ? 'Particular' : permission.actionType === 'VIEW' ? 'Ver' : permission.actionType === 'CREATE' ? 'Agregar' : permission.actionType === 'UPDATE' ? 'Editar' : permission.actionType === 'DELETE' ? 'Eliminar' : 'Inactivar'
              return readOnly || protectedRole ? (
                <div className={`permission-option permission-readonly permission-${permission.actionType.toLowerCase()}${permission.required ? ' permission-basic' : ''}${granted ? ' is-granted' : ' is-not-granted'}`} key={permission.code}>
                  <span className="permission-readonly-state"><Icon name={granted ? 'check' : 'close'} size={16} /></span>
                  <span><strong>{permission.name}</strong>{permission.description && <small>{permission.description}</small>}</span>
                  <em>{permission.required ? 'Básico incluido' : granted ? `${typeLabel} autorizado` : `${typeLabel} no autorizado`}</em>
                </div>
              ) : (
                <label className={`permission-option permission-${permission.actionType.toLowerCase()}${permission.required ? ' permission-basic' : ''}`} key={permission.code}><input type="checkbox" checked={selected.has(permission.code)} disabled={permission.required} onChange={(event) => togglePermission(module, permission.code, event.target.checked)} /><span><strong>{permission.name}</strong>{permission.description && <small>{permission.description}</small>}</span><em>{typeLabel}</em></label>
              )
            })}</div>}
          </article>
        })}
      </div>
    </section>

    {!readOnly && !protectedRole && <FormActions sticky>
      <button className="secondary-button" type="button" disabled={saving} onClick={() => navigate('/admin/roles')}>Cancelar</button>
      <button className="primary-button" type="button" disabled={saving} onClick={() => void save()}>{saving ? 'Guardando…' : mode === 'create' ? 'Crear rol' : 'Guardar cambios'}</button>
    </FormActions>}
  </main>
}
