import { apiRequest } from '../../../shared/api/apiClient'
import type {
  AdminUser,
  AdminUserPage,
  CreateUserPayload,
  ResetUserPasswordPayload,
  RoleOption,
  UpdateUserAccessPayload,
  UpdateUserProfilePayload,
  UpdateUserRolePayload,
  UserStatus
} from '../../../shared/types/users'

interface SearchUsersParams {
  query?: string
  status?: UserStatus | ''
  page?: number
  size?: number
}

export function searchUsers({
  query = '',
  status = '',
  page = 0,
  size = 20
}: SearchUsersParams = {}) {
  const parameters = new URLSearchParams({
    page: String(page),
    size: String(size)
  })

  if (query.trim()) parameters.set('query', query.trim())
  if (status) parameters.set('status', status)

  return apiRequest<AdminUserPage>(`/admin/users?${parameters.toString()}`)
}

export function getUser(publicId: string) {
  return apiRequest<AdminUser>(`/admin/users/${publicId}`)
}

export function createUser(payload: CreateUserPayload) {
  return apiRequest<AdminUser>('/admin/users', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function updateUserProfile(
  publicId: string,
  payload: UpdateUserProfilePayload
) {
  return apiRequest<AdminUser>(`/admin/users/${publicId}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}

export function updateUserAccess(
  publicId: string,
  payload: UpdateUserAccessPayload
) {
  return apiRequest<AdminUser>(`/admin/users/${publicId}/access`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}

export function updateUserRole(
  publicId: string,
  payload: UpdateUserRolePayload
) {
  return apiRequest<AdminUser>(`/admin/users/${publicId}/role`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}

export function activateUser(publicId: string) {
  return apiRequest<AdminUser>(`/admin/users/${publicId}/activate`, {
    method: 'POST'
  })
}

export function suspendUser(publicId: string) {
  return apiRequest<AdminUser>(`/admin/users/${publicId}/suspend`, {
    method: 'POST'
  })
}


export function deleteUser(publicId: string, reason?: string) {
  return apiRequest<AdminUser>(`/admin/users/${publicId}/delete`, {
    method: 'POST',
    body: JSON.stringify({ reason })
  })
}

export function restoreUser(publicId: string) {
  return apiRequest<AdminUser>(`/admin/users/${publicId}/restore`, {
    method: 'POST'
  })
}

export function resetUserPassword(
  publicId: string,
  payload: ResetUserPasswordPayload
) {
  return apiRequest<AdminUser>(`/admin/users/${publicId}/reset-password`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function getRoles() {
  return apiRequest<RoleOption[]>('/admin/roles')
}
