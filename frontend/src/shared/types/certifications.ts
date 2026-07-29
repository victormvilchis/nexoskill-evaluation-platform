export type CertificationType =
  | 'DEVELOPMENT_SECURITY'
  | 'TECHNOLOGICAL'
  | 'ONE'
  | 'NORMATIVE_TESTING'
  | 'AGILE'

export type CertificationLevel = 'JR' | 'STD' | 'SR'
export type CertificationProcessType = 'CERTIFICATION' | 'RECERTIFICATION'
export type CertificationTrackingStatus =
  | 'PENDING'
  | 'NOT_SCHEDULED'
  | 'SCHEDULED'
  | 'IN_PROGRESS'
  | 'APPLIED'
  | 'APPROVED'
  | 'NOT_APPROVED'
  | 'EXPIRED'
  | 'CANCELLED'
export type CertificationValidityStatus = 'NOT_OBTAINED' | 'VALID' | 'EXPIRING_SOON' | 'EXPIRED'
export type CertificationExamStatus =
  | 'NOT_SCHEDULED'
  | 'SCHEDULED'
  | 'RESCHEDULED'
  | 'COMPLETED'
  | 'PASSED'
  | 'FAILED'
  | 'ABSENT'
  | 'CANCELLED'

export interface CertificationAvailability {
  appliesCertifications: boolean
  operatorAllowed: boolean
  organizationPublicId: string | null
  organizationName: string | null
}

export interface CertificationCatalogItem {
  publicId: string
  code: string
  name: string
  suggestedTechnologicalProfile: string | null
}

export interface CertificationEnumOption {
  value: string
  label: string
}

export interface CertificationCatalogs {
  profiles: CertificationCatalogItem[]
  technologies: CertificationCatalogItem[]
  technologicalProfiles: CertificationCatalogItem[]
  levels: CertificationEnumOption[]
  trackingStatuses: CertificationEnumOption[]
  examStatuses: CertificationEnumOption[]
  certificationTypes: CertificationEnumOption[]
}

export interface CertificationStudentSummary {
  publicId: string
  displayName: string
  organizationPublicId: string
  organizationName: string
  status: string
  validFrom: string
  expiresAt: string
  admissionDate: string
  professionalProfile: CertificationCatalogItem | null
  technologicalProfile: CertificationCatalogItem | null
}

export interface CertificationApplicability {
  technological: boolean
  developmentSecurity: boolean
  normativeTesting: boolean
  one: boolean
  agile: boolean
}

export interface CertificationMetrics {
  applicableAreas: number
  pending: number
  scheduled: number
  approved: number
  notApproved: number
  valid: number
  expiringSoon: number
  expired: number
  pendingRecertifications: number
}

export interface CertificationCycleView {
  publicId: string
  type: CertificationType
  technologyPublicId: string | null
  technologyName: string | null
  certificationLevel: CertificationLevel | null
  primary: boolean
  processType: CertificationProcessType
  trackingStatus: CertificationTrackingStatus
  deadlineDate: string | null
  scheduledDate: string | null
  applicationDate: string | null
  approved: boolean | null
  expirationDate: string | null
  validityStatus: CertificationValidityStatus
  previousApprovedCyclePublicId: string | null
  actionsToTake: string | null
  softtekManagement: string | null
  observations: string | null
  active: boolean
  latestScore: number | null
  attemptCount: number
  version: number
}

export interface CertificationAttemptView {
  publicId: string
  cyclePublicId: string
  attemptNumber: number
  scheduledDate: string | null
  applicationDate: string | null
  examStatus: CertificationExamStatus
  score: number | null
  approved: boolean | null
  result: string | null
  observations: string | null
  createdAt: string
  version: number
}

export interface CertificationHistoryView {
  publicId: string
  eventType: string
  previousValues: string | null
  newValues: string | null
  reason: string | null
  changedAt: string
}

export interface PagedResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface StudentCertificationDetail {
  student: CertificationStudentSummary
  appliesCertifications: boolean
  applicability: CertificationApplicability
  metrics: CertificationMetrics
  cycles: CertificationCycleView[]
}

export interface CertificationCyclePayload {
  publicId?: string
  type: CertificationType
  technologyPublicId?: string | null
  certificationLevel?: CertificationLevel | null
  primary: boolean
  trackingStatus: CertificationTrackingStatus
  scheduledDate?: string | null
  applicationDate?: string | null
  approved?: boolean | null
  actionsToTake?: string | null
  softtekManagement?: string | null
  observations?: string | null
  active?: boolean
  version?: number | null
  attempts?: CertificationAttemptPayload[]
}

export interface CertificationAttemptPayload {
  publicId?: string
  scheduledDate?: string | null
  applicationDate?: string | null
  examStatus: CertificationExamStatus
  score?: number | null
  approved?: boolean | null
  result?: string | null
  observations?: string | null
  version?: number | null
}

export interface SaveStudentCertificationPayload {
  applicability: CertificationApplicability
  cycles: CertificationCyclePayload[]
}
