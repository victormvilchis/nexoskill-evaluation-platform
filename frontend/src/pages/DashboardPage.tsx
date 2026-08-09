import { useEffect, useMemo, useState, type DragEvent, type ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import {
  getDashboardConfiguration,
  getExecutiveDashboard,
  saveDashboardConfiguration
} from '../features/dashboard/api/dashboardApi'
import { useAuth } from '../features/authentication/context/AuthContext'
import { ApiRequestError } from '../shared/api/apiClient'
import { FilterToolbar } from '../shared/components/FilterToolbar'
import { Icon, type IconName } from '../shared/components/Icon'
import { ResourceSelectField } from '../shared/components/ResourceFilters'
import { useToast } from '../shared/components/ToastProvider'
import type {
  DashboardChartPoint,
  DashboardComponentDefinition,
  DashboardComponentPreference,
  DashboardConfiguration,
  DashboardSize,
  ExecutiveDashboard
} from '../shared/types/dashboard'

const EMPTY_MESSAGE = 'No hay información para los filtros seleccionados.'

const KPI_META: Record<string, { key: keyof ExecutiveDashboard['kpis']; label: string; icon: IconName; tone: string; suffix?: string }> = {
  KPI_ACTIVE_COLLABORATORS: { key: 'activeCollaborators', label: 'Colaboradores activos', icon: 'users', tone: 'blue' },
  KPI_TALENT_BANK: { key: 'talentBank', label: 'Talent Bank', icon: 'profile', tone: 'violet' },
  KPI_COMPLIANCE: { key: 'certificationCompliance', label: 'Cumplimiento de certificaciones', icon: 'check', tone: 'green', suffix: '%' },
  KPI_EXPIRING: { key: 'expiringSoon', label: 'Próximas a vencer', icon: 'clock', tone: 'amber' },
  KPI_EXPIRED: { key: 'expired', label: 'Vencidas', icon: 'warning', tone: 'red' },
  KPI_RECERTIFICATION: { key: 'pendingRecertifications', label: 'Recertificaciones pendientes', icon: 'restore', tone: 'orange' }
}

function filterValue(params: URLSearchParams, key: string, fallback = '') {
  return params.get(key) ?? fallback
}

function dashboardQuery(params: URLSearchParams) {
  return {
    organizationPublicId: filterValue(params, 'organization'),
    role: filterValue(params, 'role'),
    technology: filterValue(params, 'technology'),
    collaboratorStatus: filterValue(params, 'status', 'ACTIVE'),
    certificationType: filterValue(params, 'certification'),
    certificationState: filterValue(params, 'certificationState')
  }
}

function maxValue(points: DashboardChartPoint[]) {
  return Math.max(0, ...points.map((point) => point.value))
}

function EmptyState() {
  return <div className="executive-empty"><Icon name="info" size={18} /><span>{EMPTY_MESSAGE}</span></div>
}

function BarChart({ points, selected, onSelect, limit = 10 }: {
  points: DashboardChartPoint[]
  selected?: string
  onSelect?: (key: string) => void
  limit?: number
}) {
  const visible = points.filter((point) => point.value > 0).slice(0, limit)
  const maximum = maxValue(visible)
  if (!visible.length || maximum === 0) return <EmptyState />
  return (
    <div className="executive-bars">
      {visible.map((point, index) => (
        <button
          className={`executive-bar-row color-${(index % 6) + 1}${selected === point.key ? ' selected' : ''}`}
          key={point.key}
          type="button"
          onClick={() => onSelect?.(point.key)}
          disabled={!onSelect}
          title={`${point.label}: ${point.value}`}
        >
          <span className="executive-bar-label">{point.label}</span>
          <span className="executive-bar-track"><span style={{ width: `${Math.max(4, point.value / maximum * 100)}%` }} /></span>
          <strong>{point.value}</strong>
        </button>
      ))}
    </div>
  )
}

function DonutChart({ points, selected, onSelect }: {
  points: DashboardChartPoint[]
  selected?: string
  onSelect?: (key: string) => void
}) {
  const total = points.reduce((sum, point) => sum + point.value, 0)
  if (!total) return <EmptyState />
  let cursor = 0
  const segments = points.map((point, index) => {
    const start = cursor
    cursor += point.value / total * 100
    return `var(--dashboard-chart-${index + 1}) ${start}% ${cursor}%`
  })
  return (
    <div className="executive-donut-layout">
      <div className="executive-donut" style={{ background: `conic-gradient(${segments.join(',')})` }}>
        <div className="executive-donut-center"><strong>{total}</strong><span>aplicables</span></div>
      </div>
      <div className="executive-legend">
        {points.map((point, index) => (
          <button
            className={`${selected === point.key ? 'selected ' : ''}legend-${index + 1}`}
            key={point.key}
            type="button"
            onClick={() => onSelect?.(point.key)}
            disabled={!onSelect}
          >
            <span className="legend-dot" />
            <span>{point.label}</span>
            <strong>{point.value}</strong>
          </button>
        ))}
      </div>
    </div>
  )
}

function TimelineChart({ points }: { points: DashboardChartPoint[] }) {
  const maximum = maxValue(points)
  if (!maximum) return <EmptyState />
  return (
    <div className="executive-timeline">
      {points.map((point) => (
        <div className="timeline-column" key={point.key} title={`${point.label}: ${point.value}`}>
          <strong>{point.value || ''}</strong>
          <div className="timeline-track"><span style={{ height: `${point.value ? Math.max(8, point.value / maximum * 100) : 2}%` }} /></div>
          <small>{point.label.replace(/ \d{4}$/, '')}</small>
        </div>
      ))}
    </div>
  )
}

function Panel({ title, eyebrow, children }: { title: string; eyebrow?: string; children: ReactNode }) {
  return (
    <section className="executive-panel">
      <header><div>{eyebrow && <span>{eyebrow}</span>}<h2>{title}</h2></div></header>
      <div className="executive-panel-body">{children}</div>
    </section>
  )
}

function SizeSelector({ value, allowed, onChange }: { value: DashboardSize; allowed: DashboardSize[]; onChange: (size: DashboardSize) => void }) {
  return (
    <select aria-label="Tamaño del componente" value={value} onChange={(event) => onChange(event.target.value as DashboardSize)}>
      {allowed.map((size) => <option key={size} value={size}>{size === 'SMALL' ? 'Pequeño' : size === 'MEDIUM' ? 'Mediano' : 'Grande'}</option>)}
    </select>
  )
}

export function DashboardPage() {
  const { user } = useAuth()
  const toast = useToast()
  const [searchParams, setSearchParams] = useSearchParams()
  const [dashboard, setDashboard] = useState<ExecutiveDashboard | null>(null)
  const [configuration, setConfiguration] = useState<DashboardConfiguration | null>(null)
  const [draft, setDraft] = useState<DashboardComponentPreference[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [customizing, setCustomizing] = useState(false)
  const [catalogOpen, setCatalogOpen] = useState(false)
  const [draggedCode, setDraggedCode] = useState<string | null>(null)

  const queryKey = searchParams.toString()

  useEffect(() => {
    let active = true
    setLoading(true)
    getExecutiveDashboard(dashboardQuery(searchParams))
      .then((response) => { if (active) setDashboard(response) })
      .catch((requestError) => {
        if (!active) return
        toast.error('No fue posible cargar el Dashboard', requestError instanceof ApiRequestError ? requestError.message : 'Intenta nuevamente.')
      })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [queryKey, toast])

  useEffect(() => {
    let active = true
    getDashboardConfiguration()
      .then((response) => {
        if (!active) return
        setConfiguration(response)
        setDraft(response.components)
      })
      .catch((requestError) => {
        if (!active) return
        toast.error('No fue posible cargar la configuración del Dashboard', requestError instanceof ApiRequestError ? requestError.message : 'Intenta nuevamente.')
      })
    return () => { active = false }
  }, [toast])

  const components = customizing ? draft : configuration?.components ?? []
  const definitions = useMemo(() => new Map(configuration?.catalog.map((item) => [item.code, item]) ?? []), [configuration])
  const available = configuration?.catalog.filter((item) => !draft.some((component) => component.code === item.code)) ?? []
  const hasFilters = ['organization', 'role', 'technology', 'certification', 'certificationState'].some((key) => searchParams.has(key))
    || filterValue(searchParams, 'status', 'ACTIVE') !== 'ACTIVE'

  function setFilter(key: string, value: string) {
    const next = new URLSearchParams(searchParams)
    const urlKey = key === 'organizationPublicId' ? 'organization'
      : key === 'collaboratorStatus' ? 'status'
        : key === 'certificationType' ? 'certification' : key
    if (!value || (urlKey === 'status' && value === 'ACTIVE')) next.delete(urlKey)
    else next.set(urlKey, value)
    setSearchParams(next, { replace: true })
  }

  function clearFilters() {
    setSearchParams(new URLSearchParams(), { replace: true })
  }

  function startCustomization() {
    if (!configuration?.canPersonalize) return
    setDraft(configuration.components.map((item) => ({ ...item })))
    setCustomizing(true)
  }

  function cancelCustomization() {
    setDraft(configuration?.components ?? [])
    setCustomizing(false)
    setCatalogOpen(false)
  }

  function updateComponent(code: string, patch: Partial<DashboardComponentPreference>) {
    setDraft((current) => current.map((item) => item.code === code ? { ...item, ...patch } : item))
  }

  function normalizeOrder(items: DashboardComponentPreference[]) {
    return items.map((item, order) => ({ ...item, order }))
  }

  function moveComponent(code: string, direction: -1 | 1) {
    setDraft((current) => {
      const index = current.findIndex((item) => item.code === code)
      const target = index + direction
      if (index < 0 || target < 0 || target >= current.length) return current
      const next = [...current]
      ;[next[index], next[target]] = [next[target], next[index]]
      return normalizeOrder(next)
    })
  }

  function dropComponent(event: DragEvent<HTMLDivElement>, targetCode: string) {
    event.preventDefault()
    if (!draggedCode || draggedCode === targetCode) return
    setDraft((current) => {
      const source = current.findIndex((item) => item.code === draggedCode)
      const target = current.findIndex((item) => item.code === targetCode)
      if (source < 0 || target < 0) return current
      const next = [...current]
      const [moved] = next.splice(source, 1)
      next.splice(target, 0, moved)
      return normalizeOrder(next)
    })
    setDraggedCode(null)
  }

  async function saveConfiguration() {
    setSaving(true)
    try {
      const response = await saveDashboardConfiguration(normalizeOrder(draft))
      setConfiguration(response)
      setDraft(response.components)
      setCustomizing(false)
      setCatalogOpen(false)
      toast.success('Dashboard guardado', 'Tu distribución personal se recuperará al volver a ingresar.')
    } catch (requestError) {
      toast.error('No fue posible guardar el Dashboard', requestError instanceof ApiRequestError ? requestError.message : 'Intenta nuevamente.')
    } finally {
      setSaving(false)
    }
  }

  function renderComponent(component: DashboardComponentPreference) {
    if (!dashboard) return null
    const meta = KPI_META[component.code]
    if (meta) {
      const raw = dashboard.kpis[meta.key]
      const value = raw == null ? '—' : `${raw}${meta.suffix ?? ''}`
      const detailTarget = component.code === 'KPI_ACTIVE_COLLABORATORS' && (user?.roles.includes('ADMINISTRATOR') || user?.permissions.includes('STUDENT_VIEW'))
        ? '/admin/collaborators?status=ACTIVE'
        : component.code === 'KPI_TALENT_BANK' && (user?.roles.includes('ADMINISTRATOR') || user?.permissions.includes('TALENT_VIEW'))
          ? '/admin/talent-bank' : null
      return (
        <article className={`executive-kpi tone-${meta.tone}`}>
          <div className="executive-kpi-icon"><Icon name={meta.icon} size={19} /></div>
          <div><span>{meta.label}</span><strong>{value}</strong></div>
          {component.code === 'KPI_COMPLIANCE' && dashboard.kpis.certificationCompliance != null && (
            <small>{dashboard.kpis.coveredCertifications} de {dashboard.kpis.applicableCertifications} áreas cubiertas</small>
          )}
          {detailTarget && <Link className="kpi-detail-link" to={detailTarget}>Ver detalle <Icon name="chevronRight" size={13} /></Link>}
        </article>
      )
    }
    switch (component.code) {
      case 'CHART_CERTIFICATION_STATUS':
        return <Panel title="Estado general de certificaciones" eyebrow="Cobertura"><DonutChart points={dashboard.certificationStatus} selected={dashboard.appliedFilters.certificationState ?? undefined} onSelect={(key) => setFilter('certificationState', dashboard.appliedFilters.certificationState === key ? '' : key)} /></Panel>
      case 'CHART_EXPIRATIONS':
        return <Panel title="Próximos vencimientos" eyebrow="12 meses"><TimelineChart points={dashboard.expirations} /></Panel>
      case 'CHART_TECHNOLOGIES':
        return <Panel title="Distribución por Tecnología" eyebrow="Capacidad actual"><BarChart points={dashboard.technologies} selected={dashboard.appliedFilters.technology ?? undefined} onSelect={(key) => setFilter('technology', dashboard.appliedFilters.technology === key ? '' : key)} /></Panel>
      case 'CHART_ROLES':
        return <Panel title="Distribución por Rol" eyebrow="Perfil - Perfil tecnológico"><BarChart points={dashboard.roles} selected={dashboard.appliedFilters.role ?? undefined} onSelect={(key) => setFilter('role', dashboard.appliedFilters.role === key ? '' : key)} /></Panel>
      case 'CHART_TALENT_BANK':
        return <Panel title="Composición de Talent Bank" eyebrow="Disponibilidad"><DonutChart points={dashboard.talentBank} /></Panel>
      case 'CHART_CERTIFICATION_TYPES':
        return (
          <Panel title="Estado por tipo de certificación" eyebrow="Cumplimiento">
            {dashboard.certificationTypes.some((item) => item.applicable > 0) ? (
              <div className="certification-type-list">
                {dashboard.certificationTypes.map((item) => (
                  <button key={item.key} className={dashboard.appliedFilters.certificationType === item.key ? 'selected' : ''} type="button" onClick={() => setFilter('certificationType', dashboard.appliedFilters.certificationType === item.key ? '' : item.key)}>
                    <span><strong>{item.label}</strong><small>{item.covered}/{item.applicable} cubiertas</small></span>
                    <span className="certification-type-track">
                      <i className="covered" style={{ width: `${item.applicable ? item.covered / item.applicable * 100 : 0}%` }} />
                      <i className="expired" style={{ width: `${item.applicable ? item.expired / item.applicable * 100 : 0}%` }} />
                    </span>
                    <strong>{item.compliance == null ? '—' : `${item.compliance}%`}</strong>
                  </button>
                ))}
              </div>
            ) : <EmptyState />}
          </Panel>
        )
      case 'CHART_ORGANIZATIONS':
        return (
          <Panel title="Comparativo de organizaciones" eyebrow="Vista global">
            {dashboard.organizations.length ? <div className="organization-comparison">{dashboard.organizations.slice(0, 12).map((item) => (
              <div key={item.publicId}><strong>{item.label}</strong><span><b>{item.activeCollaborators}</b> activos</span><span><b>{item.talentBank}</b> talent bank</span><span className="positive"><b>{item.valid}</b> vigentes</span><span className="warning"><b>{item.expiringSoon}</b> próximas</span><span className="critical"><b>{item.expired}</b> vencidas</span></div>
            ))}</div> : <EmptyState />}
          </Panel>
        )
      case 'ATTENTION_REQUIRED':
        return (
          <Panel title="Atención requerida" eyebrow="Prioridades ejecutivas">
            <div className="attention-list">{dashboard.attention.map((item) => (
              <button key={item.key} className={`severity-${item.severity.toLowerCase()}`} type="button" onClick={() => item.key !== 'RECERTIFICATION' && setFilter('certificationState', item.key)}>
                <span><Icon name={item.severity === 'CRITICAL' ? 'error' : 'warning'} size={18} /></span>
                <div><strong>{item.label}</strong><p>{item.description}</p></div><b>{item.value}</b>
              </button>
            ))}</div>
          </Panel>
        )
      default:
        return null
    }
  }

  return (
    <main className="content-page dashboard-page executive-dashboard-page">
      <header className="executive-dashboard-header">
        <div><span className="eyebrow">Dashboard ejecutivo</span><h1>Panorama de talento y certificaciones</h1><p>{dashboard?.scope.global ? 'Visión consolidada de todas las organizaciones.' : dashboard?.scope.organizationName ? `Información correspondiente a ${dashboard.scope.organizationName}.` : 'Información actual de la plataforma.'}</p></div>
        <div className="dashboard-header-actions">
          {configuration?.canPersonalize && !customizing && <button className="secondary-button" type="button" onClick={startCustomization}><Icon name="edit" size={16} /> Personalizar Dashboard</button>}
          {customizing && <><button className="secondary-button" type="button" onClick={() => setCatalogOpen(true)}><Icon name="plus" size={16} /> Agregar componente</button><button className="secondary-button" type="button" onClick={cancelCustomization} disabled={saving}>Cancelar</button><button className="primary-button" type="button" onClick={() => void saveConfiguration()} disabled={saving}>{saving ? 'Guardando...' : 'Guardar Dashboard'}</button></>}
        </div>
      </header>

      {dashboard && (
        <FilterToolbar>
          {dashboard.scope.administrator && <ResourceSelectField label="Organización" value={filterValue(searchParams, 'organization')} width="wide" onChange={(value) => setFilter('organizationPublicId', value)}><option value="">Todas las organizaciones</option>{dashboard.filterOptions.organizations.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</ResourceSelectField>}
          <ResourceSelectField label="Rol" value={filterValue(searchParams, 'role')} width="wide" onChange={(value) => setFilter('role', value)}><option value="">Todos</option>{dashboard.filterOptions.roles.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</ResourceSelectField>
          <ResourceSelectField label="Tecnología" value={filterValue(searchParams, 'technology')} width="medium" onChange={(value) => setFilter('technology', value)}><option value="">Todas</option>{dashboard.filterOptions.technologies.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</ResourceSelectField>
          <ResourceSelectField label="Estado" value={filterValue(searchParams, 'status', 'ACTIVE')} width="compact" onChange={(value) => setFilter('collaboratorStatus', value)}>{dashboard.filterOptions.collaboratorStatuses.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</ResourceSelectField>
          <ResourceSelectField label="Certificación" value={filterValue(searchParams, 'certification')} width="medium" onChange={(value) => setFilter('certificationType', value)}><option value="">Todas</option>{dashboard.filterOptions.certificationTypes.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</ResourceSelectField>
          {hasFilters && <button className="dashboard-clear-filters" type="button" onClick={clearFilters}><Icon name="close" size={14} /> Limpiar</button>}
        </FilterToolbar>
      )}

      {loading && !dashboard ? <div className="dashboard-loading">Cargando indicadores...</div> : (
        <div className={`executive-dashboard-grid${customizing ? ' customization-active' : ''}`}>
          {components.map((component, index) => {
            const definition = definitions.get(component.code)
            if (!definition) return null
            return (
              <div
                className={`dashboard-grid-item size-${component.size.toLowerCase()}`}
                key={component.code}
                draggable={customizing}
                onDragStart={() => setDraggedCode(component.code)}
                onDragOver={(event) => customizing && event.preventDefault()}
                onDrop={(event) => customizing && dropComponent(event, component.code)}
              >
                {customizing && <div className="dashboard-component-toolbar"><span><Icon name="menu" size={14} /> {definition.title}</span><div><button type="button" title="Mover arriba" disabled={index === 0} onClick={() => moveComponent(component.code, -1)}><Icon name="arrowUp" size={14} /></button><button type="button" title="Mover abajo" disabled={index === components.length - 1} onClick={() => moveComponent(component.code, 1)}><Icon name="arrowDown" size={14} /></button><SizeSelector value={component.size} allowed={definition.allowedSizes} onChange={(size) => updateComponent(component.code, { size })} /><button type="button" title="Quitar componente" onClick={() => setDraft((current) => normalizeOrder(current.filter((item) => item.code !== component.code)))}><Icon name="trash" size={14} /></button></div></div>}
                {renderComponent(component)}
              </div>
            )
          })}
          {!components.length && <div className="dashboard-empty-layout"><Icon name="info" size={22} /><strong>Tu Dashboard no tiene componentes visibles.</strong>{customizing && <button className="primary-button" type="button" onClick={() => setCatalogOpen(true)}>Agregar componente</button>}</div>}
        </div>
      )}

      {catalogOpen && (
        <div className="dashboard-catalog-overlay" role="presentation" onMouseDown={(event) => { if (event.currentTarget === event.target) setCatalogOpen(false) }}>
          <section className="dashboard-catalog" role="dialog" aria-modal="true" aria-labelledby="dashboard-catalog-title">
            <header><div><span className="eyebrow">Personalización</span><h2 id="dashboard-catalog-title">Agregar componente</h2><p>Selecciona información autorizada para incorporarla a tu tablero.</p></div><button className="icon-button" type="button" aria-label="Cerrar" onClick={() => setCatalogOpen(false)}><Icon name="close" /></button></header>
            <div className="dashboard-catalog-grid">
              {available.map((item: DashboardComponentDefinition) => <button key={item.code} type="button" onClick={() => { setDraft((current) => normalizeOrder([...current, { code: item.code, size: item.defaultSize, order: current.length }])) ; setCatalogOpen(false) }}><span className={`catalog-type ${item.category.toLowerCase()}`}>{item.category === 'KPI' ? 'KPI' : item.category === 'GRAPH' ? 'Gráfica' : 'Atención'}</span><strong>{item.title}</strong><small>Tamaño inicial: {item.defaultSize === 'SMALL' ? 'Pequeño' : item.defaultSize === 'MEDIUM' ? 'Mediano' : 'Grande'}</small></button>)}
              {!available.length && <EmptyState />}
            </div>
          </section>
        </div>
      )}
    </main>
  )
}
