import { apiRequest } from '../../../shared/api/apiClient'
import type {
  CollectionDetail,
  CollectionPage,
  CollectionPayload,
  CollectionStatus,
  CatalogStatus,
  CloneToGlobalPreview,
  CloneToGlobalResult,
  ContentScope,
  CreateQuestionCategoryPayload,
  QuestionCatalogs,
  QuestionCategory,
  QuestionCategoryDependencies,
  QuestionCategoryStatusHistory,
  QuestionDetail,
  QuestionMedia,
  QuestionPage,
  QuestionPayload,
  QuestionStatus,
  QuestionTag,
  UpdateCollectionPayload,
  UpdateQuestionCategoryPayload,
  UpdateQuestionPayload
} from '../../../shared/types/questions'

export function getQuestionCatalogs(signal?: AbortSignal) {
  return apiRequest<QuestionCatalogs>('/admin/question-catalogs', { signal })
}

export function getQuestionCategoryOptions(questionPublicId?: string, signal?: AbortSignal) {
  const query = new URLSearchParams()
  if (questionPublicId) query.set('questionPublicId', questionPublicId)
  const suffix = questionPublicId ? `?${query.toString()}` : ''
  return apiRequest<QuestionCategory[]>(`/admin/question-catalogs/question-options${suffix}`, { signal })
}

export function getQuestionCategories(
  signal?: AbortSignal,
  status: CatalogStatus | 'ALL' = 'ACTIVE'
) {
  const query = new URLSearchParams({ status })
  return apiRequest<QuestionCategory[]>(`/admin/question-catalogs/categories?${query}`, { signal })
}

export function getQuestionCategory(id: string, signal?: AbortSignal) {
  return apiRequest<QuestionCategory>(`/admin/question-catalogs/categories/${id}`, { signal })
}

export function getQuestionCategoryDependencies(id: string, signal?: AbortSignal) {
  return apiRequest<QuestionCategoryDependencies>(
    `/admin/question-catalogs/categories/${id}/dependencies`,
    { signal }
  )
}

export function getQuestionCategoryStatusHistory(id: string, signal?: AbortSignal) {
  return apiRequest<QuestionCategoryStatusHistory[]>(
    `/admin/question-catalogs/categories/${id}/status-history`,
    { signal }
  )
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
  expectedEntityVersion: number,
  reason?: string
) {
  return apiRequest<QuestionCategory>(
    `/admin/question-catalogs/categories/${id}/${status === 'ACTIVE' ? 'activate' : 'deactivate'}`,
    {
      method: 'POST',
      body: JSON.stringify({ expectedEntityVersion, reason })
    }
  )
}

export function deleteQuestionCategory(
  id: string,
  expectedEntityVersion: number,
  reason?: string
) {
  return apiRequest<QuestionCategory>(`/admin/question-catalogs/categories/${id}`, {
    method: 'DELETE',
    body: JSON.stringify({ expectedEntityVersion, reason })
  })
}

export interface QuestionSearchParams {
  query?: string
  status?: QuestionStatus | ''
  typeCode?: string
  categoryPublicId?: string
  scope?: ContentScope | ''
  organizationPublicId?: string
  technologyPublicId?: string
  difficultyCode?: string
  levelCode?: string
  creatorPublicId?: string
  createdYear?: number
  createdFrom?: string
  createdTo?: string
  updatedFrom?: string
  updatedTo?: string
  clonedToGlobal?: boolean | ''
  inUse?: boolean | ''
  page?: number
  size?: number
  signal?: AbortSignal
}

export function searchQuestions(params: QuestionSearchParams = {}) {
  const query = new URLSearchParams({
    page: String(params.page ?? 0),
    size: String(params.size ?? 10)
  })
  if (params.query?.trim()) query.set('query', params.query.trim())
  if (params.status === '') query.set('status', 'ALL')
  else if (params.status) query.set('status', params.status)
  if (params.typeCode) query.set('typeCode', params.typeCode)
  if (params.categoryPublicId) query.set('categoryPublicId', params.categoryPublicId)
  if (params.scope) query.set('scope', params.scope)
  if (params.organizationPublicId) query.set('organizationPublicId', params.organizationPublicId)
  if (params.technologyPublicId) query.set('technologyPublicId', params.technologyPublicId)
  if (params.difficultyCode) query.set('difficultyCode', params.difficultyCode)
  if (params.levelCode) query.set('levelCode', params.levelCode)
  if (params.creatorPublicId) query.set('creatorPublicId', params.creatorPublicId)
  if (params.createdYear) query.set('createdYear', String(params.createdYear))
  if (params.createdFrom) query.set('createdFrom', params.createdFrom)
  if (params.createdTo) query.set('createdTo', params.createdTo)
  if (params.updatedFrom) query.set('updatedFrom', params.updatedFrom)
  if (params.updatedTo) query.set('updatedTo', params.updatedTo)
  if (typeof params.clonedToGlobal === 'boolean') query.set('clonedToGlobal', String(params.clonedToGlobal))
  if (typeof params.inUse === 'boolean') query.set('inUse', String(params.inUse))

  return apiRequest<QuestionPage>(`/admin/questions?${query}`, {
    signal: params.signal
  })
}

export function getQuestionCreationYears(signal?: AbortSignal) {
  return apiRequest<number[]>('/admin/questions/filter-options/years', { signal })
}

export function suggestQuestionTags(queryValue: string, signal?: AbortSignal) {
  const query = new URLSearchParams({
    query: queryValue.trim(),
    limit: '8'
  })
  return apiRequest<QuestionTag[]>(`/admin/question-tags/suggestions?${query}`, { signal })
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

export function getQuestionClonePreview(id: string, signal?: AbortSignal) {
  return apiRequest<CloneToGlobalPreview>(`/admin/questions/${id}/clone-to-global/preview`, { signal })
}

export function cloneQuestionToGlobal(id: string, payload: { includeDependencies: boolean; notes?: string }) {
  return apiRequest<CloneToGlobalResult>(`/admin/questions/${id}/clone-to-global`, {
    method: 'POST',
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
    size: String(params.size ?? 10)
  })
  if (params.query) query.set('query', params.query)
  if (params.status === '') query.set('status', 'ALL')
  else if (params.status) query.set('status', params.status)

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
