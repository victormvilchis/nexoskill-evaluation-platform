import { apiRequest } from '../../../shared/api/apiClient'
import type {
  CollectionDetail,
  CollectionFormOption,
  CollectionPayload,
  CollectionStatus,
  CollectionSummary
} from '../../../shared/types/collections'
import type { PagedResponse, SortDirection } from '../../../shared/types/pagination'

interface SearchParams {
  query?: string
  status?: string
  page?: number
  size?: number
  sort?: string
  direction?: SortDirection
  signal?: AbortSignal
}

function queryString(params: Omit<SearchParams, 'signal'>): string {
  const search = new URLSearchParams()
  if (params.query?.trim()) search.set('query', params.query.trim())
  if (params.status) search.set('status', params.status)
  if (params.page !== undefined) search.set('page', String(params.page))
  if (params.size !== undefined) search.set('size', String(params.size))
  if (params.sort) search.set('sort', params.sort)
  if (params.direction) search.set('direction', params.direction)
  const value = search.toString()
  return value ? `?${value}` : ''
}

export function searchLearningCollections(params: SearchParams = {}) {
  return apiRequest<PagedResponse<CollectionSummary>>(
    `/admin/collections${queryString({
      ...params,
      page: params.page ?? 0,
      size: params.size ?? 10,
      sort: params.sort ?? 'updatedAt',
      direction: params.direction ?? 'DESC'
    })}`,
    { signal: params.signal }
  )
}

export function getLearningCollection(publicId: string) {
  return apiRequest<CollectionDetail>(`/admin/collections/${publicId}`)
}

export function searchCollectionFormOptions(params: SearchParams = {}) {
  return apiRequest<CollectionFormOption[]>(
    `/admin/collections/form-options${queryString(params)}`,
    { signal: params.signal }
  )
}

export function createLearningCollection(payload: CollectionPayload) {
  return apiRequest<CollectionDetail>('/admin/collections', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function updateLearningCollection(publicId: string, payload: CollectionPayload) {
  return apiRequest<CollectionDetail>(`/admin/collections/${publicId}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}

export function changeLearningCollectionStatus(publicId: string, status: CollectionStatus) {
  return apiRequest<CollectionDetail>(
    `/admin/collections/${publicId}/status/${status}`,
    { method: 'POST' }
  )
}
