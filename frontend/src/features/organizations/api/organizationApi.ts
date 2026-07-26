import { apiRequest } from '../../../shared/api/apiClient'
import type {
  OrganizationDetail,
  OrganizationPage,
  OrganizationPayload,
  OrganizationStatus
} from '../types/organizations'

export async function searchOrganizations(params: {
  query?: string
  status?: OrganizationStatus | 'ALL'
  page?: number
  size?: number
  signal?: AbortSignal
}): Promise<OrganizationPage> {
  const search = new URLSearchParams()
  if (params.query?.trim()) search.set('query', params.query.trim())
  if (params.status) search.set('status', params.status)
  search.set('page', String(params.page ?? 0))
  search.set('size', String(params.size ?? 20))
  return apiRequest<OrganizationPage>(`/admin/organizations?${search.toString()}`, {
    signal: params.signal
  })
}

export function getOrganization(publicId: string, signal?: AbortSignal) {
  return apiRequest<OrganizationDetail>(`/admin/organizations/${publicId}`, { signal })
}

export function createOrganization(payload: OrganizationPayload) {
  return apiRequest<OrganizationDetail>('/admin/organizations', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function updateOrganization(publicId: string, payload: OrganizationPayload) {
  return apiRequest<OrganizationDetail>(`/admin/organizations/${publicId}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}

export function changeOrganizationStatus(publicId: string, status: OrganizationStatus) {
  return apiRequest<OrganizationDetail>(`/admin/organizations/${publicId}/status/${status}`, {
    method: 'POST'
  })
}
