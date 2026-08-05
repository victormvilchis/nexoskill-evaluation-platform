import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { useParams } from 'react-router-dom'
import { searchOrganizations } from '../features/organizations/api/organizationApi'
import { getRoles, getUser, updateUser } from '../features/users/api/userApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { BackButton } from '../shared/components/BackButton'
import { SelectField } from '../shared/components/SelectField'
import { LoadingScreen } from '../shared/components/LoadingScreen'
import { useSaveNavigation } from '../shared/hooks/useSaveNavigation'
import type { OrganizationSummary } from '../features/organizations/types/organizations'
import type { AdminUser, InternalRoleCode, RoleOption } from '../shared/types/users'

interface AdminUserDetailPageProps {
  mode?: 'view' | 'edit'
}

function formatDate(value: string | null, fallback = 'Sin registro') {
  if (!value) return fallback
  return new Intl.DateTimeFormat('es-MX', {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(new Date(value))
}

const roleLabels: Record<string, string> = {
  ADMINISTRATOR: 'Administrador',
  MANAGER: 'Gestor',
  SUPERVISOR: 'Supervisor'
}

const statusLabels: Record<string, string> = {
  ACTIVE: 'Activo',
  INACTIVE: 'Inactivo',
  SUSPENDED: 'Suspendido',
  DELETED: 'Eliminado'
}

export function AdminUserDetailPage({ mode = 'view' }: AdminUserDetailPageProps) {
  const { publicId } = useParams()
  const completeSave = useSaveNavigation('/admin/users')
  const editing = mode === 'edit'
  const [user, setUser] = useState<AdminUser | null>(null)
  const [roles, setRoles] = useState<RoleOption[]>([])
  const [organizations, setOrganizations] = useState<OrganizationSummary[]>([])
  const [email, setEmail] = useState('')
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [roleCode, setRoleCode] = useState<InternalRoleCode>('MANAGER')
  const [organizationPublicId, setOrganizationPublicId] = useState('')
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  useEffect(() => {
    if (!publicId) return
    let active = true
    setLoading(true)
    setError(null)

    Promise.all([
      getUser(publicId),
      getRoles(),
      editing ? searchOrganizations({ status: 'ACTIVE', page: 0, size: 100 }) : Promise.resolve(null)
    ])
      .then(([response, roleOptions, organizationPage]) => {
        if (!active) return
        setUser(response)
        setRoles(roleOptions)
        setOrganizations(organizationPage?.content ?? [])
        setEmail(response.email)
        setFirstName(response.firstName)
        setLastName(response.lastName)
        setDisplayName(response.displayName)
        setRoleCode(response.roles[0] ?? 'MANAGER')
        setOrganizationPublicId(response.organizationPublicId ?? '')
      })
      .catch((requestError) => {
        if (!active) return
        setError(requestError instanceof ApiRequestError
          ? requestError.message
          : 'No fue posible cargar el usuario.')
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => { active = false }
  }, [editing, publicId])

  const globalOrganization = useMemo(
    () => organizations.find((organization) => organization.organizationType === 'GLOBAL'),
    [organizations]
  )
  const customerOrganizations = useMemo(() => {
    const today = new Date().toISOString().slice(0, 10)
    return organizations.filter((organization) =>
      organization.organizationType === 'CUSTOMER'
      && (!organization.expiresOn || organization.expiresOn >= today)
    )
  }, [organizations])

  useEffect(() => {
    if (!editing) return
    if (roleCode === 'ADMINISTRATOR') {
      setOrganizationPublicId(globalOrganization?.publicId ?? '')
      return
    }
    if (!customerOrganizations.some((organization) => organization.publicId === organizationPublicId)) {
      setOrganizationPublicId('')
    }
  }, [editing, roleCode, globalOrganization, customerOrganizations, organizationPublicId])

  const organizationOptions = roleCode === 'ADMINISTRATOR'
    ? (globalOrganization ? [globalOrganization] : [])
    : customerOrganizations
  const roleName = useMemo(
    () => roles.find((item) => item.code === user?.roles[0])?.name ?? roleLabels[user?.roles[0] ?? ''] ?? user?.roles[0] ?? 'Sin rol',
    [roles, user]
  )

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!editing || !publicId || submitting || !user) return
    setError(null)
    setFieldErrors({})

    if (!organizationPublicId) {
      setFieldErrors({ organizationPublicId: 'Selecciona una organización.' })
      return
    }

    setSubmitting(true)
    try {
      const updated = await updateUser(publicId, {
        email: email.trim(),
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        displayName: displayName.trim() || undefined,
        roleCode,
        organizationPublicId,
        startsAt: user.startsAt,
        expiresAt: user.expiresAt
      })
      setUser(updated)
      completeSave({
        title: 'Usuario actualizado correctamente.',
        message: 'Los cambios se guardaron correctamente.'
      })
    } catch (requestError) {
      if (requestError instanceof ApiRequestError) {
        setError(requestError.message)
        setFieldErrors(requestError.fieldErrors ?? {})
      } else {
        setError('No fue posible guardar los cambios.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) return <LoadingScreen />

  if (!user || error && !editing) {
    return (
      <main className="content-page">
        <BackButton fallback="/admin/users" />
        <div className="error-message" role="alert">{error ?? 'El usuario no está disponible.'}</div>
      </main>
    )
  }

  if (!editing) {
    return (
      <main className="content-page resource-page internal-user-detail-page">
        <BackButton fallback="/admin/users" />
        <section className="user-overview-grid">
          <article className="summary-card"><span>Estado</span><strong>{statusLabels[user.status]}</strong></article>
          <article className="summary-card"><span>Rol</span><strong>{roleName}</strong></article>
          <article className="summary-card"><span>Organización</span><strong>{user.organizationName ?? 'Global'}</strong></article>
          <article className="summary-card"><span>Último acceso</span><strong>{formatDate(user.lastLoginAt)}</strong></article>
        </section>

        <section className="detail-card internal-user-readonly-grid">
          <div><span>Nombre</span><strong>{user.firstName}</strong></div>
          <div><span>Apellidos</span><strong>{user.lastName}</strong></div>
          <div><span>Correo electrónico</span><strong>{user.email}</strong></div>
          <div><span>Nombre para mostrar</span><strong>{user.displayName}</strong></div>
          <div><span>Fecha de creación</span><strong>{formatDate(user.createdAt)}</strong></div>
          <div><span>Último cambio de estado</span><strong>{formatDate(user.statusChangedAt)}</strong></div>
          <div className="form-wide"><span>Motivo de estado</span><strong>{user.statusReason || 'Sin motivo registrado'}</strong></div>
        </section>
      </main>
    )
  }

  return (
    <main className="content-page resource-page internal-user-editor-page">
      <BackButton fallback={`/admin/users/${publicId}`} />
      <form className="entity-form internal-user-form" onSubmit={(event) => void handleSubmit(event)}>
        <section className="form-section form-wide">
          <div className="form-section-heading">
            <span className="form-section-number">1</span>
            <div><h2>Datos generales</h2><p>Información de identificación y contacto.</p></div>
          </div>
          <div className="form-grid-three">
            <label className="form-field"><span>Nombre</span><input value={firstName} required onChange={(event) => setFirstName(event.target.value)} />{fieldErrors.firstName && <small className="field-error">{fieldErrors.firstName}</small>}</label>
            <label className="form-field"><span>Apellidos</span><input value={lastName} required onChange={(event) => setLastName(event.target.value)} />{fieldErrors.lastName && <small className="field-error">{fieldErrors.lastName}</small>}</label>
            <label className="form-field"><span>Nombre para mostrar</span><input value={displayName} onChange={(event) => setDisplayName(event.target.value)} />{fieldErrors.displayName && <small className="field-error">{fieldErrors.displayName}</small>}</label>
          </div>
          <label className="form-field"><span>Correo electrónico</span><input type="email" value={email} required onChange={(event) => setEmail(event.target.value)} />{fieldErrors.email && <small className="field-error">{fieldErrors.email}</small>}</label>
        </section>

        <section className="form-section form-wide">
          <div className="form-section-heading">
            <span className="form-section-number">2</span>
            <div><h2>Rol y organización</h2><p>Todos los usuarios internos pertenecen obligatoriamente a una organización.</p></div>
          </div>
          <div className="internal-user-role-grid">
            <label className="form-field">
              <span>Rol</span>
              <SelectField value={roleCode} onChange={(nextValue) => setRoleCode(nextValue as InternalRoleCode)} ariaLabel="Rol"
                options={roles.map((role) => ({ value: role.code, label: role.name }))} />
            </label>
            <label className="form-field">
              <span>Organización</span>
              <SelectField value={organizationPublicId} disabled={roleCode === 'ADMINISTRATOR'} required
                onChange={setOrganizationPublicId} ariaLabel="Organización"
                options={[
                  { value: '', label: 'Seleccionar organización' },
                  ...organizationOptions.map((organization) => ({ value: organization.publicId, label: `${organization.name} · ${organization.code}` }))
                ]} />
              <small>{roleCode === 'ADMINISTRATOR' ? 'Los Administradores pertenecen obligatoriamente a GLOBAL.' : 'Solo se muestran organizaciones comerciales activas y vigentes.'}</small>
              {fieldErrors.organizationPublicId && <small className="field-error">{fieldErrors.organizationPublicId}</small>}
            </label>
          </div>
        </section>


        {error && <div className="error-message form-wide" role="alert">{error}</div>}
        <div className="form-actions form-wide">
          <button className="primary-button" type="submit" disabled={submitting}>{submitting ? 'Guardando…' : 'Guardar cambios'}</button>
        </div>
      </form>
    </main>
  )
}
