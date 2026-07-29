export type StudentStatus = 'ACTIVE' | 'INACTIVE' | 'EXPIRED' | 'DELETED'
export type StudentEffectiveStatus = StudentStatus
export type StudentSessionStatus = 'ACTIVE' | 'REVOKED' | 'EXPIRED'
export interface StudentCatalogRef { publicId: string; code: string; name: string }
export interface StudentOrganizationRef { publicId: string; code: string; name: string; appliesCertifications: boolean }
export interface StudentSummary {
  publicId: string
  studentCode: string
  email: string
  firstName?: string
  lastName?: string
  displayName: string
  status: StudentStatus
  effectiveStatus: StudentEffectiveStatus
  validFrom: string
  expiresAt: string | null
  lastLoginAt: string | null
  updatedAt: string
  version: number
  organization?: StudentOrganizationRef
  professionalProfile?: StudentCatalogRef | null
  technologicalProfile?: StudentCatalogRef | null
  technology?: StudentCatalogRef | null
  certificationsEnabled?: boolean
  certificationEnrollmentDate?: string | null
}
export interface StudentDetail extends StudentSummary {
  firstName: string
  lastName: string
  passwordChangeRequired: boolean
  temporaryPasswordExpiresAt: string | null
  createdAt: string
  version: number
}
export interface StudentPage { content: StudentSummary[]; page: number; size: number; totalElements: number; totalPages: number }
export interface StudentSession {
  publicId: string; status: StudentSessionStatus; ipAddress: string | null; userAgent: string | null
  createdAt: string; lastActivityAt: string | null; expiresAt: string; revokedAt: string | null; revocationReason: string | null
}
export interface StudentCatalogs {
  organization: StudentOrganizationRef
  appliesCertifications: boolean
  profiles: StudentCatalogRef[]
  technologicalProfiles: StudentCatalogRef[]
  technologies: StudentCatalogRef[]
}
export interface CreateStudentPayload {
  organizationPublicId?: string
  studentCode: string
  email: string
  firstName: string
  lastName: string
  displayName?: string
  temporaryPassword: string
  status?: 'ACTIVE' | 'INACTIVE'
  validFrom: string
  expiresAt: string | null
  professionalProfilePublicId?: string
  technologicalProfilePublicId?: string
  technologyPublicId?: string
  certificationEnrollmentDate?: string
}
export interface UpdateStudentPayload {
  email: string
  firstName: string
  lastName: string
  displayName?: string
  validFrom: string
  expiresAt: string | null
  professionalProfilePublicId?: string
  technologicalProfilePublicId?: string
  technologyPublicId?: string
  certificationEnrollmentDate?: string
  version: number
}
export interface StudentDeletionResult { publicId: string; operationReference: string; deletedAt: string }
export interface StudentIdentity {
  publicId: string; organizationPublicId: string; organizationCode: string; organizationName: string
  studentCode: string; email: string; firstName: string; lastName: string; displayName: string
  status: StudentEffectiveStatus; validFrom: string; expiresAt: string | null; lastLoginAt: string | null
  passwordChangeRequired: boolean
}
