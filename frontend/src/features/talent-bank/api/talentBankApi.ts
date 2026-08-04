import { apiRequest } from '../../../shared/api/apiClient'
import type { StudentDetail, UpdateStudentPayload } from '../../../shared/types/students'
import type {
  AcademyTalentPayload,
  PermanentDeletionPage,
  PermanentDeletionResult,
  ProspectTalentPayload,
  TalentCatalogs,
  TalentCreateResult,
  TalentCvMetadata,
  TalentHistoryPage,
  TalentPage,
  TalentSummary,
  TalentType
} from '../../../shared/types/talentBank'
import type { SortDirection } from '../../../shared/types/pagination'

export function searchTalents(params: {
  query?: string
  organizationPublicId?: string
  type?: TalentType | 'ALL'
  profileCode?: string
  technologyPublicId?: string
  sort?: string
  direction?: SortDirection
  page?: number
  size?: number
  signal?: AbortSignal
} = {}) {
  const search = new URLSearchParams({
    page: String(params.page ?? 0),
    size: String(params.size ?? 10),
    sort: params.sort ?? 'updatedAt',
    direction: params.direction ?? 'DESC',
    type: params.type ?? 'ALL'
  })
  if (params.query?.trim()) search.set('query', params.query.trim())
  if (params.organizationPublicId) search.set('organizationPublicId', params.organizationPublicId)
  if (params.profileCode) search.set('profileCode', params.profileCode)
  if (params.technologyPublicId) search.set('technologyPublicId', params.technologyPublicId)
  return apiRequest<TalentPage>(`/admin/talent-bank?${search.toString()}`, { signal: params.signal })
}

export function getTalent(publicId: string, signal?: AbortSignal) {
  return apiRequest<TalentSummary>(`/admin/talent-bank/${publicId}`, { signal })
}

export function getTalentHistory(publicId: string, page = 0, size = 10, signal?: AbortSignal) {
  const search = new URLSearchParams({ page: String(page), size: String(size) })
  return apiRequest<TalentHistoryPage>(`/admin/talent-bank/${publicId}/history?${search.toString()}`, { signal })
}

export function getTalentFoundation(publicId: string, signal?: AbortSignal) {
  return apiRequest<StudentDetail>(`/admin/talent-bank/${publicId}/foundation`, { signal })
}

export function getTalentCatalogs(organizationPublicId?: string, signal?: AbortSignal) {
  const search = new URLSearchParams()
  if (organizationPublicId) search.set('organizationPublicId', organizationPublicId)
  const suffix = search.size ? `?${search.toString()}` : ''
  return apiRequest<TalentCatalogs>(`/admin/talent-bank/catalogs${suffix}`, { signal })
}

export function createAcademyTalent(payload: AcademyTalentPayload) {
  return apiRequest<TalentCreateResult>('/admin/talent-bank/academy', {
    method: 'POST', body: JSON.stringify(payload)
  })
}

export function updateAcademyTalent(publicId: string, payload: AcademyTalentPayload & { version: number }) {
  return apiRequest<TalentSummary>(`/admin/talent-bank/${publicId}/academy`, {
    method: 'PUT', body: JSON.stringify(payload)
  })
}

export function createProspectTalent(payload: ProspectTalentPayload) {
  return apiRequest<TalentCreateResult>('/admin/talent-bank/prospects', {
    method: 'POST', body: JSON.stringify(payload)
  })
}

export function updateFullTalent(publicId: string, payload: ProspectTalentPayload & { version: number }) {
  return apiRequest<TalentSummary>(`/admin/talent-bank/${publicId}/full`, {
    method: 'PUT', body: JSON.stringify(payload)
  })
}

export function convertTalent(publicId: string, payload: UpdateStudentPayload) {
  return apiRequest<StudentDetail>(`/admin/talent-bank/${publicId}/convert`, {
    method: 'POST', body: JSON.stringify(payload)
  })
}

export function getTalentCv(publicId: string, signal?: AbortSignal) {
  return apiRequest<TalentCvMetadata | null>(`/admin/talent-bank/${publicId}/cv`, { signal })
}

export function uploadTalentCv(publicId: string, file: File) {
  const body = new FormData()
  body.append('file', file)
  return apiRequest<TalentCvMetadata>(`/admin/talent-bank/${publicId}/cv`, { method: 'POST', body })
}

async function fetchTalentCv(publicId: string) {
  const response = await fetch(`/api/v1/admin/talent-bank/${publicId}/cv/download`, {
    credentials: 'include',
    headers: window.localStorage.getItem('nexoskill:organization-context')
      ? { 'X-Organization-Context': window.localStorage.getItem('nexoskill:organization-context')! }
      : undefined
  })
  if (!response.ok) throw new Error('No fue posible consultar el CV.')
  const disposition = response.headers.get('content-disposition') ?? ''
  const match = disposition.match(/filename\*=UTF-8''([^;]+)/i)
  return {
    blob: await response.blob(),
    fileName: match?.[1] ? decodeURIComponent(match[1]) : 'cv'
  }
}

export async function viewTalentCv(publicId: string) {
  const { blob } = await fetchTalentCv(publicId)
  const url = URL.createObjectURL(blob)
  const opened = window.open(url, '_blank', 'noopener,noreferrer')
  if (!opened) {
    URL.revokeObjectURL(url)
    throw new Error('El navegador bloqueó la apertura del CV.')
  }
  window.setTimeout(() => URL.revokeObjectURL(url), 60_000)
}

export async function downloadTalentCv(publicId: string) {
  const { blob, fileName } = await fetchTalentCv(publicId)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

export function searchPermanentDeletions(params: {
  query?: string
  module?: 'ALL' | 'COLLABORATOR' | 'TALENT_BANK'
  organizationPublicId?: string
  page?: number
  size?: number
  signal?: AbortSignal
} = {}) {
  const search = new URLSearchParams({
    module: params.module ?? 'ALL',
    page: String(params.page ?? 0),
    size: String(params.size ?? 10)
  })
  if (params.query?.trim()) search.set('query', params.query.trim())
  if (params.organizationPublicId) search.set('organizationPublicId', params.organizationPublicId)
  return apiRequest<PermanentDeletionPage>(`/admin/permanent-deletions?${search.toString()}`, { signal: params.signal })
}

export function permanentlyDeletePerson(publicId: string) {
  return apiRequest<PermanentDeletionResult>(`/admin/permanent-deletions/${publicId}`, {
    method: 'POST', body: JSON.stringify({ confirmed: true })
  })
}
