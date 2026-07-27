export type CatalogType =
  | 'CATEGORIES'
  | 'TECHNOLOGIES'
  | 'PROFESSIONAL_PROFILES'
  | 'TECHNOLOGICAL_PROFILES'
  | 'QUESTION_TYPES'
  | 'DIFFICULTIES'

export type ManagedCatalogStatus = 'ACTIVE' | 'INACTIVE'

export interface CatalogTypeSummary {
  type: CatalogType
  name: string
  description: string
  activeCount: number
  inactiveCount: number
  lastModifiedAt?: string
  tenantAware: boolean
}

export interface CatalogItem {
  id: string
  code: string
  name: string
  description?: string
  status: ManagedCatalogStatus
  displayOrder: number
  scope?: 'GLOBAL' | 'ORGANIZATION'
  organizationPublicId?: string
  organizationName?: string
  suggestedTechnologicalProfile?: string
  createdBy?: number
  updatedBy?: number
  createdAt: string
  updatedAt?: string
  version: number
  dependencyCount: number
}

export interface CatalogDependencies {
  total: number
  details: string[]
  deletable: boolean
}

export interface CatalogPayload {
  code: string
  name: string
  description?: string
  displayOrder?: number
  organizationPublicId?: string
  suggestedTechnologicalProfile?: string
  expectedVersion?: number
}
