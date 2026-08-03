export type FormStatus = 'DRAFT' | 'ACTIVE' | 'DISABLED' | 'CLOSED' | 'ARCHIVED'
export type FormMode = 'ASSESSMENT' | 'PRACTICE'
export type FormContentMode = 'MANUAL' | 'RANDOM_POOL'
export type FormContentScope = 'GLOBAL' | 'ORGANIZATION'

export interface FormQuestionItem {
  questionPublicId: string
  statement: string
  typeCode: string
  typeName: string
  difficultyCode?: string
  difficultyName?: string
  technologyName?: string
  levelCode?: string
  categoryNames: string[]
  status: 'ACTIVE' | 'ARCHIVED' | 'DELETED'
  order: number
  points: number
  required: boolean
}

export interface FormPoolItem {
  publicId?: string
  categoryPublicId: string
  categoryName: string
  questionCount: number
  difficultyCode?: string
  order: number
  availableQuestionCount?: number
}

export interface FormSummary {
  publicId: string
  code: string
  title: string
  status: FormStatus
  modeCode: FormMode
  passingScore: number
  contentScope: FormContentScope
  ownerOrganizationPublicId: string
  ownerOrganizationName: string
  contentMode: FormContentMode
  questionCount: number
  poolCount: number
  startsAt?: string
  endsAt?: string
  version: number
}

export interface FormDetail extends FormSummary {
  description?: string
  maxAttempts?: number
  retryUntilPassed: boolean
  acceptResponses: boolean
  durationMinutes?: number
  showResults: boolean
  showCorrectAnswers: boolean
  randomizeQuestions: boolean
  randomizeOptions: boolean
  showProgress: boolean
  hideQuestionNumbers: boolean
  allowSaveResume: boolean
  oneActiveAttempt: boolean
  thankYouMessage?: string
  questions: FormQuestionItem[]
  pools: FormPoolItem[]
}

export interface FormQuestionPayload {
  questionPublicId: string
  order: number
  points: number
  required: boolean
}

export interface FormPoolPayload {
  publicId?: string
  sourceType: 'CATEGORY'
  sourcePublicId: string
  questionCount: number
  difficultyCode?: string
  order: number
}

export interface FormPayload {
  title: string
  description: string
  modeCode: FormMode
  passingScore: number
  maxAttempts?: number
  retryUntilPassed: boolean
  acceptResponses: boolean
  startsAt?: string
  endsAt?: string
  durationMinutes?: number
  showResults: boolean
  showCorrectAnswers: boolean
  randomizeQuestions: boolean
  randomizeOptions: boolean
  showProgress: boolean
  hideQuestionNumbers: boolean
  allowSaveResume: boolean
  oneActiveAttempt: boolean
  thankYouMessage: string
  version?: number
  contentScope?: FormContentScope
  organizationPublicId?: string
  contentMode: FormContentMode
  operationId?: string
  questions: FormQuestionPayload[]
  pools: FormPoolPayload[]
  sections?: never[]
}

export interface FormQuestionOption {
  publicId: string
  statement: string
  typeCode: string
  typeName: string
  difficultyCode?: string
  difficultyName?: string
  technologyName?: string
  levelCode?: string
  categoryNames: string[]
  contentScope: FormContentScope
  organizationName: string
}

export interface FormQuestionOptionPage {
  content: FormQuestionOption[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface FormCategoryOption {
  publicId: string
  code: string
  name: string
  contentScope: FormContentScope
  organizationName: string
  activeQuestionCount: number
}

export interface FormClonePayload {
  title: string
  targetScope: FormContentScope
  organizationPublicId?: string
  operationId: string
}
