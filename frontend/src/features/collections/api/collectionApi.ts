import { apiRequest } from '../../../shared/api/apiClient'
import type {
  CollectionDetail,
  CollectionFormOption,
  CollectionPayload,
  CollectionStatus,
  CollectionSummary
} from '../../../shared/types/collections'

interface SearchParams {
  query?: string
  status?: string
  signal?: AbortSignal
}

function queryString(params: Omit<SearchParams, 'signal'>): string {
  const search = new URLSearchParams()
  if (params.query?.trim()) search.set('query', params.query.trim())
  if (params.status) search.set('status', params.status)
  const value = search.toString()
  return value ? `?${value}` : ''
}

export function searchLearningCollections(params: SearchParams = {}) {
  return apiRequest<CollectionSummary[]>(
    `/admin/collections${queryString(params)}`,
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

export function updateLearningCollection(
  publicId: string,
  payload: CollectionPayload
) {
  return apiRequest<CollectionDetail>(`/admin/collections/${publicId}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}

export function changeLearningCollectionStatus(
  publicId: string,
  status: CollectionStatus
) {
  return apiRequest<CollectionDetail>(
    `/admin/collections/${publicId}/status/${status}`,
    { method: 'POST' }
  )
}
