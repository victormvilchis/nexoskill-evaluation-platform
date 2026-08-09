export type DashboardSize = 'SMALL' | 'MEDIUM' | 'LARGE'

export interface DashboardOption { value: string; label: string }
export interface DashboardFilter {
  organizationPublicId: string | null
  role: string | null
  technology: string | null
  collaboratorStatus: 'ACTIVE' | 'INACTIVE' | 'ALL'
  certificationType: string | null
  certificationState: string | null
}
export interface DashboardScope {
  administrator: boolean
  global: boolean
  organizationPublicId: string | null
  organizationName: string | null
}
export interface DashboardKpis {
  activeCollaborators: number
  talentBank: number
  certificationCompliance: number | null
  applicableCertifications: number
  coveredCertifications: number
  expiringSoon: number
  expired: number
  pendingRecertifications: number
}
export interface DashboardChartPoint { key: string; label: string; value: number }
export interface DashboardCertificationTypePoint {
  key: string; label: string; applicable: number; covered: number; pending: number
  expiringSoon: number; expired: number; compliance: number | null
}
export interface DashboardOrganizationPoint {
  publicId: string; label: string; activeCollaborators: number; talentBank: number
  valid: number; expiringSoon: number; expired: number
}
export interface DashboardAttentionItem {
  key: string; label: string; value: number; severity: 'CRITICAL' | 'WARNING' | 'ATTENTION'; description: string
}
export interface DashboardFilterOptions {
  organizations: DashboardOption[]; roles: DashboardOption[]; technologies: DashboardOption[]
  collaboratorStatuses: DashboardOption[]; certificationTypes: DashboardOption[]; certificationStates: DashboardOption[]
}
export interface ExecutiveDashboard {
  generatedAt: string
  scope: DashboardScope
  canPersonalize: boolean
  appliedFilters: DashboardFilter
  filterOptions: DashboardFilterOptions
  kpis: DashboardKpis
  certificationStatus: DashboardChartPoint[]
  certificationTypes: DashboardCertificationTypePoint[]
  expirations: DashboardChartPoint[]
  technologies: DashboardChartPoint[]
  roles: DashboardChartPoint[]
  talentBank: DashboardChartPoint[]
  organizations: DashboardOrganizationPoint[]
  attention: DashboardAttentionItem[]
}
export interface DashboardComponentDefinition {
  code: string; title: string; category: 'KPI' | 'GRAPH' | 'ATTENTION'
  allowedSizes: DashboardSize[]; defaultSize: DashboardSize; administratorOnly: boolean
}
export interface DashboardComponentPreference { code: string; size: DashboardSize; order: number }
export interface DashboardConfiguration {
  personalized: boolean; canPersonalize: boolean
  components: DashboardComponentPreference[]; catalog: DashboardComponentDefinition[]
}
