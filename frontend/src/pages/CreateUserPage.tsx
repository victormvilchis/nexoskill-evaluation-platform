import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { searchOrganizations } from '../features/organizations/api/organizationApi'
import { createUser, getRoles } from '../features/users/api/userApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { BackButton } from '../shared/components/BackButton'
import { DateTimeField } from '../shared/components/DateField'
import { useSaveNavigation } from '../shared/hooks/useSaveNavigation'
import type { OrganizationSummary } from '../features/organizations/types/organizations'
import type { InternalRoleCode, RoleOption } from '../shared/types/users'

function defaultStartDate() {
  const now = new Date()
  const offset = now.getTimezoneOffset() * 60_000
  return new Date(now.getTime() - offset).toISOString().slice(0, 16)
}

function toInstant(value: string) {
  return new Date(value).toISOString()
}

export function CreateUserPage() {
  const completeSave = useSaveNavigation('/admin/users')
  const [roles, setRoles] = useState<RoleOption[]>([])
  const [organizations, setOrganizations] = useState<OrganizationSummary[]>([])
  const [email, setEmail] = useState('')
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [roleCode, setRoleCode] = useState<InternalRoleCode>('MANAGER')
  const [organizationPublicId, setOrganizationPublicId] = useState('')
  const [startsAt, setStartsAt] = useState(defaultStartDate)
  const [expiresAt, setExpiresAt] = useState('')
  const [withoutExpiration, setWithoutExpiration] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  useEffect(() => {
    Promise.all([
      getRoles(),
      searchOrganizations({ status: 'ACTIVE', page: 0, size: 100 })
    ])
      .then(([roleOptions, organizationPage]) => {
        const internalRoles = roleOptions.filter((role) =>
          role.code === 'ADMINISTRATOR' || role.code === 'MANAGER' || role.code === 'SUPERVISOR'
        )
        setRoles(internalRoles)
        setOrganizations(organizationPage.content)
        const firstRole = internalRoles[0]
        if (firstRole && !internalRoles.some((role) => role.code === roleCode)) {
          setRoleCode(firstRole.code)
        }
      })
      .catch((requestError) => {
        setError(requestError instanceof ApiRequestError
          ? requestError.message
          : 'No fue posible cargar los catálogos del formulario.')
      })
  }, [])

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
    if (roleCode === 'ADMINISTRATOR') {
      setOrganizationPublicId(globalOrganization?.publicId ?? '')
      return
    }
    if (!customerOrganizations.some((organization) => organization.publicId === organizationPublicId)) {
      setOrganizationPublicId('')
    }
  }, [roleCode, globalOrganization, customerOrganizations, organizationPublicId])

  const organizationRequired = true

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting) return
    setError(null)
    setFieldErrors({})

    const localErrors: Record<string, string> = {}
    if (organizationRequired && !organizationPublicId) {
      localErrors.organizationPublicId = 'Selecciona una organización.'
    }
    if (!startsAt) localErrors.startsAt = 'La fecha de inicio es obligatoria.'
    if (!withoutExpiration && !expiresAt) {
      localErrors.expiresAt = 'Selecciona un vencimiento o marca acceso sin vencimiento.'
    }
    if (!withoutExpiration && startsAt && expiresAt && new Date(expiresAt) <= new Date(startsAt)) {
      localErrors.expiresAt = 'El vencimiento debe ser posterior al inicio.'
    }
    if (Object.keys(localErrors).length > 0) {
      setFieldErrors(localErrors)
      return
    }

    setSubmitting(true)
    try {
      const response = await createUser({
        email: email.trim(),
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        displayName: displayName.trim() || undefined,
        roleCode,
        organizationPublicId,
        startsAt: toInstant(startsAt),
        expiresAt: withoutExpiration ? null : toInstant(expiresAt)
      })
      completeSave({
        title: 'Usuario creado correctamente.',
        message: 'Copia la contraseña temporal; no podrá consultarse nuevamente.',
        state: {
          temporaryCredentials: {
            email: response.user.email,
            password: response.temporaryPassword
          }
        }
      })
    } catch (requestError) {
      if (requestError instanceof ApiRequestError) {
        setError(requestError.message)
        setFieldErrors(requestError.fieldErrors ?? {})
      } else {
        setError('No fue posible crear el usuario.')
      }
    } finally {
      setSubmitting(false)
    }
  }


  return (
    <main className="content-page resource-page create-user-page">
      <BackButton fallback="/admin/users" />
      <header className="ns-page-header">
        <div>
          <p className="eyebrow">Administración · Usuarios</p>
          <h1>Crear usuario interno</h1>
          <p className="muted">La plataforma generará una contraseña temporal segura y la mostrará una sola vez.</p>
        </div>
      </header>

      <form className="entity-form internal-user-form" onSubmit={(event) => void handleSubmit(event)}>
        <section className="form-section form-wide">
          <div className="form-section-heading"><span className="form-section-number">1</span><div><h2>Datos generales</h2><p>Información de identificación y contacto.</p></div></div>
          <div className="form-grid-three">
            <label className="form-field"><span>Nombre</span><input value={firstName} required maxLength={100} onChange={(event) => setFirstName(event.target.value)} />{fieldErrors.firstName && <small className="field-error">{fieldErrors.firstName}</small>}</label>
            <label className="form-field"><span>Apellidos</span><input value={lastName} required maxLength={150} onChange={(event) => setLastName(event.target.value)} />{fieldErrors.lastName && <small className="field-error">{fieldErrors.lastName}</small>}</label>
            <label className="form-field"><span>Nombre para mostrar</span><input value={displayName} maxLength={250} onChange={(event) => setDisplayName(event.target.value)} />{fieldErrors.displayName && <small className="field-error">{fieldErrors.displayName}</small>}</label>
          </div>
          <label className="form-field"><span>Correo electrónico</span><input type="email" value={email} required maxLength={254} onChange={(event) => setEmail(event.target.value)} />{fieldErrors.email && <small className="field-error">{fieldErrors.email}</small>}</label>
        </section>

        <section className="form-section form-wide">
          <div className="form-section-heading"><span className="form-section-number">2</span><div><h2>Rol y organización</h2><p>Solo existen Administrador, Gestor y Supervisor.</p></div></div>
          <div className="internal-user-role-grid">
            <label className="form-field"><span>Rol</span><select value={roleCode} onChange={(event) => setRoleCode(event.target.value as InternalRoleCode)}>{roles.map((role) => <option key={role.code} value={role.code}>{role.name}</option>)}</select></label>
            <label className="form-field"><span>Organización</span><select value={organizationPublicId} disabled={roleCode === 'ADMINISTRATOR'} required onChange={(event) => setOrganizationPublicId(event.target.value)}><option value="">Seleccionar organización</option>{roleCode === 'ADMINISTRATOR' ? (globalOrganization && <option value={globalOrganization.publicId}>GLOBAL · Contexto del sistema</option>) : customerOrganizations.map((organization) => <option key={organization.publicId} value={organization.publicId}>{organization.name} · {organization.code}</option>)}</select><small>{roleCode === 'ADMINISTRATOR' ? 'Los Administradores pertenecen obligatoriamente a GLOBAL.' : 'Solo se muestran organizaciones comerciales activas y vigentes.'}</small>{fieldErrors.organizationPublicId && <small className="field-error">{fieldErrors.organizationPublicId}</small>}</label>
          </div>
        </section>

        <section className="form-section form-wide">
          <div className="form-section-heading"><span className="form-section-number">3</span><div><h2>Vigencia</h2><p>Define el periodo de acceso del usuario.</p></div></div>
          <div className="form-grid-three">
            <label className="form-field"><span>Inicio de vigencia</span><DateTimeField value={startsAt} onChange={setStartsAt} required ariaInvalid={Boolean(fieldErrors.startsAt)} ariaLabel="Seleccionar inicio de vigencia" />{fieldErrors.startsAt && <small className="field-error">{fieldErrors.startsAt}</small>}</label>
            <label className="form-field"><span>Vencimiento</span><DateTimeField value={expiresAt} onChange={setExpiresAt} disabled={withoutExpiration} required={!withoutExpiration} ariaInvalid={Boolean(fieldErrors.expiresAt)} ariaLabel="Seleccionar vencimiento" />{fieldErrors.expiresAt && <small className="field-error">{fieldErrors.expiresAt}</small>}</label>
            <label className="checkbox-row internal-user-expiration-check"><input type="checkbox" checked={withoutExpiration} onChange={(event) => setWithoutExpiration(event.target.checked)} />Sin fecha de vencimiento</label>
          </div>
        </section>

        <div className="password-policy form-wide">
          <strong>Contraseña temporal automática</strong>
          <span>Se genera con un mecanismo criptográficamente seguro.</span>
          <span>Incluye mayúscula, minúscula, número y símbolo.</span>
          <span>No se almacena ni vuelve a mostrarse después de esta operación.</span>
        </div>

        {error && <div className="error-message form-wide" role="alert">{error}</div>}
        <div className="form-actions form-wide">
          <button className="primary-button" type="submit" disabled={submitting}>{submitting ? 'Creando…' : 'Crear usuario'}</button>
        </div>
      </form>
    </main>
  )
}
