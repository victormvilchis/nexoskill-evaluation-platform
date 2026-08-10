import { apiRequest } from '../../../shared/api/apiClient'
import type {
  AdministrativeHistoryPage,
  CreateStudentPayload,
  StudentAdministrationView,
  StudentCatalogs,
  StudentDeletionResult,
  StudentDetail,
  StudentEffectiveStatus,
  StudentIdentity,
  StudentPage,
  StudentFilterOptions,
  StudentCredentialResult,
  StudentSession,
  UpdateStudentPayload
} from '../../../shared/types/students'
import type { SortDirection } from '../../../shared/types/pagination'

export function searchStudents(params: {
  query?: string
  status?: StudentEffectiveStatus | 'ALL'
  organizationPublicId?: string
  profilePublicId?: string
  technologicalProfilePublicId?: string
  technologyPublicId?: string
  certificationsEnabled?: boolean
  certificationFocus?: string
  page?: number
  size?: number
  sort?: string
  direction?: SortDirection
  signal?: AbortSignal
} = {}) {
  const search = new URLSearchParams({
    page: String(params.page ?? 0),
    size: String(params.size ?? 10),
    sort: params.sort ?? 'updatedAt',
    direction: params.direction ?? 'DESC'
  })
  if (params.query?.trim()) search.set('query', params.query.trim())
  if (params.status) search.set('status', params.status)
  if (params.organizationPublicId) search.set('organizationPublicId', params.organizationPublicId)
  if (params.profilePublicId) search.set('profilePublicId', params.profilePublicId)
  if (params.technologicalProfilePublicId) search.set('technologicalProfilePublicId', params.technologicalProfilePublicId)
  if (params.technologyPublicId) search.set('technologyPublicId', params.technologyPublicId)
  if (typeof params.certificationsEnabled === 'boolean') search.set('certificationsEnabled', String(params.certificationsEnabled))
  if (params.certificationFocus) search.set('certificationFocus', params.certificationFocus)
  return apiRequest<StudentPage>(`/admin/students?${search.toString()}`, { signal: params.signal }).then((response) => ({
    ...response,
    content: Array.isArray(response.content) ? response.content : [],
    page: Number.isFinite(response.page) ? Math.max(response.page, 0) : 0,
    size: Number.isFinite(response.size) ? Math.max(response.size, 1) : (params.size ?? 10),
    totalElements: Number.isFinite(response.totalElements) ? Math.max(response.totalElements, 0) : 0,
    totalPages: Number.isFinite(response.totalPages) ? Math.max(response.totalPages, 0) : 0
  }))
}

export function getStudentFilterOptions(organizationPublicId?: string, signal?: AbortSignal) {
  const query = new URLSearchParams()
  if (organizationPublicId) query.set('organizationPublicId', organizationPublicId)
  const suffix = query.size ? `?${query.toString()}` : ''
  return apiRequest<StudentFilterOptions>(`/admin/students/filter-options${suffix}`, { signal }).then((response) => ({
    organizations: Array.isArray(response.organizations) ? response.organizations : [],
    roles: Array.isArray(response.roles) ? response.roles : [],
    technologies: Array.isArray(response.technologies) ? response.technologies : [],
    certificationStatusAvailable: Boolean(response.certificationStatusAvailable)
  }))
}

export function getStudentCatalogs(organizationPublicId?: string, signal?: AbortSignal) {
  const query = new URLSearchParams()
  if (organizationPublicId) query.set('organizationPublicId', organizationPublicId)
  const suffix = query.size > 0 ? `?${query.toString()}` : ''
  return apiRequest<StudentCatalogs>(`/admin/students/catalogs${suffix}`, { signal })
}

export function getStudent(publicId: string) {
  return apiRequest<StudentDetail>(`/admin/students/${publicId}`)
}

export function createStudent(payload: CreateStudentPayload) {
  return apiRequest<StudentCredentialResult>('/admin/students', { method: 'POST', body: JSON.stringify(payload) })
}

export function updateStudent(publicId: string, payload: UpdateStudentPayload) {
  return apiRequest<StudentDetail>(`/admin/students/${publicId}`, { method: 'PUT', body: JSON.stringify(payload) })
}

export function getStudentAdministration(publicId: string) {
  return apiRequest<StudentAdministrationView>(`/admin/students/${publicId}/administration`)
}

export function getStudentAdministrativeHistory(publicId: string, page = 0, size = 10) {
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  return apiRequest<AdministrativeHistoryPage>(`/admin/students/${publicId}/administrative-history?${query.toString()}`)
}

export function activateStudent(publicId: string) {
  return apiRequest<StudentDetail>(`/admin/students/${publicId}/activate`, { method: 'POST' })
}

export function deactivateStudent(publicId: string) {
  return apiRequest<StudentDetail>(`/admin/students/${publicId}/deactivate`, { method: 'POST' })
}

export function deleteStudent(publicId: string) {
  return apiRequest<StudentDeletionResult>(`/admin/students/${publicId}?confirmed=true`, { method: 'DELETE' })
}

export function resetStudentPassword(publicId: string) {
  return apiRequest<StudentCredentialResult>(`/admin/students/${publicId}/reset-password`, { method: 'POST' })
}

export function getStudentSessions(publicId: string) {
  return apiRequest<StudentSession[]>(`/admin/students/${publicId}/sessions`)
}

export function revokeStudentSession(publicId: string, sessionPublicId: string) {
  return apiRequest<void>(`/admin/students/${publicId}/sessions/${sessionPublicId}/revoke`, { method: 'POST' })
}

export function revokeAllStudentSessions(publicId: string) {
  return apiRequest<void>(`/admin/students/${publicId}/sessions/revoke-all`, { method: 'POST' })
}

export function studentLogin(organizationCode: string, email: string, password: string) {
  return apiRequest<{ student: StudentIdentity }>('/student-auth/login', {
    method: 'POST',
    body: JSON.stringify({ organizationCode, email, password })
  })
}

export function getCurrentStudent() {
  return apiRequest<{ student: StudentIdentity }>('/student/me')
}

export function studentLogout() {
  return apiRequest<void>('/student-auth/logout', { method: 'POST' })
}

export function changeStudentPassword(currentPassword: string, newPassword: string, confirmPassword: string) {
  return apiRequest<void>('/student-auth/change-password', {
    method: 'POST',
    body: JSON.stringify({ currentPassword, newPassword, confirmPassword })
  })
}
