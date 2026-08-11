import { apiRequest } from '../../../shared/api/apiClient'
import type { PagedResponse, SortDirection } from '../../../shared/types/pagination'
import type { PathCollectionOption, PathDetail, PathPayload, PathStatus, PathSummary } from '../../../shared/types/paths'

interface SearchParams {
  query?: string
  status?: string
  page?: number
  size?: number
  sort?: string
  direction?: SortDirection
  signal?: AbortSignal
}

function queryString(params: Omit<SearchParams, 'signal'>) {
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

export function searchPaths(params: SearchParams = {}) {
  return apiRequest<PagedResponse<PathSummary>>(`/admin/paths${queryString({
    ...params,
    page: params.page ?? 0,
    size: params.size ?? 10,
    sort: params.sort ?? 'updatedAt',
    direction: params.direction ?? 'DESC'
  })}`, { signal: params.signal })
}

export function getPath(publicId: string) {
  return apiRequest<PathDetail>(`/admin/paths/${publicId}`)
}

export function searchPathCollectionOptions(query?: string, signal?: AbortSignal) {
  const search = new URLSearchParams()
  if (query?.trim()) search.set('query', query.trim())
  const suffix = search.toString() ? `?${search}` : ''
  return apiRequest<PathCollectionOption[]>(`/admin/paths/collection-options${suffix}`, { signal })
}

export function createPath(payload: PathPayload) {
  return apiRequest<PathDetail>('/admin/paths', { method: 'POST', body: JSON.stringify(payload) })
}

export function updatePath(publicId: string, payload: PathPayload) {
  return apiRequest<PathDetail>(`/admin/paths/${publicId}`, { method: 'PUT', body: JSON.stringify(payload) })
}

export function changePathStatus(publicId: string, status: PathStatus) {
  return apiRequest<PathDetail>(`/admin/paths/${publicId}/status/${status}`, { method: 'POST' })
}
