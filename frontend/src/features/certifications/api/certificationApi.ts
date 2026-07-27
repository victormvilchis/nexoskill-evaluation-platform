import { apiRequest } from '../../../shared/api/apiClient'
import type {
  CertificationAvailability,
  CertificationCatalogs,
  SaveStudentCertificationPayload,
  StudentCertificationDetail
} from '../../../shared/types/certifications'

export function getCurrentCertificationAvailability() {
  return apiRequest<CertificationAvailability>('/admin/certification-settings/current')
}

export function getCertificationCatalogs() {
  return apiRequest<CertificationCatalogs>('/admin/certification-catalogs')
}

export function getStudentCertifications(studentPublicId: string) {
  return apiRequest<StudentCertificationDetail>(`/admin/students/${studentPublicId}/certifications`)
}

export function saveStudentCertifications(
  studentPublicId: string,
  payload: SaveStudentCertificationPayload
) {
  return apiRequest<StudentCertificationDetail>(`/admin/students/${studentPublicId}/certifications`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}
