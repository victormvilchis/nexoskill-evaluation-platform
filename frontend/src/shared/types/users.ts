export type UserStatus =
  | 'PENDING'
  | 'ACTIVE'
  | 'SUSPENDED'
  | 'LOCKED'
  | 'DISABLED'

export type UserAccessStatus =
  | 'PENDING'
  | 'ACTIVE'
  | 'SUSPENDED'
  | 'EXPIRED'
  | 'CANCELED'

export interface AdminUser {
  publicId: string
  email: string
  firstName: string
  lastName: string
  displayName: string
  status: UserStatus
  roles: string[]
  accessStatus: UserAccessStatus
  startsAt: string
  expiresAt: string | null
  lastLoginAt: string | null
}

export interface AdminUserPage {
  content: AdminUser[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface RoleOption {
  code: string
  name: string
}

export interface CreateUserPayload {
  email: string
  firstName: string
  lastName: string
  displayName?: string
  roleCode: string
  temporaryPassword: string
  startsAt: string
  expiresAt: string | null
}

export interface UpdateUserProfilePayload {
  email: string
  firstName: string
  lastName: string
  displayName?: string
}

export interface UpdateUserAccessPayload {
  startsAt: string
  expiresAt: string | null
}

export interface UpdateUserRolePayload {
  roleCode: string
}

export interface ResetUserPasswordPayload {
  temporaryPassword: string
}
