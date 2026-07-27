export type CatalogStatus = 'ACTIVE' | 'INACTIVE' | 'DELETED'
export type ContentScope = 'GLOBAL' | 'ORGANIZATION'
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

export interface QuestionTechnology {
  publicId: string
  code: string
  name: string
  status: 'ACTIVE' | 'INACTIVE'
  displayOrder: number
}

export interface QuestionCategoryRef {
  publicId: string
  code: string
  name: string
  status: CatalogStatus
}

export interface QuestionCategory extends QuestionCategoryRef {
  description?: string
  contentScope: ContentScope
  ownerOrganizationPublicId?: string
  ownerOrganizationName?: string
  entityVersion: number
  questionCount: number
  createdBy?: number
  createdAt: string
  updatedAt?: string
}

export interface QuestionCategoryDependencies {
  publicId: string
  questionCount: number
  canDelete: boolean
  message: string
}

export interface QuestionCategoryStatusHistory {
  id: number
  previousStatus?: CatalogStatus
  newStatus: CatalogStatus
  reason?: string
  actorUserId?: number
  occurredAt: string
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

export interface QuestionOwnership {
  scope: ContentScope
  organizationPublicId?: string
  organizationCode?: string
  organizationName?: string
  creatorPublicId?: string
  creatorName?: string
  sourceOrganizationPublicId?: string
  sourceOrganizationName?: string
  sourceQuestionPublicId?: string
  sourceQuestionVersion?: number
  clonedToGlobal: boolean
}

export interface QuestionSummary {
  publicId: string
  statement: string
  typeCode: QuestionTypeCode
  typeName: string
  difficultyCode?: string
  difficultyName?: string
  levelCode?: string
  technology?: QuestionTechnology
  categories: QuestionCategoryRef[]
  status: QuestionStatus
  hasMedia: boolean
  hasCode: boolean
  inUse: boolean
  ownership: QuestionOwnership
  forms: QuestionUsageRef[]
  collections: QuestionUsageRef[]
  entityVersion: number
  createdAt: string
  updatedAt?: string
}

export interface QuestionDetail extends QuestionSummary {
  explanation?: string
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
  difficulties: CatalogOption[]
  technologies: QuestionTechnology[]
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
  difficultyCode?: string
  technologyPublicId?: string
  levelCode?: string
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

export interface CloneToGlobalPreview {
  source: {
    publicId: string
    name: string
    scope: ContentScope
    ownerOrganizationName?: string
    version: number
  }
  dependencies: Array<{
    contentType: string
    publicId: string
    name: string
    scope: ContentScope
    globalEquivalentAvailable: boolean
    globalEquivalentPublicId?: string
  }>
  possibleDuplicates: Array<{
    publicId: string
    name: string
    version: number
  }>
  promotable: boolean
  warnings: string[]
}

export interface CloneToGlobalResult {
  promotionPublicId: string
  sourceQuestionPublicId: string
  globalQuestionPublicId: string
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
