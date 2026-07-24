import { apiRequest } from '../../../shared/api/apiClient'

export interface OwnProfile {
  publicId: string
  email: string
  firstName: string
  lastName: string
  displayName: string
}

export interface OwnProfileResponse {
  profile: OwnProfile
}

export interface UpdateOwnProfilePayload {
  firstName: string
  lastName: string
  displayName?: string
}

export function getOwnProfile() {
  return apiRequest<OwnProfileResponse>('/users/me/profile')
}

export function updateOwnProfile(payload: UpdateOwnProfilePayload) {
  return apiRequest<OwnProfileResponse>('/users/me/profile', {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}
