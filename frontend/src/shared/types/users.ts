export type UserStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED' | 'DELETED'

export type UserAccessStatus =
  | 'PENDING'
  | 'ACTIVE'
  | 'SUSPENDED'
  | 'EXPIRED'
  | 'CANCELED'

export type InternalRoleCode = 'ADMINISTRATOR' | 'MANAGER' | 'SUPERVISOR'
export type AuthSessionStatus = 'ACTIVE' | 'REVOKED' | 'EXPIRED'
export type AuthSessionScope = 'FULL' | 'PASSWORD_CHANGE'

export interface AdminUser {
  publicId: string
  email: string
  firstName: string
  lastName: string
  displayName: string
  status: UserStatus
  roles: InternalRoleCode[]
  organizationPublicId: string | null
  organizationName: string | null
  accessStatus: UserAccessStatus
  startsAt: string
  expiresAt: string | null
  lastLoginAt: string | null
  createdAt: string
  statusChangedAt: string | null
  statusChangedBy: string | null
  statusReason: string | null
}

export interface AdminUserPage {
  content: AdminUser[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface RoleOption {
  code: InternalRoleCode
  name: string
}

export interface CreateUserPayload {
  email: string
  firstName: string
  lastName: string
  displayName?: string
  roleCode: InternalRoleCode
  organizationPublicId: string | null
  initialStatus: 'ACTIVE' | 'INACTIVE'
  startsAt: string
  expiresAt: string | null
}

export interface CreateUserResponse {
  user: AdminUser
  temporaryPassword: string
}

export interface UpdateInternalUserPayload {
  email: string
  firstName: string
  lastName: string
  displayName?: string
  roleCode: InternalRoleCode
  organizationPublicId: string | null
  startsAt: string
  expiresAt: string | null
}

export interface UserStatusChangePayload {
  reason?: string
}

export interface TemporaryPasswordResponse {
  user: AdminUser
  temporaryPassword: string
}

export interface InternalUserStatusHistory {
  previousStatus: UserStatus | null
  newStatus: UserStatus
  reason: string | null
  actorDisplayName: string
  occurredAt: string
}

export interface InternalUserSession {
  publicId: string
  status: AuthSessionStatus
  scope: AuthSessionScope
  ipAddress: string | null
  userAgent: string | null
  createdAt: string
  lastActivityAt: string | null
  expiresAt: string
  revokedAt: string | null
}
