import { apiRequest } from '../../../shared/api/apiClient'
import type {
  CertificationAttemptPayload,
  CertificationAttemptView,
  CertificationAvailability,
  CertificationCatalogs,
  CertificationCyclePayload,
  CertificationCycleView,
  CertificationHistoryView,
  PagedResponse,
  SaveStudentCertificationPayload,
  StudentCertificationDetail
} from '../../../shared/types/certifications'

export function getCurrentCertificationAvailability() {
  return apiRequest<CertificationAvailability>('/admin/certification-settings/current')
}

export function getCertificationCatalogs(studentPublicId?: string) {
  const query = new URLSearchParams()
  if (studentPublicId) query.set('studentPublicId', studentPublicId)
  return apiRequest<CertificationCatalogs>(`/admin/certification-catalogs${query.size ? `?${query.toString()}` : ''}`)
}

export function getStudentCertifications(studentPublicId: string) {
  return apiRequest<StudentCertificationDetail>(`/admin/students/${studentPublicId}/certifications`)
}

export function saveStudentCertifications(studentPublicId: string, payload: SaveStudentCertificationPayload) {
  return apiRequest<StudentCertificationDetail>(`/admin/students/${studentPublicId}/certifications`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}

export function createStudentCertificationCycle(studentPublicId: string, payload: CertificationCyclePayload) {
  return apiRequest<CertificationCycleView>(`/admin/students/${studentPublicId}/certifications`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function updateStudentCertificationCycle(studentPublicId: string, certificationId: string, payload: CertificationCyclePayload) {
  return apiRequest<CertificationCycleView>(`/admin/students/${studentPublicId}/certifications/${certificationId}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}

export function makeStudentCertificationPrimary(studentPublicId: string, certificationId: string) {
  return apiRequest<CertificationCycleView>(`/admin/students/${studentPublicId}/certifications/${certificationId}/make-primary`, {
    method: 'POST'
  })
}

export function cancelStudentCertificationCycle(studentPublicId: string, certificationId: string, reason?: string) {
  return apiRequest<CertificationCycleView>(`/admin/students/${studentPublicId}/certifications/${certificationId}/cancel`, {
    method: 'POST',
    body: JSON.stringify({ reason: reason?.trim() || null })
  })
}

export function getStudentCertificationAttempts(studentPublicId: string, certificationId: string, page = 0, size = 10) {
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  return apiRequest<PagedResponse<CertificationAttemptView>>(
    `/admin/students/${studentPublicId}/certifications/${certificationId}/attempts?${query.toString()}`
  )
}

export function createStudentCertificationAttempt(
  studentPublicId: string,
  certificationId: string,
  payload: CertificationAttemptPayload
) {
  return apiRequest<CertificationAttemptView>(
    `/admin/students/${studentPublicId}/certifications/${certificationId}/attempts`,
    { method: 'POST', body: JSON.stringify(payload) }
  )
}

export function updateStudentCertificationAttempt(
  studentPublicId: string,
  certificationId: string,
  attemptId: string,
  payload: CertificationAttemptPayload
) {
  return apiRequest<CertificationAttemptView>(
    `/admin/students/${studentPublicId}/certifications/${certificationId}/attempts/${attemptId}`,
    { method: 'PUT', body: JSON.stringify(payload) }
  )
}

export function getStudentCertificationHistory(studentPublicId: string, page = 0, size = 10) {
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  return apiRequest<PagedResponse<CertificationHistoryView>>(
    `/admin/students/${studentPublicId}/certifications/history?${query.toString()}`
  )
}
