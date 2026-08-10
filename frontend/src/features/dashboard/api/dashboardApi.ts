import { apiRequest } from '../../../shared/api/apiClient'
import type {
  DashboardComponentPreference,
  DashboardConfiguration,
  ExecutiveDashboard
} from '../../../shared/types/dashboard'

export interface DashboardQuery {
  organizationPublicId?: string
  role?: string
  technology?: string
  collaboratorStatus?: string
  certificationType?: string
  certificationState?: string
}

export function getExecutiveDashboard(query: DashboardQuery) {
  const params = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => {
    if (value) params.set(key, value)
  })
  const suffix = params.toString()
  return apiRequest<ExecutiveDashboard>(`/dashboard/overview${suffix ? `?${suffix}` : ''}`)
}

export function getDashboardConfiguration(organizationPublicId?: string) {
  const query = organizationPublicId ? `?organizationPublicId=${encodeURIComponent(organizationPublicId)}` : ''
  return apiRequest<DashboardConfiguration>(`/dashboard/configuration${query}`)
}

export function saveDashboardConfiguration(components: DashboardComponentPreference[], organizationPublicId?: string) {
  const query = organizationPublicId ? `?organizationPublicId=${encodeURIComponent(organizationPublicId)}` : ''
  return apiRequest<DashboardConfiguration>(`/dashboard/configuration${query}`, {
    method: 'PUT',
    body: JSON.stringify({ components })
  })
}
