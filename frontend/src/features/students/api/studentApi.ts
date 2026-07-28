import { apiRequest } from '../../../shared/api/apiClient'
import type {
  CreateStudentPayload, StudentCatalogs, StudentDetail, StudentEffectiveStatus, StudentIdentity,
  StudentPage, StudentSession, UpdateStudentPayload
} from '../../../shared/types/students'
import type { SortDirection } from '../../../shared/types/pagination'

export function searchStudents(params: {
  query?: string
  status?: StudentEffectiveStatus | 'ALL'
  includeDeleted?: boolean
  organizationPublicId?: string
  profilePublicId?: string
  technologicalProfilePublicId?: string
  technologyPublicId?: string
  certificationsEnabled?: boolean
  page?: number
  size?: number
  sort?: string
  direction?: SortDirection
  signal?: AbortSignal
} = {}) {
  const search = new URLSearchParams({
    page: String(params.page ?? 0), size: String(params.size ?? 10),
    includeDeleted: String(params.includeDeleted ?? false), sort: params.sort ?? 'updatedAt',
    direction: params.direction ?? 'DESC'
  })
  if (params.query?.trim()) search.set('query', params.query.trim())
  if (params.status) search.set('status', params.status)
  if (params.organizationPublicId) search.set('organizationPublicId', params.organizationPublicId)
  if (params.profilePublicId) search.set('profilePublicId', params.profilePublicId)
  if (params.technologicalProfilePublicId) search.set('technologicalProfilePublicId', params.technologicalProfilePublicId)
  if (params.technologyPublicId) search.set('technologyPublicId', params.technologyPublicId)
  if (typeof params.certificationsEnabled === 'boolean') search.set('certificationsEnabled', String(params.certificationsEnabled))
  return apiRequest<StudentPage>(`/admin/students?${search.toString()}`, { signal: params.signal }).then((response) => ({
    ...response,
    content: Array.isArray(response.content) ? response.content : [],
    page: Number.isFinite(response.page) ? Math.max(response.page, 0) : 0,
    size: Number.isFinite(response.size) ? Math.max(response.size, 1) : (params.size ?? 10),
    totalElements: Number.isFinite(response.totalElements) ? Math.max(response.totalElements, 0) : 0,
    totalPages: Number.isFinite(response.totalPages) ? Math.max(response.totalPages, 0) : 0
  }))
}
export function getStudentCatalogs(signal?: AbortSignal) { return apiRequest<StudentCatalogs>('/admin/students/catalogs', { signal }) }
export function getStudent(publicId: string) { return apiRequest<StudentDetail>(`/admin/students/${publicId}`) }
export function createStudent(payload: CreateStudentPayload) { return apiRequest<StudentDetail>('/admin/students', { method: 'POST', body: JSON.stringify(payload) }) }
export function updateStudent(publicId: string, payload: UpdateStudentPayload) { return apiRequest<StudentDetail>(`/admin/students/${publicId}`, { method: 'PUT', body: JSON.stringify(payload) }) }
export function activateStudent(publicId: string) { return apiRequest<StudentDetail>(`/admin/students/${publicId}/activate`, { method: 'POST' }) }
export function deactivateStudent(publicId: string) { return apiRequest<StudentDetail>(`/admin/students/${publicId}/deactivate`, { method: 'POST' }) }
export function suspendStudent(publicId: string) { return apiRequest<StudentDetail>(`/admin/students/${publicId}/suspend`, { method: 'POST' }) }
export function archiveStudent(publicId: string) { return apiRequest<StudentDetail>(`/admin/students/${publicId}/archive`, { method: 'POST' }) }
export function deleteStudent(publicId: string, reason: string) { return apiRequest<StudentDetail>(`/admin/students/${publicId}/delete`, { method: 'POST', body: JSON.stringify({ reason }) }) }
export function restoreStudent(publicId: string) { return apiRequest<StudentDetail>(`/admin/students/${publicId}/restore`, { method: 'POST' }) }
export function resetStudentPassword(publicId: string, temporaryPassword: string) { return apiRequest<StudentDetail>(`/admin/students/${publicId}/reset-password`, { method: 'POST', body: JSON.stringify({ temporaryPassword }) }) }
export function getStudentSessions(publicId: string) { return apiRequest<StudentSession[]>(`/admin/students/${publicId}/sessions`) }
export function revokeStudentSession(publicId: string, sessionPublicId: string) { return apiRequest<void>(`/admin/students/${publicId}/sessions/${sessionPublicId}/revoke`, { method: 'POST' }) }
export function revokeAllStudentSessions(publicId: string) { return apiRequest<void>(`/admin/students/${publicId}/sessions/revoke-all`, { method: 'POST' }) }
export function studentLogin(organizationCode: string, email: string, password: string) { return apiRequest<{ student: StudentIdentity }>('/student-auth/login', { method: 'POST', body: JSON.stringify({ organizationCode, email, password }) }) }
export function getCurrentStudent() { return apiRequest<{ student: StudentIdentity }>('/student/me') }
export function studentLogout() { return apiRequest<void>('/student-auth/logout', { method: 'POST' }) }
export function changeStudentPassword(currentPassword: string, newPassword: string, confirmPassword: string) { return apiRequest<void>('/student-auth/change-password', { method: 'POST', body: JSON.stringify({ currentPassword, newPassword, confirmPassword }) }) }
