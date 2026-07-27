import { apiRequest } from '../../../shared/api/apiClient'
import type {
  CatalogDependencies,
  CatalogItem,
  CatalogPayload,
  CatalogType,
  CatalogTypeSummary,
  ManagedCatalogStatus
} from '../types/catalogs'

export function getCatalogTypes(signal?: AbortSignal) {
  return apiRequest<CatalogTypeSummary[]>('/admin/catalogs', { signal })
}

export function getCatalogItems(
  type: CatalogType,
  options: {
    status?: ManagedCatalogStatus | 'ALL'
    organizationPublicId?: string
    signal?: AbortSignal
  } = {}
) {
  const query = new URLSearchParams({ status: options.status ?? 'ACTIVE' })
  if (options.organizationPublicId) query.set('organizationPublicId', options.organizationPublicId)
  return apiRequest<CatalogItem[]>(`/admin/catalogs/${type}?${query}`, { signal: options.signal })
}

export function createCatalogItem(type: CatalogType, payload: CatalogPayload) {
  return apiRequest<CatalogItem>(`/admin/catalogs/${type}`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function updateCatalogItem(type: CatalogType, id: string, payload: CatalogPayload) {
  return apiRequest<CatalogItem>(`/admin/catalogs/${type}/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}

export function changeCatalogItemStatus(
  type: CatalogType,
  id: string,
  action: 'activate' | 'deactivate',
  expectedVersion: number
) {
  return apiRequest<CatalogItem>(
    `/admin/catalogs/${type}/${encodeURIComponent(id)}/${action}`,
    { method: 'POST', body: JSON.stringify({ expectedVersion }) }
  )
}

export function getCatalogDependencies(type: CatalogType, id: string, signal?: AbortSignal) {
  return apiRequest<CatalogDependencies>(
    `/admin/catalogs/${type}/${encodeURIComponent(id)}/dependencies`,
    { signal }
  )
}

export function deleteCatalogItem(type: CatalogType, id: string, expectedVersion: number) {
  return apiRequest<void>(`/admin/catalogs/${type}/${encodeURIComponent(id)}`, {
    method: 'DELETE',
    body: JSON.stringify({ expectedVersion })
  })
}
