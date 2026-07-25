import { apiRequest } from '../../../shared/api/apiClient'
import type {
  CollectionDetail,
  CollectionPage,
  CollectionPayload,
  CollectionStatus,
  CreateQuestionCategoryPayload,
  QuestionCatalogs,
  QuestionCategory,
  QuestionDetail,
  QuestionMedia,
  QuestionPage,
  QuestionPayload,
  QuestionStatus,
  UpdateCollectionPayload,
  UpdateQuestionCategoryPayload,
  UpdateQuestionPayload
} from '../../../shared/types/questions'

export function getQuestionCatalogs(signal?: AbortSignal) {
  return apiRequest<QuestionCatalogs>('/admin/question-catalogs', { signal })
}

export function getQuestionCategories(signal?: AbortSignal) {
  return apiRequest<QuestionCategory[]>('/admin/question-catalogs/categories', { signal })
}

export function createQuestionCategory(payload: CreateQuestionCategoryPayload) {
  return apiRequest<QuestionCategory>('/admin/question-catalogs/categories', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function updateQuestionCategory(
  id: string,
  payload: UpdateQuestionCategoryPayload
) {
  return apiRequest<QuestionCategory>(`/admin/question-catalogs/categories/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}

export function changeQuestionCategoryStatus(
  id: string,
  status: 'ACTIVE' | 'INACTIVE',
  expectedEntityVersion: number
) {
  return apiRequest<QuestionCategory>(
    `/admin/question-catalogs/categories/${id}/${status === 'ACTIVE' ? 'activate' : 'deactivate'}`,
    {
      method: 'POST',
      body: JSON.stringify({ expectedEntityVersion })
    }
  )
}

export interface QuestionSearchParams {
  query?: string
  status?: QuestionStatus | ''
  typeCode?: string
  categoryPublicId?: string
  page?: number
  size?: number
  signal?: AbortSignal
}

export function searchQuestions(params: QuestionSearchParams = {}) {
  const query = new URLSearchParams({
    page: String(params.page ?? 0),
    size: String(params.size ?? 20)
  })
  if (params.query) query.set('query', params.query)
  if (params.status) query.set('status', params.status)
  if (params.typeCode) query.set('typeCode', params.typeCode)
  if (params.categoryPublicId) {
    query.set('categoryPublicId', params.categoryPublicId)
  }

  return apiRequest<QuestionPage>(`/admin/questions?${query}`, {
    signal: params.signal
  })
}

export function getQuestion(id: string, signal?: AbortSignal) {
  return apiRequest<QuestionDetail>(`/admin/questions/${id}`, { signal })
}

export function createQuestion(payload: QuestionPayload) {
  return apiRequest<QuestionDetail>('/admin/questions', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function updateQuestion(id: string, payload: UpdateQuestionPayload) {
  return apiRequest<QuestionDetail>(`/admin/questions/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}

export function duplicateQuestion(id: string) {
  return apiRequest<QuestionDetail>(`/admin/questions/${id}/duplicate`, {
    method: 'POST'
  })
}

export function changeQuestionStatus(
  id: string,
  status: Exclude<QuestionStatus, 'DELETED'>,
  expectedEntityVersion: number
) {
  return apiRequest<QuestionDetail>(
    `/admin/questions/${id}/${status === 'ACTIVE' ? 'activate' : 'archive'}`,
    {
      method: 'POST',
      body: JSON.stringify({ status, expectedEntityVersion })
    }
  )
}


export function deleteQuestion(
  id: string,
  expectedEntityVersion: number,
  reason?: string
) {
  return apiRequest<QuestionDetail>(`/admin/questions/${id}/delete`, {
    method: 'POST',
    body: JSON.stringify({ expectedEntityVersion, reason })
  })
}

export function restoreQuestion(id: string, expectedEntityVersion: number) {
  return apiRequest<QuestionDetail>(`/admin/questions/${id}/restore`, {
    method: 'POST',
    body: JSON.stringify({ expectedEntityVersion })
  })
}

export function uploadQuestionMedia(file: File) {
  const body = new FormData()
  body.append('file', file)
  return apiRequest<QuestionMedia>('/question-media', {
    method: 'POST',
    body
  })
}

export interface CollectionSearchParams {
  query?: string
  status?: CollectionStatus | ''
  page?: number
  size?: number
  signal?: AbortSignal
}

export function searchCollections(params: CollectionSearchParams = {}) {
  const query = new URLSearchParams({
    page: String(params.page ?? 0),
    size: String(params.size ?? 20)
  })
  if (params.query) query.set('query', params.query)
  if (params.status) query.set('status', params.status)

  return apiRequest<CollectionPage>(`/admin/question-collections?${query}`, {
    signal: params.signal
  })
}

export function getCollection(id: string, signal?: AbortSignal) {
  return apiRequest<CollectionDetail>(`/admin/question-collections/${id}`, {
    signal
  })
}

export function createCollection(payload: CollectionPayload) {
  return apiRequest<CollectionDetail>('/admin/question-collections', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function updateCollection(id: string, payload: UpdateCollectionPayload) {
  return apiRequest<CollectionDetail>(`/admin/question-collections/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}

export function changeCollectionStatus(
  id: string,
  status: CollectionStatus,
  expectedEntityVersion: number
) {
  return apiRequest<CollectionDetail>(
    `/admin/question-collections/${id}/${status === 'ACTIVE' ? 'activate' : 'deactivate'}`,
    {
      method: 'POST',
      body: JSON.stringify({ expectedEntityVersion })
    }
  )
}
