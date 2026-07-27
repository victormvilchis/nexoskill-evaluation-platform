import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  activateOrganization,
  deactivateOrganization,
  deleteOrganization,
  getOrganization,
  getOrganizationStatusHistory,
  restoreOrganization
} from '../features/organizations/api/organizationApi'
import type {
  OrganizationDetail,
  OrganizationStatusHistory
} from '../features/organizations/types/organizations'
import { ApiRequestError } from '../shared/api/apiClient'
import { BackButton } from '../shared/components/BackButton'
import { Icon } from '../shared/components/Icon'
import { LoadingScreen } from '../shared/components/LoadingScreen'

const LABELS = {
  ACTIVE: 'Activa',
  INACTIVE: 'Inactiva',
  SUSPENDED: 'Suspendida',
  EXPIRED: 'Vencida',
  DELETED: 'Eliminada'
} as const

type Action = 'ACTIVATE' | 'DEACTIVATE' | 'DELETE' | 'RESTORE'

const CONSEQUENCES: Record<Action, { title: string; description: string; items: string[]; confirm: string }> = {
  DEACTIVATE: {
    title: 'Desactivar organización',
    description: 'La desactivación bloquea la operación sin modificar los estados individuales ni eliminar información.',
    items: [
      'Gestores, Supervisores y Estudiantes perderán acceso inmediatamente.',
      'Todas las sesiones activas serán revocadas.',
      'No se podrán crear nuevos registros operativos.',
      'Usuarios, estudiantes, contenido, avances, resultados y auditoría se conservarán.',
      'La organización podrá reactivarse posteriormente.'
    ],
    confirm: 'Desactivar organización'
  },
  DELETE: {
    title: 'Eliminar organización lógicamente',
    description: 'La eliminación es lógica y exige que la organización ya esté inactiva.',
    items: [
      'La organización dejará de aparecer en los listados operativos.',
      'Todos los accesos permanecerán bloqueados y las sesiones serán revocadas.',
      'No se eliminarán usuarios, estudiantes, preguntas, categorías, resultados ni configuraciones.',
      'La auditoría y las relaciones históricas permanecerán disponibles.',
      'Para volver a utilizarla deberá restaurarse primero como inactiva.'
    ],
    confirm: 'Eliminar lógicamente'
  },
  ACTIVATE: {
    title: 'Activar organización',
    description: 'La organización volverá a operar conforme a su vigencia y a los estados individuales de sus usuarios y estudiantes.',
    items: [
      'Los usuarios y estudiantes elegibles podrán autenticarse nuevamente.',
      'Se habilitarán las operaciones permitidas por su plan y permisos.',
      'No se modificarán registros históricos.'
    ],
    confirm: 'Activar organización'
  },
  RESTORE: {
    title: 'Restaurar organización',
    description: 'La restauración no activa la organización directamente.',
    items: [
      'La organización regresará como INACTIVE.',
      'Los accesos continuarán bloqueados hasta que se active explícitamente.',
      'Toda la información y trazabilidad se conservarán.'
    ],
    confirm: 'Restaurar como inactiva'
  }
}

function formatDateTime(value?: string) {
  if (!value) return 'Sin registro'
  return new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium', timeStyle: 'short' })
    .format(new Date(value))
}

