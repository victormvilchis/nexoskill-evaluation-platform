export type OrganizationStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED' | 'EXPIRED' | 'DELETED'
export type OrganizationType = 'GLOBAL' | 'CUSTOMER'
export type ContentMode = 'GLOBAL_CATALOG' | 'CLEAN' | 'CUSTOM'

export interface OrganizationSummary {
  publicId: string
  code: string
  name: string
  organizationType: OrganizationType
  status: OrganizationStatus
  contentMode: ContentMode
  appliesCertifications: boolean
  manualStudentCode: boolean
  studentCount: number
  expiresOn?: string
  updatedAt: string
}

export interface OrganizationDetail extends OrganizationSummary {
  validFrom: string
  contractedSeats?: number
  includedReplacements?: number
  additionalReplacements?: number
  standardReleaseHours?: number
  exhaustedReleaseDays?: number
  cycleStartsOn?: string
  cycleEndsOn?: string
  statusChangedAt?: string
  statusReason?: string
  createdAt: string
  version: number
}

export interface OrganizationPayload {
  name: string
  code?: string
  contentMode: ContentMode
  appliesCertifications: boolean
  manualStudentCode: boolean
  expiresOn?: string
  contractedSeats: number
  includedReplacements: number
  additionalReplacements?: number
  standardReleaseHours?: number
  exhaustedReleaseDays?: number
  cycleStartsOn?: string
  cycleEndsOn?: string
  version?: number
}

export interface OrganizationStatusHistory {
  previousStatus?: OrganizationStatus
  newStatus: OrganizationStatus
  reason?: string
  changedBy?: number
  changedAt: string
}

export interface OrganizationPage {
  content: OrganizationSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
