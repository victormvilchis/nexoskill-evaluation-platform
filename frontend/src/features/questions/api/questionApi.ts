import { apiRequest } from '../../../shared/api/apiClient'
import type {
  CreateQuestionCategoryPayload,
  CreateQuestionPayload,
  QuestionCatalogs,
  QuestionCategory,
  QuestionDetail,
  QuestionDifficultyCode,
  QuestionPage,
  QuestionStatus,
  QuestionTypeCode
} from '../../../shared/types/questions'

interface SearchQuestionsParams {
  query?: string
  status?: QuestionStatus | ''
  typeCode?: QuestionTypeCode | ''
  difficultyCode?: QuestionDifficultyCode | ''
  categoryPublicId?: string
  page?: number
  size?: number
}

export function getQuestionCatalogs() {
  return apiRequest<QuestionCatalogs>('/admin/question-catalogs')
}

export function createQuestionCategory(payload: CreateQuestionCategoryPayload) {
  return apiRequest<QuestionCategory>('/admin/question-catalogs/categories', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function searchQuestions({
  query = '',
  status = '',
  typeCode = '',
  difficultyCode = '',
  categoryPublicId = '',
  page = 0,
  size = 20
}: SearchQuestionsParams = {}) {
  const parameters = new URLSearchParams({
    page: String(page),
    size: String(size)
  })

  if (query.trim()) parameters.set('query', query.trim())
  if (status) parameters.set('status', status)
  if (typeCode) parameters.set('typeCode', typeCode)
  if (difficultyCode) parameters.set('difficultyCode', difficultyCode)
  if (categoryPublicId) parameters.set('categoryPublicId', categoryPublicId)

  return apiRequest<QuestionPage>(`/admin/questions?${parameters.toString()}`)
}

export function getQuestion(publicId: string) {
  return apiRequest<QuestionDetail>(`/admin/questions/${publicId}`)
}

export function createQuestion(payload: CreateQuestionPayload) {
  return apiRequest<QuestionDetail>('/admin/questions', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}