export function OrganizationManagementPage() {
  const { publicId = '' } = useParams()
  const navigate = useNavigate()
  const [organization, setOrganization] = useState<OrganizationDetail>()
  const [history, setHistory] = useState<OrganizationStatusHistory[]>([])
  const [selectedAction, setSelectedAction] = useState<Action>()
  const [reason, setReason] = useState('')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    Promise.all([
      getOrganization(publicId, controller.signal),
      getOrganizationStatusHistory(publicId, controller.signal)
    ])
      .then(([detail, entries]) => {
        setOrganization(detail)
        setHistory(entries)
      })
      .catch((requestError: unknown) => {
        if (!controller.signal.aborted) {
          setError(requestError instanceof ApiRequestError
            ? requestError.message
            : 'No fue posible consultar la administración de la organización.')
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [publicId])

  const availableActions = useMemo<Action[]>(() => {
    switch (organization?.status) {
      case 'ACTIVE':
      case 'SUSPENDED':
      case 'EXPIRED':
        return ['DEACTIVATE']
      case 'INACTIVE':
        return ['ACTIVATE', 'DELETE']
      case 'DELETED':
        return ['RESTORE']
      default:
        return []
    }
  }, [organization?.status])

  async function execute() {
    if (!selectedAction || !organization || busy) return
    if (selectedAction === 'DELETE' && !reason.trim()) {
      setError('El motivo es obligatorio para eliminar lógicamente la organización.')
      return
    }
    setBusy(true)
    setError(undefined)
    try {
      if (selectedAction === 'ACTIVATE') await activateOrganization(publicId, reason)
      if (selectedAction === 'DEACTIVATE') await deactivateOrganization(publicId, reason)
      if (selectedAction === 'DELETE') await deleteOrganization(publicId, reason)
      if (selectedAction === 'RESTORE') await restoreOrganization(publicId, reason)
      const successByAction: Record<Action, string> = {
        ACTIVATE: 'activated',
        DEACTIVATE: 'deactivated',
        DELETE: 'deleted',
        RESTORE: 'restored'
      }
      navigate(`/admin/organizations?success=${successByAction[selectedAction]}`, { replace: true })
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError
        ? requestError.message
        : 'No fue posible completar la acción administrativa.')
    } finally {
      setBusy(false)
    }
  }

  if (loading) return <LoadingScreen />
  if (!organization) {
    return (
      <main className="content-page">
        <BackButton fallback="/admin/organizations" />
        <section className="inline-error-panel" role="alert">
          <div className="inline-error-icon"><Icon name="error" /></div>
          <div><strong>No fue posible cargar la organización</strong><p>{error}</p></div>
        </section>
      </main>
    )
  }

  const action = selectedAction ? CONSEQUENCES[selectedAction] : undefined

  return (
    <main className="content-page narrow-content resource-page org-management-page">
      <BackButton fallback="/admin/organizations" />
      <header className="ns-page-header">
        <div>
          <p className="eyebrow">Administración · Organización</p>
          <h1>Administrar {organization.name}</h1>
          <p className="muted">Las acciones de estado no eliminan físicamente información relacionada.</p>
        </div>
        <span className={`status-badge status-${organization.status.toLowerCase()}`}>{LABELS[organization.status]}</span>
      </header>

      {error && (
        <section className="inline-error-panel" role="alert">
          <div className="inline-error-icon"><Icon name="error" /></div>
          <div><strong>No fue posible completar la operación</strong><p>{error}</p></div>
        </section>
      )}

      <section className="ns-card org-management-summary">
        <div><span>Nombre</span><strong>{organization.name}</strong></div>
        <div><span>Código</span><strong>{organization.code}</strong></div>
        <div><span>Estudiantes registrados</span><strong>{organization.studentCount ?? 0}</strong></div>
        <div><span>Último cambio de estado</span><strong>{formatDateTime(organization.statusChangedAt)}</strong></div>
        <div className="org-management-wide"><span>Motivo actual</span><strong>{organization.statusReason || 'Sin motivo registrado'}</strong></div>
      </section>

      <section className="ns-card">
        <div className="ns-card-heading"><div><span className="ns-step">1</span><h2>Acción administrativa</h2></div></div>
        <div className="org-management-actions">
          {availableActions.map((value) => (
            <button
              key={value}
              type="button"
              className={value === 'DELETE' || value === 'DEACTIVATE' ? 'danger-button' : 'primary-button'}
              onClick={() => {
                setSelectedAction(value)
                setReason('')
                setError(undefined)
              }}
            >
              {CONSEQUENCES[value].confirm}
            </button>
          ))}
        </div>

        {action && selectedAction && (
          <div className="org-consequence-panel">
            <h3>{action.title}</h3>
            <p>{action.description}</p>
            <ul>{action.items.map((item) => <li key={item}>{item}</li>)}</ul>
            <label className="ns-field">
              <span>Motivo {selectedAction === 'DELETE' ? '*' : '(opcional)'}</span>
              <textarea rows={3} maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} />
            </label>
            <div className="org-confirm-actions">
              <button className="secondary-button" type="button" disabled={busy} onClick={() => setSelectedAction(undefined)}>Cancelar</button>
              <button
                className={selectedAction === 'DELETE' || selectedAction === 'DEACTIVATE' ? 'danger-button' : 'primary-button'}
                type="button"
                disabled={busy || (selectedAction === 'DELETE' && !reason.trim())}
                onClick={() => void execute()}
              >
                {busy ? 'Procesando…' : action.confirm}
              </button>
            </div>
          </div>
        )}
      </section>

      <section className="ns-card">
        <div className="ns-card-heading"><div><span className="ns-step">2</span><h2>Historial de estados</h2></div></div>
        {history.length === 0 ? (
          <p className="muted">Todavía no hay cambios de estado registrados.</p>
        ) : (
          <div className="org-history-list">
            {history.map((entry, index) => (
              <article key={`${entry.changedAt}-${index}`}>
                <div><strong>{entry.previousStatus ? LABELS[entry.previousStatus] : 'Creación'} → {LABELS[entry.newStatus]}</strong><time>{formatDateTime(entry.changedAt)}</time></div>
                <p>{entry.reason || 'Sin motivo registrado.'}</p>
              </article>
            ))}
          </div>
        )}
      </section>
    </main>
  )
}
