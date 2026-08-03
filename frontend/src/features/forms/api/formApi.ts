import { apiRequest } from '../../../shared/api/apiClient'
import type {
  FormCategoryOption,
  FormClonePayload,
  FormContentScope,
  FormDetail,
  FormPayload,
  FormQuestionOptionPage
} from '../../../shared/types/forms'

function targetQuery(scope: FormContentScope, organizationPublicId?: string) {
  const query = new URLSearchParams({ scope })
  if (scope === 'ORGANIZATION' && organizationPublicId) {
    query.set('organizationPublicId', organizationPublicId)
  }
  return query
}

export function getForm(publicId: string, signal?: AbortSignal) {
  return apiRequest<FormDetail>(`/admin/forms/${publicId}`, { signal })
}

export function createForm(payload: FormPayload) {
  return apiRequest<FormDetail>('/admin/forms', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function updateForm(publicId: string, payload: FormPayload) {
  return apiRequest<FormDetail>(`/admin/forms/${publicId}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}

export function cloneForm(publicId: string, payload: FormClonePayload) {
  return apiRequest<FormDetail>(`/admin/forms/${publicId}/clone`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function getFormQuestionOptions(params: {
  scope: FormContentScope
  organizationPublicId?: string
  query?: string
  categoryPublicId?: string
  page?: number
  size?: number
  signal?: AbortSignal
}) {
  const query = targetQuery(params.scope, params.organizationPublicId)
  query.set('page', String(params.page ?? 0))
  query.set('size', String(params.size ?? 10))
  if (params.query?.trim()) query.set('query', params.query.trim())
  if (params.categoryPublicId) query.set('categoryPublicId', params.categoryPublicId)
  return apiRequest<FormQuestionOptionPage>(`/admin/forms/content-options/questions?${query}`, {
    signal: params.signal
  })
}

export function getFormCategoryOptions(
  scope: FormContentScope,
  organizationPublicId?: string,
  signal?: AbortSignal
) {
  const query = targetQuery(scope, organizationPublicId)
  return apiRequest<FormCategoryOption[]>(`/admin/forms/content-options/categories?${query}`, { signal })
}
