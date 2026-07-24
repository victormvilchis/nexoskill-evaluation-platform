import { apiRequest } from '../../../shared/api/apiClient'
import type {
  AdminUser,
  AdminUserPage,
  CreateUserPayload,
  RoleOption,
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

export function createUser(payload: CreateUserPayload) {
  return apiRequest<AdminUser>('/admin/users', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function getRoles() {
  return apiRequest<RoleOption[]>('/admin/roles')
}
