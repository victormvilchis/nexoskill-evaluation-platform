import { apiRequest } from '../../../shared/api/apiClient'
import type {
  AdminUser,
  AdminUserPage,
  CreateUserPayload,
  CreateUserResponse,
  InternalUserSession,
  InternalUserStatusHistory,
  RoleOption,
  TemporaryPasswordResponse,
  UpdateInternalUserPayload,
  UserStatus,
  UserStatusChangePayload
} from '../../../shared/types/users'

interface SearchUsersParams {
  query?: string
  status?: UserStatus | 'ALL'
  page?: number
  size?: number
}

export function searchUsers({
  query = '',
  status = 'ACTIVE',
  page = 0,
  size = 20
}: SearchUsersParams = {}) {
  const parameters = new URLSearchParams({
    page: String(page),
    size: String(size)
  })

  if (query.trim()) parameters.set('query', query.trim())
  parameters.set('status', status)

  return apiRequest<AdminUserPage>(`/admin/users?${parameters.toString()}`)
}

export function getUser(publicId: string) {
  return apiRequest<AdminUser>(`/admin/users/${publicId}`)
}

export function createUser(payload: CreateUserPayload) {
  return apiRequest<CreateUserResponse>('/admin/users', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function updateUser(publicId: string, payload: UpdateInternalUserPayload) {
  return apiRequest<AdminUser>(`/admin/users/${publicId}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}

function statusAction(
  publicId: string,
  action: 'activate' | 'deactivate' | 'suspend' | 'restore',
  payload: UserStatusChangePayload = {}
) {
  return apiRequest<AdminUser>(`/admin/users/${publicId}/${action}`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function activateUser(publicId: string, payload: UserStatusChangePayload = {}) {
  return statusAction(publicId, 'activate', payload)
}

export function deactivateUser(publicId: string, payload: UserStatusChangePayload = {}) {
  return statusAction(publicId, 'deactivate', payload)
}

export function suspendUser(publicId: string, payload: UserStatusChangePayload) {
  return statusAction(publicId, 'suspend', payload)
}

export function restoreUser(publicId: string, payload: UserStatusChangePayload = {}) {
  return statusAction(publicId, 'restore', payload)
}

export function deleteUser(publicId: string, payload: UserStatusChangePayload) {
  return apiRequest<AdminUser>(`/admin/users/${publicId}`, {
    method: 'DELETE',
    body: JSON.stringify(payload)
  })
}

export function resetUserPassword(publicId: string) {
  return apiRequest<TemporaryPasswordResponse>(`/admin/users/${publicId}/reset-password`, {
    method: 'POST'
  })
}

export function getUserStatusHistory(publicId: string) {
  return apiRequest<InternalUserStatusHistory[]>(`/admin/users/${publicId}/status-history`)
}

export function getUserSessions(publicId: string) {
  return apiRequest<InternalUserSession[]>(`/admin/users/${publicId}/sessions`)
}

export function revokeUserSessions(publicId: string) {
  return apiRequest<{ revokedSessions: number }>(`/admin/users/${publicId}/revoke-sessions`, {
    method: 'POST'
  })
}

export function getRoles() {
  return apiRequest<RoleOption[]>('/admin/roles')
}
