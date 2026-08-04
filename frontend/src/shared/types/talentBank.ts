import type { StudentCatalogs, StudentDetail, UpdateStudentPayload } from './students'

export type TalentType = 'ACADEMY' | 'PROSPECT' | 'BBVA_EXIT'
export type TalentProfileCode = 'TR' | 'JR' | 'STD' | 'SR'

export interface TalentOrganizationRef {
  publicId: string
  code: string
  name: string
  manualStudentCode: boolean
  appliesCertifications: boolean
}

export interface TalentTechnologyRef {
  publicId: string
  code: string
  name: string
}

export interface TalentSummary {
  publicId: string
  studentCode: string
  corporateUser: string | null
  email: string
  firstName: string
  lastName: string
  displayName: string
  talentType: TalentType
  profileCode: TalentProfileCode | null
  technology: TalentTechnologyRef | null
  currentTechnologyExpertise: string | null
  organizationHiredOn: string | null
  validFrom: string
  expiresAt: string
  admissionDate: string | null
  hasCv: boolean
  organization: TalentOrganizationRef
  movedAt: string | null
  createdAt: string
  updatedAt: string
  version: number
}

export interface TalentPage {
  content: TalentSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface TalentCatalogs {
  organization: TalentOrganizationRef
  profiles: TalentProfileCode[]
  technologies: TalentTechnologyRef[]
}

export interface AcademyTalentPayload {
  organizationPublicId?: string
  studentCode?: string
  email: string
  firstName: string
  lastName: string
  displayName?: string
  validFrom: string
  expiresAt: string
  organizationHiredOn: string
  profileCode: TalentProfileCode
  technologyPublicId: string
  version?: number
}

export interface ProspectTalentPayload {
  organizationPublicId?: string
  studentCode?: string
  corporateUser?: string
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
  appliesJira?: boolean
  version?: number
}

export interface TalentCreateResult {
  talent: TalentSummary
}

export interface TalentHistoryItem {
  publicId: string
  eventType: string
  description: string
  occurredAt: string
}

export interface TalentHistoryPage {
  content: TalentHistoryItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface TalentCvMetadata {
  publicId: string
  fileName: string
  contentType: string
  extension: string
  fileSize: number
  updatedAt: string
}

export interface PermanentDeletionCandidate {
  publicId: string
  displayName: string
  email: string
  status: 'ACTIVE' | 'INACTIVE' | 'EXPIRED' | 'DELETED'
  module: 'COLLABORATOR' | 'TALENT_BANK'
  talentType: TalentType | null
  organizationPublicId: string
  organizationCode: string
  organizationName: string
  hasCv: boolean
}

export interface PermanentDeletionPage {
  content: PermanentDeletionCandidate[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface PermanentDeletionResult {
  operationReference: string
  deletedAt: string
}

export type TalentFoundation = StudentDetail
export type TalentFoundationCatalogs = StudentCatalogs
export type TalentConversionPayload = UpdateStudentPayload
