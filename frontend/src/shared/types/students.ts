export type StudentStatus = 'ACTIVE' | 'INACTIVE' | 'EXPIRED' | 'DELETED'
export type StudentEffectiveStatus = StudentStatus
export type StudentSessionStatus = 'ACTIVE' | 'REVOKED' | 'EXPIRED'

export interface StudentCatalogRef {
  publicId: string
  code: string
  name: string
}

export interface StudentOrganizationRef {
  publicId: string
  code: string
  name: string
  appliesCertifications: boolean
}

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
  expiresAt: string
  admissionDate: string | null
  lastLoginAt: string | null
  updatedAt: string
  version: number
  organization?: StudentOrganizationRef
  professionalProfile?: StudentCatalogRef | null
  technologicalProfile?: StudentCatalogRef | null
  certificationsEnabled: boolean
  appliesTechnologicalCertification: boolean
  appliesDevelopmentSecurity: boolean
  appliesNormativeTesting: boolean
  appliesOne: boolean
  appliesAgile: boolean
}

export interface StudentDetail extends StudentSummary {
  firstName: string
  lastName: string
  passwordChangeRequired: boolean
  temporaryPasswordExpiresAt: string | null
  createdAt: string
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

export interface StudentCatalogs {
  organization: StudentOrganizationRef
  appliesCertifications: boolean
  profiles: StudentCatalogRef[]
  technologicalProfiles: StudentCatalogRef[]
}

export interface StudentCertificationFlags {
  appliesTechnologicalCertification: boolean
  appliesDevelopmentSecurity: boolean
  appliesNormativeTesting: boolean
  appliesOne: boolean
  appliesAgile: boolean
}

export interface CreateStudentPayload {
  organizationPublicId?: string
  studentCode: string
  email: string
  firstName: string
  lastName: string
  displayName?: string
  status?: 'ACTIVE' | 'INACTIVE'
  validFrom: string
  expiresAt: string
  admissionDate?: string
  professionalProfilePublicId?: string
  technologicalProfilePublicId?: string
  appliesTechnologicalCertification?: boolean
  appliesDevelopmentSecurity?: boolean
  appliesNormativeTesting?: boolean
  appliesOne?: boolean
  appliesAgile?: boolean
}

export interface UpdateStudentPayload {
  email: string
  firstName: string
  lastName: string
  displayName?: string
  validFrom: string
  expiresAt: string
  admissionDate?: string
  professionalProfilePublicId?: string
  technologicalProfilePublicId?: string
  appliesTechnologicalCertification?: boolean
  appliesDevelopmentSecurity?: boolean
  appliesNormativeTesting?: boolean
  appliesOne?: boolean
  appliesAgile?: boolean
  version: number
}

export interface StudentDeletionResult {
  publicId: string
  operationReference: string
  deletedAt: string
}

export interface StudentTemporaryCredentials {
  organizationLogin: string
  email: string
  temporaryPassword: string
  mustChangePassword: boolean
}

export interface StudentCredentialResult {
  student: {
    publicId: string
    fullName: string
    email: string
    status: StudentStatus
  }
  temporaryCredentials: StudentTemporaryCredentials
}

export interface AdministrativeHistoryItem {
  publicId: string
  eventType: string
  description: string
  actorUserId: number | null
  occurredAt: string | null
}

export interface AdministrativeHistoryPage {
  content: AdministrativeHistoryItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface StudentSeatView {
  contractedSeats: number | null
  assignedSeatPublicId: string | null
  status: string
}

export interface StudentAdministrationView {
  student: StudentDetail
  seat: StudentSeatView
  activeSessions: number
  sessions: StudentSession[]
  lastAdministrativeChange: AdministrativeHistoryItem | null
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
  expiresAt: string
  lastLoginAt: string | null
  passwordChangeRequired: boolean
}
