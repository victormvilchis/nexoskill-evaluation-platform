export type PathStatus = 'DRAFT' | 'ACTIVE' | 'INACTIVE' | 'ARCHIVED'
export type PathScope = 'GLOBAL' | 'ORGANIZATION'

export interface PathSummary {
  publicId: string
  code: string
  name: string
  description?: string | null
  status: PathStatus
  contentScope: PathScope
  organizationPublicId: string
  organizationName: string
  collectionCount: number
  formCount: number
  updatedAt: string
  version: number
}

export interface PathCollectionView {
  order: number
  collectionPublicId: string
  name: string
  description?: string | null
  status: string
  contentScope: PathScope
  organizationPublicId: string
  organizationName: string
  formCount: number
}

export interface PathDetail {
  publicId: string
  code: string
  name: string
  description?: string | null
  status: PathStatus
  contentScope: PathScope
  organizationPublicId: string
  organizationName: string
  collections: PathCollectionView[]
  createdAt: string
  updatedAt: string
  version: number
}

export interface PathCollectionOption {
  publicId: string
  name: string
  description?: string | null
  status: string
  contentScope: PathScope
  organizationPublicId: string
  organizationName: string
  formCount: number
}

export interface PathPayload {
  name: string
  description?: string
  collectionPublicIds: string[]
  version?: number
}
