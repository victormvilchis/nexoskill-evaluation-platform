export type CatalogStatus = 'ACTIVE' | 'INACTIVE'
export type QuestionStatus = 'ACTIVE' | 'ARCHIVED' | 'DELETED'
export type QuestionTypeCode =
  | 'SINGLE_CHOICE'
  | 'MULTIPLE_CHOICE'
  | 'TRUE_FALSE'
  | 'MATCHING'
  | 'OPEN_TEXT'

export interface CatalogOption {
  code: string
  name: string
  description?: string
}

export interface QuestionCategoryRef {
  publicId: string
  code: string
  name: string
  status: CatalogStatus
}

export interface QuestionCategory extends QuestionCategoryRef {
  description?: string
  entityVersion: number
  questionCount: number
  createdAt: string
  updatedAt?: string
}

export interface QuestionMedia {
  publicId: string
  originalName: string
  contentType: string
  size: number
  url: string
}

export interface QuestionUsageRef {
  publicId: string
  name: string
}

export interface QuestionOption {
  publicId: string
  order: number
  text?: string
  media?: QuestionMedia
  matchText?: string
  matchMedia?: QuestionMedia
  correct: boolean
  feedback?: string
}

export interface QuestionAnswerSettings {
  acceptedAnswers: string[]
  caseSensitive: boolean
  manualReview: boolean
  numericMin?: number
  numericMax?: number
  numericTolerance?: number
  maxLength?: number
}

export interface QuestionSummary {
  publicId: string
  statement: string
  typeCode: QuestionTypeCode
  typeName: string
  categories: QuestionCategoryRef[]
  status: QuestionStatus
  hasMedia: boolean
  hasCode: boolean
  forms: QuestionUsageRef[]
  collections: QuestionUsageRef[]
  entityVersion: number
  createdAt: string
  updatedAt?: string
}

export interface QuestionDetail extends QuestionSummary {
  explanation?: string
  entityVersion: number
  promptMedia?: QuestionMedia
  codeLanguage?: 'JAVA'
  codeContent?: string
  answerSettings: QuestionAnswerSettings
  options: QuestionOption[]
}

export interface QuestionPage {
  content: QuestionSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface QuestionCatalogs {
  types: CatalogOption[]
  categories: QuestionCategory[]
}

export interface QuestionOptionPayload {
  text?: string
  mediaPublicId?: string
  matchText?: string
  matchMediaPublicId?: string
  correct: boolean
  feedback?: string
}

export interface QuestionPayload {
  typeCode: QuestionTypeCode
  categoryPublicIds: string[]
  statement: string
  explanation?: string
  promptMediaPublicId?: string
  codeContent?: string
  answerSettings: QuestionAnswerSettings
  options: QuestionOptionPayload[]
}

export interface UpdateQuestionPayload extends QuestionPayload {
  expectedEntityVersion: number
}

export interface CreateQuestionCategoryPayload {
  code?: string
  name: string
  description?: string
}

export interface UpdateQuestionCategoryPayload extends CreateQuestionCategoryPayload {
  expectedEntityVersion: number
}

export type CollectionStatus = 'ACTIVE' | 'INACTIVE'
export interface CollectionSummary {
  publicId: string
  name: string
  description?: string
  status: CollectionStatus
  categoryCount: number
  explicitQuestionCount: number
  effectiveQuestionCount: number
  entityVersion: number
  createdAt: string
  updatedAt?: string
}
export interface CollectionDetail {
  publicId: string
  name: string
  description?: string
  status: CollectionStatus
  categories: QuestionCategoryRef[]
  explicitQuestions: QuestionSummary[]
  effectiveQuestions: QuestionSummary[]
  entityVersion: number
  createdAt: string
  updatedAt?: string
}
export interface CollectionPage {
  content: CollectionSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
export interface CollectionPayload {
  name: string
  description?: string
  categoryPublicIds: string[]
  questionPublicIds: string[]
}
export interface UpdateCollectionPayload extends CollectionPayload {
  expectedEntityVersion: number
}
