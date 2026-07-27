import { apiRequest } from '../../../shared/api/apiClient'

export type GlobalContentType = 'CATEGORY' | 'QUESTION' | 'FORM' | 'COLLECTION' | 'PATH'
export type ContentScope = 'GLOBAL' | 'ORGANIZATION'
export type EditorialStatus = 'DRAFT' | 'UNDER_REVIEW' | 'PUBLISHED' | 'REJECTED' | 'ARCHIVED'
export type DistributionMode = 'GLOBAL_REFERENCE' | 'ORGANIZATION_COPY'
export type AccessMode = 'READ_ONLY' | 'USE_DIRECT' | 'EDITABLE_COPY'
export type UpdatePolicy = 'FIXED_VERSION' | 'MANUAL' | 'AUTOMATIC' | 'NEW_ASSIGNMENTS_ONLY'
export type DuplicateResolution = 'USE_EXISTING' | 'CREATE_NEW_VERSION' | 'CREATE_DISTINCT' | 'CANCEL'

export interface ContentResource {
  contentType: GlobalContentType
  internalId: number
  publicId: string
  name: string
  description?: string
  status: string
  scope: ContentScope
  ownerOrganizationId?: number
  ownerOrganizationPublicId?: string
  ownerOrganizationName?: string
  createdBy?: number
  createdAt: string
  updatedBy?: number
  updatedAt: string
  version: number
  promoted: boolean
  distributed: boolean
  functionalHash: string
}

export interface ReviewPage {
  content: ContentResource[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface Dependency {
  contentType: GlobalContentType
  internalId: number
  publicId: string
  name: string
  scope: ContentScope
  ownerOrganizationId?: number
  version: number
  globalEquivalentAvailable: boolean
  globalEquivalentPublicId?: string
}

export interface PromotionPreview {
  source: ContentResource
  dependencies: Dependency[]
  possibleDuplicates: ContentResource[]
  promotable: boolean
  warnings: string[]
}

export interface PromotionView {
  publicId: string
  contentType: GlobalContentType
  sourceOrganizationPublicId: string
  sourceOrganizationName: string
  sourceContentPublicId: string
  sourceContentName: string
  sourceVersion: number
  globalContentPublicId: string
  globalVersion: number
  status: EditorialStatus
  notes?: string
  promotedBy: number
  promotedAt: string
  reviewedBy?: number
  reviewedAt?: string
  publishedBy?: number
  publishedAt?: string
  rejectionReason?: string
  dependencies: Dependency[]
}

export interface DistributionJobView {
  publicId: string
  contentType: GlobalContentType
  globalContentPublicId: string
  globalVersion: number
  distributionMode: DistributionMode
  status: string
  totalOrganizations: number
  processedOrganizations: number
  successfulOrganizations: number
  failedOrganizations: number
  skippedOrganizations: number
  requestedAt: string
  startedAt?: string
  finishedAt?: string
  results: Array<{
    organizationPublicId?: string
    organizationName: string
    status: 'SUCCESS' | 'SKIPPED' | 'FAILED'
    grantPublicId?: string
    targetContentPublicId?: string
    errorCode?: string
    errorMessage?: string
    attemptNumber: number
    processedAt: string
  }>
}

export interface ReviewParams {
  query?: string
  organizationPublicId?: string
  contentType?: GlobalContentType | 'ALL'
  scope?: ContentScope | 'ALL'
  status?: string
  promoted?: boolean
  distributed?: boolean
  page?: number
  size?: number
  signal?: AbortSignal
}

export function reviewGlobalContent(params: ReviewParams) {
  const search = new URLSearchParams()
  if (params.query?.trim()) search.set('query', params.query.trim())
  if (params.organizationPublicId) search.set('organizationPublicId', params.organizationPublicId)
  if (params.contentType && params.contentType !== 'ALL') search.set('contentType', params.contentType)
  if (params.scope && params.scope !== 'ALL') search.set('scope', params.scope)
  if (params.status && params.status !== 'ALL') search.set('status', params.status)
  if (params.promoted !== undefined) search.set('promoted', String(params.promoted))
  if (params.distributed !== undefined) search.set('distributed', String(params.distributed))
  search.set('page', String(params.page ?? 0))
  search.set('size', String(params.size ?? 20))
  return apiRequest<ReviewPage>(`/admin/global-content/review?${search.toString()}`, {
    signal: params.signal
  })
}

export function getPromotionPreview(type: GlobalContentType, sourcePublicId: string) {
  const search = new URLSearchParams({ type, sourcePublicId })
  return apiRequest<PromotionPreview>(`/admin/global-content/promotions/preview?${search.toString()}`)
}

export function listPromotions(signal?: AbortSignal) {
  return apiRequest<PromotionView[]>('/admin/global-content/promotions', { signal })
}

export function promoteContent(payload: {
  contentType: GlobalContentType
  sourcePublicId: string
  includeDependencies: boolean
  duplicateResolution: DuplicateResolution
  existingGlobalPublicId?: string
  notes?: string
}) {
  return apiRequest<PromotionView>('/admin/global-content/promotions', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function submitPromotion(publicId: string) {
  return apiRequest<PromotionView>(`/admin/global-content/promotions/${publicId}/submit-review`, {
    method: 'POST'
  })
}

export function publishPromotion(publicId: string) {
  return apiRequest<PromotionView>(`/admin/global-content/promotions/${publicId}/publish`, {
    method: 'POST'
  })
}

export function distributeContent(payload: {
  contentType: GlobalContentType
  globalContentPublicId: string
  globalVersion: number
  organizationPublicIds: string[]
  distributionMode: DistributionMode
  accessMode: AccessMode
  cloningAllowed: boolean
  organizationEditable: boolean
  updatePolicy: UpdatePolicy
  notes?: string
}) {
  return apiRequest<DistributionJobView>('/admin/global-content/distributions', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}
