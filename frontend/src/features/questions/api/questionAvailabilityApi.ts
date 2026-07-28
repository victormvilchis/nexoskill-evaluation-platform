import { apiRequest } from '../../../shared/api/apiClient'

export type QuestionAvailabilityMode = 'GLOBAL' | 'SELECTED_ORGANIZATIONS'
export interface QuestionAvailabilityOrganization { publicId: string; code: string; name: string }
export interface QuestionAvailability {
  mode: QuestionAvailabilityMode
  organizations: QuestionAvailabilityOrganization[]
}

export function getQuestionAvailability(questionPublicId: string, signal?: AbortSignal) {
  return apiRequest<QuestionAvailability>(`/admin/questions/${questionPublicId}/availability`, { signal })
}

export function updateQuestionAvailability(questionPublicId: string, availability: {
  mode: QuestionAvailabilityMode
  organizationPublicIds: string[]
}) {
  return apiRequest<QuestionAvailability>(`/admin/questions/${questionPublicId}/availability`, {
    method: 'PUT',
    body: JSON.stringify(availability)
  })
}
