import { apiRequest } from '../../../shared/api/apiClient'
import type {
  StudentExperiencePayload,
  StudentImportApplyCommand,
  StudentImportApplyResult,
  StudentImportPreview
} from '../types/studentImport'

export function previewStudentImport(file: File, signal?: AbortSignal) {
  const body = new FormData()
  body.append('file', file)
  return apiRequest<StudentImportPreview>('/admin/students/import/preview', {
    method: 'POST',
    body,
    signal
  })
}

export function applyStudentImport(command: StudentImportApplyCommand) {
  return apiRequest<StudentImportApplyResult>('/admin/students/import/apply', {
    method: 'POST',
    body: JSON.stringify(command)
  })
}

export function discardStudentImport(token: string) {
  return apiRequest<void>(`/admin/students/import/${encodeURIComponent(token)}`, { method: 'DELETE' })
}

export function getStudentExperience(studentPublicId: string) {
  return apiRequest<StudentExperiencePayload>(`/admin/students/${studentPublicId}/experience`)
}

export function updateStudentExperience(studentPublicId: string, payload: StudentExperiencePayload) {
  return apiRequest<StudentExperiencePayload>(`/admin/students/${studentPublicId}/experience`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}
