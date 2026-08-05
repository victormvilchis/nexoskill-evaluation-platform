import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ApiRequestError } from '../shared/api/apiClient'
import { createRole, getPermissionCatalog, getRole, updateRole } from '../features/roles/api/roleApi'
import { BackButton } from '../shared/components/BackButton'
import { FormActions } from '../shared/components/FormActions'
import { Icon } from '../shared/components/Icon'
import { useToast } from '../shared/components/ToastProvider'
import type { PermissionCatalog, PermissionModule, RoleDetail } from '../shared/types/roles'

interface RoleEditorPageProps { mode: 'create' | 'edit' | 'view' }

function moduleViewPermission(module: PermissionModule) {
  return module.permissions.find((permission) => permission.viewPermission)?.code
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
    Promise.all([
      getPermissionCatalog(),
      mode === 'create' ? Promise.resolve<RoleDetail | null>(null) : getRole(roleCode ?? '')
    ]).then(([permissionCatalog, currentRole]) => {
      if (cancelled) return
      setCatalog(permissionCatalog)
      const requiredPermissions = permissionCatalog.modules.flatMap((module) =>
        module.permissions.filter((permission) => permission.required).map((permission) => permission.code))
      setExpanded(new Set(permissionCatalog.modules.map((module) => module.code)))
      if (currentRole) {
        setRole(currentRole)
        setName(currentRole.name)
        setDescription(currentRole.description ?? '')
        setSelected(new Set([...currentRole.permissionCodes, ...requiredPermissions]))
      } else {
        setSelected(new Set(requiredPermissions))
      }
    }).catch((requestError) => {
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

  function togglePermission(module: PermissionModule, code: string, checked: boolean) {
    if (readOnly || role?.protectedRole) return
    const target = module.permissions.find((permission) => permission.code === code)
    if (target?.required && !checked) return
    setSelected((current) => {
      const next = new Set(current)
      const viewCode = moduleViewPermission(module)
      if (checked) {
        next.add(code)
        if (viewCode) next.add(viewCode)
      } else {
        next.delete(code)
        if (code === viewCode) module.permissions.forEach((permission) => next.delete(permission.code))
      }
      return next
    })
  }

  function toggleModule(module: PermissionModule, checked: boolean) {
    if (readOnly || role?.protectedRole) return
    setSelected((current) => {
      const next = new Set(current)
      module.permissions.forEach((permission) => {
        if (checked || permission.required) next.add(permission.code)
        else next.delete(permission.code)
      })
      return next
    })
  }

  function selectAll(checked: boolean) {
    if (readOnly || role?.protectedRole) return
    const required = catalog.modules.flatMap((module) =>
      module.permissions.filter((permission) => permission.required).map((permission) => permission.code))
    setSelected(checked
      ? new Set(catalog.modules.flatMap((module) => module.permissions.map((permission) => permission.code)))
      : new Set(required))
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
      const request = { name: normalizedName, description: description.trim() || undefined, permissionCodes: Array.from(selected) }
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
    <div className="editor-page-heading"><BackButton fallback="/admin/roles" label="Regresar" /><div><span className="eyebrow">Administración · Roles</span><h1>{mode === 'create' ? 'Crear rol' : protectedRole ? 'Acceso del Administrador' : 'Configurar rol'}</h1><p>{protectedRole ? 'El Administrador es un rol protegido y siempre conserva acceso completo.' : 'Define los módulos y acciones que utilizarán todos los usuarios asignados a este rol.'}</p></div></div>

    <section className="editor-card role-basic-card">
      <div className="form-grid two-columns">
        <label className="field-group"><span>Nombre del rol</span><input value={name} maxLength={100} disabled={readOnly || protectedRole} onChange={(event) => setName(event.target.value)} aria-invalid={Boolean(fieldError)} />{fieldError && <small className="field-error">{fieldError}</small>}</label>
        <label className="field-group"><span>Descripción</span><input value={description} maxLength={500} disabled={readOnly || protectedRole} onChange={(event) => setDescription(event.target.value)} placeholder="Describe el propósito del rol" /></label>
      </div>
      {protectedRole && <div className="protected-role-banner"><Icon name="lock" /><div><strong>Rol protegido</strong><p>Acceso completo a todos los módulos, vistas, botones y acciones.</p></div></div>}
    </section>

    <section className="editor-card permission-matrix-card">
      <header className="permission-matrix-header"><div><h2>Permisos por módulo</h2><p>Las acciones particulares se muestran únicamente donde existen.</p></div>{!readOnly && !protectedRole && <div className="permission-global-actions"><button className="secondary-button compact-button" type="button" onClick={() => selectAll(true)}>Seleccionar todos</button><button className="secondary-button compact-button" type="button" onClick={() => selectAll(false)}>Limpiar todos</button></div>}</header>
      <div className="permission-search"><Icon name="search" size={17} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Buscar módulo o permiso" aria-label="Buscar módulo o permiso" /></div>
      <div className="permission-module-list">
        {visibleModules.length === 0 && <div className="empty-state"><strong>No se encontraron permisos</strong><p>Prueba con otro término de búsqueda.</p></div>}
        {visibleModules.map((module) => {
          const open = expanded.has(module.code)
          const selectedCount = module.permissions.filter((permission) => selected.has(permission.code)).length
          const allSelected = selectedCount === module.permissions.length && module.permissions.length > 0
          return <article className="permission-module" key={module.code}>
            <header><button className="permission-module-trigger" type="button" aria-expanded={open} onClick={() => setExpanded((current) => { const next = new Set(current); if (next.has(module.code)) next.delete(module.code); else next.add(module.code); return next })}><Icon name={open ? 'chevronDown' : 'chevronRight'} size={17} /><span><strong>{module.name}</strong><small>{selectedCount} de {module.permissions.length} permisos seleccionados</small></span></button>
              {!readOnly && !protectedRole && <label className="permission-select-all"><input type="checkbox" checked={allSelected} onChange={(event) => toggleModule(module, event.target.checked)} /> Todos</label>}
            </header>
            {open && <div className="permission-options">{module.permissions.map((permission) => <label className={`permission-option permission-${permission.actionType.toLowerCase()}`} key={permission.code}><input type="checkbox" checked={protectedRole || selected.has(permission.code)} disabled={readOnly || protectedRole || permission.required} onChange={(event) => togglePermission(module, permission.code, event.target.checked)} /><span><strong>{permission.name}</strong>{permission.description && <small>{permission.description}</small>}</span><em>{permission.required ? 'Básico' : permission.actionType === 'SPECIAL' ? 'Particular' : permission.actionType === 'VIEW' ? 'Ver' : permission.actionType === 'CREATE' ? 'Agregar' : permission.actionType === 'UPDATE' ? 'Editar' : permission.actionType === 'DELETE' ? 'Eliminar' : 'Inactivar'}</em></label>)}</div>}
          </article>
        })}
      </div>
    </section>

    {!readOnly && !protectedRole && <FormActions sticky>
      <button className="secondary-button" type="button" disabled={saving} onClick={() => navigate('/admin/roles')}>Cancelar</button>
      <button className="primary-button" type="button" disabled={saving} onClick={() => void save()}>{saving ? 'Guardando…' : mode === 'create' ? 'Crear rol' : 'Guardar permisos'}</button>
    </FormActions>}
  </main>
}
