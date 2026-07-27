import { apiRequest } from '../../../shared/api/apiClient'
import type {
  OrganizationDetail,
  OrganizationPage,
  OrganizationPayload,
  OrganizationStatus,
  OrganizationStatusHistory
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
  search.set('status', params.status ?? 'ACTIVE')
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

function organizationAction(publicId: string, action: 'activate' | 'deactivate' | 'restore', reason?: string) {
  return apiRequest<OrganizationDetail>(`/admin/organizations/${publicId}/${action}`, {
    method: 'POST',
    body: JSON.stringify({ reason: reason?.trim() || undefined })
  })
}

export function activateOrganization(publicId: string, reason?: string) {
  return organizationAction(publicId, 'activate', reason)
}

export function deactivateOrganization(publicId: string, reason?: string) {
  return organizationAction(publicId, 'deactivate', reason)
}

export function restoreOrganization(publicId: string, reason?: string) {
  return organizationAction(publicId, 'restore', reason)
}

export function deleteOrganization(publicId: string, reason: string) {
  return apiRequest<OrganizationDetail>(`/admin/organizations/${publicId}`, {
    method: 'DELETE',
    body: JSON.stringify({ reason: reason.trim() })
  })
}

export function getOrganizationStatusHistory(publicId: string, signal?: AbortSignal) {
  return apiRequest<OrganizationStatusHistory[]>(
    `/admin/organizations/${publicId}/status-history`,
    { signal }
  )
}

/** Compatibilidad temporal para consumidores anteriores. */
export function changeOrganizationStatus(publicId: string, status: OrganizationStatus) {
  return apiRequest<OrganizationDetail>(`/admin/organizations/${publicId}/status/${status}`, {
    method: 'POST'
  })
}
