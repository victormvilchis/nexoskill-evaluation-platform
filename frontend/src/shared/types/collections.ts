import type { FormMode, FormStatus } from './forms'

export type CollectionStatus = 'DRAFT' | 'ACTIVE' | 'INACTIVE' | 'ARCHIVED'

export interface CollectionSummary {
  publicId: string
  code: string
  name: string
  description?: string
  status: CollectionStatus
  levelCount: number
  activeLevelCount: number
  updatedAt: string
  version: number
}

export interface CollectionLevel {
  level: number
  unlockRule: 'FIRST_AVAILABLE' | 'PASS_PREVIOUS'
  formPublicId: string
  formCode: string
  formTitle: string
  formStatus: FormStatus
  modeCode: FormMode
  passingScore: number
  durationMinutes?: number
}

export interface CollectionDetail {
  publicId: string
  code: string
  name: string
  description?: string
  status: CollectionStatus
  levels: CollectionLevel[]
  createdAt: string
  updatedAt: string
  version: number
}

export interface CollectionPayload {
  name: string
  description?: string
  formPublicIds: string[]
  version?: number
}

export interface CollectionFormOption {
  publicId: string
  code: string
  title: string
  status: FormStatus
  modeCode: FormMode
  passingScore: number
  durationMinutes?: number
}
