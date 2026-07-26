export type StudentStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED' | 'ARCHIVED' | 'DELETED'
export type StudentEffectiveStatus = 'PENDING' | 'ACTIVE' | 'INACTIVE' | 'SUSPENDED' | 'EXPIRED' | 'ARCHIVED' | 'DELETED'
export type StudentSessionStatus = 'ACTIVE' | 'REVOKED' | 'EXPIRED'

export interface StudentSummary {
  publicId: string
  studentCode: string
  email: string
  displayName: string
  status: StudentStatus
  effectiveStatus: StudentEffectiveStatus
  validFrom: string
  expiresAt: string | null
  lastLoginAt: string | null
  updatedAt: string
}

export interface StudentDetail extends StudentSummary {
  firstName: string
  lastName: string
  passwordChangeRequired: boolean
  temporaryPasswordExpiresAt: string | null
  archivedAt: string | null
  deletedAt: string | null
  deletionReason: string | null
  createdAt: string
  version: number
}

export interface StudentPage {
  content: StudentSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface StudentSession {
  publicId: string
  status: StudentSessionStatus
  ipAddress: string | null
  userAgent: string | null
  createdAt: string
  lastActivityAt: string | null
  expiresAt: string
  revokedAt: string | null
  revocationReason: string | null
}

export interface CreateStudentPayload {
  studentCode: string
  email: string
  firstName: string
  lastName: string
  displayName?: string
  temporaryPassword: string
  status: 'ACTIVE' | 'INACTIVE'
  validFrom: string
  expiresAt: string | null
}

export interface UpdateStudentPayload {
  email: string
  firstName: string
  lastName: string
  displayName?: string
  validFrom: string
  expiresAt: string | null
  version: number
}

export interface StudentIdentity {
  publicId: string
  organizationPublicId: string
  organizationCode: string
  organizationName: string
  studentCode: string
  email: string
  firstName: string
  lastName: string
  displayName: string
  status: StudentEffectiveStatus
  validFrom: string
  expiresAt: string | null
  lastLoginAt: string | null
  passwordChangeRequired: boolean
}
