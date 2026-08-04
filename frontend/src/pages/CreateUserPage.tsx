import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { searchOrganizations } from '../features/organizations/api/organizationApi'
import { createUser, getRoles } from '../features/users/api/userApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { BackButton } from '../shared/components/BackButton'
import { SelectField } from '../shared/components/SelectField'
import { useSaveNavigation } from '../shared/hooks/useSaveNavigation'
import type { OrganizationSummary } from '../features/organizations/types/organizations'
import type { InternalRoleCode, RoleOption } from '../shared/types/users'

function startOfDate(value: string) {
  return new Date(`${value}T00:00:00`).toISOString()
}

function dayAfter(value: string) {
  const date = new Date(`${value}T00:00:00`)
  date.setDate(date.getDate() + 1)
  return date.toISOString()
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
  const selectedOrganization = organizations.find((organization) => organization.publicId === organizationPublicId)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting) return
    setError(null)
    setFieldErrors({})

    const localErrors: Record<string, string> = {}
    if (organizationRequired && !organizationPublicId) {
      localErrors.organizationPublicId = 'Selecciona una organización.'
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
        startsAt: startOfDate(new Date().toISOString().slice(0, 10)),
        expiresAt: selectedOrganization?.expiresOn ? dayAfter(selectedOrganization.expiresOn) : null
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
                  ...(roleCode === 'ADMINISTRATOR'
                    ? globalOrganization ? [{ value: globalOrganization.publicId, label: 'GLOBAL · Contexto del sistema' }] : []
                    : customerOrganizations.map((organization) => ({ value: organization.publicId, label: `${organization.name} · ${organization.code}` })))
                ]} />
              <small>{roleCode === 'ADMINISTRATOR' ? 'Los Administradores pertenecen obligatoriamente a GLOBAL.' : 'Solo se muestran organizaciones comerciales activas y vigentes.'}</small>
              {fieldErrors.organizationPublicId && <small className="field-error">{fieldErrors.organizationPublicId}</small>}
            </label>
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
