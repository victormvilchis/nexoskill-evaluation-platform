import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { createUser, getRoles } from '../features/users/api/userApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { useToast } from '../shared/components/ToastProvider'
import type { RoleOption } from '../shared/types/users'

function toLocalInputValue(date: Date) {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 16)
}

export function CreateUserPage() {
  const toast = useToast()
  const navigate = useNavigate()
  const defaultDates = useMemo(() => {
    const start = new Date()
    const expiration = new Date(start)
    expiration.setDate(expiration.getDate() + 30)
    return {
      startsAt: toLocalInputValue(start),
      expiresAt: toLocalInputValue(expiration)
    }
  }, [])

  const [roles, setRoles] = useState<RoleOption[]>([])
  const [email, setEmail] = useState('')
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [roleCode, setRoleCode] = useState('USER')
  const [temporaryPassword, setTemporaryPassword] = useState('')
  const [startsAt, setStartsAt] = useState(defaultDates.startsAt)
  const [expiresAt, setExpiresAt] = useState(defaultDates.expiresAt)
  const [withoutExpiration, setWithoutExpiration] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  useEffect(() => {
    getRoles()
      .then((response) => {
        setRoles(response)
        const fallbackRoleCode = response[0]?.code
        if (fallbackRoleCode) {
          setRoleCode((current) =>
            response.some((role) => role.code === current)
              ? current
              : fallbackRoleCode
          )
        }
      })
      .catch(() => setError('No fue posible cargar los roles disponibles.'))
  }, [])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setFieldErrors({})
    setSubmitting(true)

    try {
      await createUser({
        email,
        firstName,
        lastName,
        displayName: displayName || undefined,
        roleCode,
        temporaryPassword,
        startsAt: new Date(startsAt).toISOString(),
        expiresAt: withoutExpiration ? null : new Date(expiresAt).toISOString()
      })
      navigate('/admin/users?created=1', { replace: true })
    } catch (requestError) {
      if (requestError instanceof ApiRequestError) {
        setError(requestError.message)
        setFieldErrors(requestError.fieldErrors ?? {})
        toast.error('No fue posible crear el usuario', requestError.message)
      } else {
        const message = 'No fue posible crear el usuario.'
        setError(message)
        toast.error('No fue posible crear el usuario', message)
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="content-page narrow-content">
      <div className="page-heading">
        <div>
          <p className="eyebrow">Administración de usuarios</p>
          <h1>Crear usuario</h1>
          <p className="muted">
            Registra la cuenta, el rol inicial y el periodo de vigencia.
          </p>
        </div>
        <Link className="secondary-button button-link" to="/admin/users">
          Volver
        </Link>
      </div>

      <form className="entity-form" onSubmit={(event) => void handleSubmit(event)}>
        {error && <div className="error-message form-wide">{error}</div>}

        <div className="form-field">
          <label htmlFor="firstName">Nombre</label>
          <input
            id="firstName"
            value={firstName}
            maxLength={100}
            required
            onChange={(event) => setFirstName(event.target.value)}
          />
          {fieldErrors.firstName && <small className="field-error">{fieldErrors.firstName}</small>}
        </div>

        <div className="form-field">
          <label htmlFor="lastName">Apellidos</label>
          <input
            id="lastName"
            value={lastName}
            maxLength={150}
            required
            onChange={(event) => setLastName(event.target.value)}
          />
          {fieldErrors.lastName && <small className="field-error">{fieldErrors.lastName}</small>}
        </div>

        <div className="form-field form-wide">
          <label htmlFor="displayName">Nombre para mostrar</label>
          <input
            id="displayName"
            value={displayName}
            maxLength={250}
            placeholder="Se generará con nombre y apellidos"
            onChange={(event) => setDisplayName(event.target.value)}
          />
          {fieldErrors.displayName && <small className="field-error">{fieldErrors.displayName}</small>}
        </div>

        <div className="form-field form-wide">
          <label htmlFor="email">Correo electrónico</label>
          <input
            id="email"
            type="email"
            value={email}
            maxLength={254}
            autoComplete="off"
            required
            onChange={(event) => setEmail(event.target.value)}
          />
          {fieldErrors.email && <small className="field-error">{fieldErrors.email}</small>}
        </div>

        <div className="form-field">
          <label htmlFor="roleCode">Rol inicial</label>
          <select
            id="roleCode"
            value={roleCode}
            required
            onChange={(event) => setRoleCode(event.target.value)}
          >
            {roles.map((role) => (
              <option key={role.code} value={role.code}>{role.name}</option>
            ))}
          </select>
          {fieldErrors.roleCode && <small className="field-error">{fieldErrors.roleCode}</small>}
        </div>

        <div className="form-field">
          <label htmlFor="temporaryPassword">Contraseña temporal</label>
          <input
            id="temporaryPassword"
            type="password"
            value={temporaryPassword}
            minLength={10}
            maxLength={128}
            autoComplete="new-password"
            required
            onChange={(event) => setTemporaryPassword(event.target.value)}
          />
          <small>10 caracteres, mayúscula, minúscula, número y símbolo.</small>
          {fieldErrors.temporaryPassword && (
            <small className="field-error">{fieldErrors.temporaryPassword}</small>
          )}
        </div>

        <div className="form-field">
          <label htmlFor="startsAt">Inicio de vigencia</label>
          <div className="date-input-wrapper">
            <input
              id="startsAt"
              type="datetime-local"
              value={startsAt}
              required
              onChange={(event) => setStartsAt(event.target.value)}
            />
          </div>
          {fieldErrors.startsAt && <small className="field-error">{fieldErrors.startsAt}</small>}
        </div>

        <div className="form-field">
          <label htmlFor="expiresAt">Vencimiento</label>
          <div className="date-input-wrapper">
            <input
              id="expiresAt"
              type="datetime-local"
              value={expiresAt}
              disabled={withoutExpiration}
              required={!withoutExpiration}
              onChange={(event) => setExpiresAt(event.target.value)}
            />
          </div>
          {fieldErrors.expiresAt && <small className="field-error">{fieldErrors.expiresAt}</small>}
        </div>

        <label className="checkbox-row form-wide">
          <input
            type="checkbox"
            checked={withoutExpiration}
            onChange={(event) => setWithoutExpiration(event.target.checked)}
          />
          Crear acceso sin fecha de vencimiento
        </label>

        <div className="form-actions form-wide">
          <Link className="secondary-button button-link" to="/admin/users">
            Cancelar
          </Link>
          <button className="primary-button" type="submit" disabled={submitting}>
            {submitting ? 'Creando…' : 'Crear usuario'}
          </button>
        </div>
      </form>
    </main>
  )
}
