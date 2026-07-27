export type CertificationType =
  | 'DEVELOPMENT_SECURITY'
  | 'TECHNOLOGICAL'
  | 'ONE'
  | 'NORMATIVE_TESTING'
  | 'AGILE'

export type CertificationStatus =
  | 'NOT_APPLICABLE'
  | 'PENDING'
  | 'IN_PROGRESS'
  | 'SCHEDULED'
  | 'CERTIFIED'
  | 'NOT_CERTIFIED'
  | 'EXPIRED'
  | 'CANCELLED'

export type CertificationExamStatus =
  | 'NOT_SCHEDULED'
  | 'SCHEDULED'
  | 'RESCHEDULED'
  | 'COMPLETED'
  | 'PASSED'
  | 'FAILED'
  | 'ABSENT'
  | 'CANCELLED'

export type TechnologicalProfile = 'DEVELOPER' | 'FUNCTIONAL' | 'SPECIALIZED_PLATFORM'

export interface CertificationAvailability {
  appliesCertifications: boolean
  operatorAllowed: boolean
  organizationPublicId: string
  organizationName: string
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
  technologicalProfiles: CertificationEnumOption[]
  certificationStatuses: CertificationEnumOption[]
  examStatuses: CertificationEnumOption[]
  certificationTypes: CertificationEnumOption[]
}

export interface CertificationProfileView {
  publicId: string
  professionalProfilePublicId: string
  professionalProfileName: string
  certificationTechnologyPublicId: string
  certificationTechnologyName: string
  enrollmentDate: string
  technologicalProfile: TechnologicalProfile
  version: number
}

export interface CertificationRequirementView {
  publicId: string | null
  type: CertificationType
  applies: boolean
  certificationStatus: CertificationStatus
  examStatus: CertificationExamStatus
  calculatedDeadline: string | null
  manualDeadline: string | null
  effectiveDeadline: string | null
  deadlineOverrideReason: string | null
  applicationDate: string | null
  score: number | null
  currentAttempt: number | null
  actionsToTake: string | null
  observations: string | null
  version: number | null
}

export interface CertificationAttemptView {
  publicId: string
  type: CertificationType
  attemptNumber: number
  scheduledDate: string | null
  applicationDate: string | null
  examStatus: CertificationExamStatus
  score: number | null
  result: string | null
  observations: string | null
  createdAt: string
}

export interface CertificationHistoryView {
  publicId: string
  eventType: string
  previousValues: string | null
  newValues: string | null
  reason: string | null
  changedAt: string
}

export interface StudentCertificationDetail {
  studentPublicId: string
  studentName: string
  organizationPublicId: string
  organizationName: string
  appliesCertifications: boolean
  profile: CertificationProfileView | null
  requirements: CertificationRequirementView[]
  attempts: CertificationAttemptView[]
  history: CertificationHistoryView[]
}

export interface CertificationRequirementPayload {
  type: CertificationType
  applies: boolean
  certificationStatus: CertificationStatus
  examStatus: CertificationExamStatus
  manualDeadline: string | null
  deadlineOverrideReason: string | null
  applicationDate: string | null
  score: number | null
  currentAttempt: number | null
  actionsToTake: string | null
  observations: string | null
  version: number | null
}

export interface CertificationAttemptPayload {
  type: CertificationType
  attemptNumber: number
  scheduledDate: string | null
  applicationDate: string | null
  examStatus: CertificationExamStatus
  score: number | null
  result: string | null
  observations: string | null
}

export interface SaveStudentCertificationPayload {
  professionalProfilePublicId: string
  certificationTechnologyPublicId: string
  enrollmentDate: string
  technologicalProfile: TechnologicalProfile
  profileVersion: number | null
  requirements: CertificationRequirementPayload[]
  newAttempts: CertificationAttemptPayload[]
}
