import { apiRequest } from '../../../shared/api/apiClient'
import type {
  ChangePasswordPayload,
  CurrentUserResponse,
  LoginResponse
} from '../../../shared/types/auth'

export function login(email: string, password: string) {
  return apiRequest<LoginResponse>('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password })
  })
}

export function changePassword(payload: ChangePasswordPayload) {
  return apiRequest<void>('/auth/change-password', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function logout() {
  return apiRequest<void>('/auth/logout', {
    method: 'POST'
  })
}

export function getCurrentUser() {
  return apiRequest<CurrentUserResponse>('/users/me')
}
