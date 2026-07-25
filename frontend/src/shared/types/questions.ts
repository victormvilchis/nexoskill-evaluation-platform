export type QuestionStatus =
  | 'DRAFT'
  | 'UNDER_REVIEW'
  | 'APPROVED'
  | 'PUBLISHED'
  | 'ARCHIVED'

export type QuestionTypeCode =
  | 'SINGLE_CHOICE'
  | 'MULTIPLE_CHOICE'
  | 'TRUE_FALSE'

export type QuestionDifficultyCode =
  | 'BASIC'
  | 'INTERMEDIATE'
  | 'ADVANCED'

export interface CatalogOption {
  code: string
  name: string
  description: string | null
}

export interface QuestionCategory {
  publicId: string
  code: string
  name: string
  description: string | null
  status: 'ACTIVE' | 'INACTIVE'
  createdAt: string
}

export interface QuestionCatalogs {
  types: CatalogOption[]
  difficulties: CatalogOption[]
  categories: QuestionCategory[]
}

export interface QuestionOption {
  publicId: string
  order: number
  text: string
  correct: boolean
}

export interface QuestionSummary {
  publicId: string
  statement: string
  typeCode: QuestionTypeCode
  typeName: string
  difficultyCode: QuestionDifficultyCode
  difficultyName: string
  categoryPublicId: string
  categoryName: string
  status: QuestionStatus
  versionNumber: number
  createdAt: string
  updatedAt: string | null
}

export interface QuestionDetail extends QuestionSummary {
  explanation: string | null
  entityVersion: number
  publishedVersionNumber: number | null
  options: QuestionOption[]
}

export interface QuestionVersionSummary {
  versionNumber: number
  status: QuestionStatus
  statement: string
  changeSummary: string | null
  createdAt: string
  statusChangedAt: string | null
  publishedAt: string | null
}

export interface QuestionHistory {
  questionPublicId: string
  versions: QuestionVersionSummary[]
}

export interface QuestionPage {
  content: QuestionSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface CreateQuestionOptionPayload {
  text: string
  correct: boolean
}

export interface CreateQuestionPayload {
  typeCode: QuestionTypeCode
  difficultyCode: QuestionDifficultyCode
  categoryPublicId: string
  statement: string
  explanation?: string
  options: CreateQuestionOptionPayload[]
}

export interface UpdateQuestionPayload extends CreateQuestionPayload {
  changeSummary?: string
  expectedEntityVersion: number
}

export interface TransitionQuestionPayload {
  targetStatus: QuestionStatus
  expectedEntityVersion: number
}

export interface CreateQuestionCategoryPayload {
  code?: string
  name: string
  description?: string
}
