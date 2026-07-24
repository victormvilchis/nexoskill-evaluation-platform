export type UserAccessStatus =
  | 'PENDING'
  | 'ACTIVE'
  | 'SUSPENDED'
  | 'EXPIRED'
  | 'CANCELED'

export interface CurrentUser {
  publicId: string
  email: string
  firstName: string
  lastName: string
  displayName: string
  roles: string[]
  permissions: string[]
  lastLoginAt: string | null
  accessStatus: UserAccessStatus
  accessStartsAt: string
  accessExpiresAt: string | null
}

export interface LoginResponse {
  user: CurrentUser
}

export interface CurrentUserResponse {
  user: CurrentUser
}

export interface ApiError {
  timestamp?: string
  status?: number
  code: string
  message: string
  path?: string
  fieldErrors?: Record<string, string>
}
