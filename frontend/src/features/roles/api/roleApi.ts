import { apiRequest } from '../../../shared/api/apiClient'
import type { PermissionCatalog, RoleDetail, RoleSummary } from '../../../shared/types/roles'
import type { PagedResponse, SortDirection } from '../../../shared/types/pagination'

export interface SaveRoleRequest {
  name: string
  description?: string
  permissionCodes: string[]
}

export function listRoles(params: {
  query?: string
  status?: 'ACTIVE' | 'INACTIVE' | 'ALL'
  page?: number
  size?: number
  sort?: string
  direction?: SortDirection
  signal?: AbortSignal
} = {}) {
  const query = new URLSearchParams({
    status: params.status ?? 'ACTIVE',
    page: String(params.page ?? 0),
    size: String(params.size ?? 10),
    sort: params.sort ?? 'name',
    direction: params.direction ?? 'ASC'
  })
  if (params.query?.trim()) query.set('query', params.query.trim())
  return apiRequest<PagedResponse<RoleSummary>>(`/admin/role-management?${query.toString()}`, { signal: params.signal })
}

export function getRole(code: string) {
  return apiRequest<RoleDetail>(`/admin/role-management/${encodeURIComponent(code)}`)
}

export function getPermissionCatalog(scope: 'ORGANIZATIONAL' | 'ADMINISTRATOR' = 'ORGANIZATIONAL') {
  return apiRequest<PermissionCatalog>(`/admin/role-management/permissions?scope=${scope}`)
}

export function createRole(request: SaveRoleRequest) {
  return apiRequest<RoleDetail>('/admin/role-management', { method: 'POST', body: JSON.stringify(request) })
}

export function updateRole(code: string, request: SaveRoleRequest) {
  return apiRequest<RoleDetail>(`/admin/role-management/${encodeURIComponent(code)}`, {
    method: 'PUT', body: JSON.stringify(request)
  })
}

export function cloneRole(code: string, request: { name: string; description?: string }) {
  return apiRequest<RoleDetail>(`/admin/role-management/${encodeURIComponent(code)}/clone`, {
    method: 'POST', body: JSON.stringify(request)
  })
}

export function changeRoleStatus(code: string, status: 'ACTIVE' | 'INACTIVE') {
  return apiRequest<RoleDetail>(`/admin/role-management/${encodeURIComponent(code)}/status`, {
    method: 'POST', body: JSON.stringify({ status })
  })
}

export function deleteRole(code: string) {
  return apiRequest<void>(`/admin/role-management/${encodeURIComponent(code)}`, { method: 'DELETE' })
}
