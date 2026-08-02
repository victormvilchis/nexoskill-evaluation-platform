export interface StudentImportIssue {
  row: number
  code: string
  message: string
}

export interface StudentImportFieldChange {
  key: string
  field: string
  currentValue: string
  excelValue: string
  selected: boolean
}

export interface NewStudentPreview {
  rowKey: string
  row: number
  collaborator: string | null
  profile: string | null
  primaryTechnology: string | null
  suggestedEmail: string | null
  warnings: string[]
}

export interface ChangedStudentPreview {
  studentPublicId: string
  rowKey: string
  collaborator: string
  changes: StudentImportFieldChange[]
  warnings: string[]
}

export interface PossibleLowPreview {
  studentPublicId: string
  collaborator: string
  email: string
  action: 'KEEP'
}

export type StudentImportConflictActionValue = 'USE_PLATFORM' | 'USE_EXCEL' | 'OMIT_ROW'

export interface StudentImportConflictAction {
  value: StudentImportConflictActionValue
  label: string
  description: string
}

export interface StudentImportConflict {
  id: string
  rowKey: string
  row: number
  collaborator: string
  code: string
  groupKey: string
  title: string
  field: string
  certificationType: string | null
  certification: string | null
  excelValue: string
  currentValue: string
  calculatedValue: string
  reason: string
  actions: StudentImportConflictAction[]
  resolvedAction: StudentImportConflictActionValue | null
  reusedDecision: boolean
}

export interface StudentImportPreview {
  token: string
  fileName: string
  sheetName: string
  organizationName: string
  organizationCode: string
  totalRows: number
  newStudents: NewStudentPreview[]
  changedStudents: ChangedStudentPreview[]
  possibleLows: PossibleLowPreview[]
  conflicts: StudentImportConflict[]
  warnings: StudentImportIssue[]
  errors: StudentImportIssue[]
  notice: string
}

export interface StudentImportApplyCommand {
  token: string
  newStudents: Array<{ rowKey: string; email: string; selected: boolean }>
  changedStudents: Array<{ studentPublicId: string; fields: string[] }>
  possibleLows: Array<{ studentPublicId: string; action: 'KEEP' | 'DEACTIVATE' | 'IGNORE' }>
  conflicts: Array<{ conflictId: string; action: StudentImportConflictActionValue }>
}

export interface StudentImportCredential {
  organization: string
  organizationCode: string
  collaborator: string
  email: string
  studentCode: string
  temporaryPassword: string
}

export interface StudentImportApplyResult {
  created: number
  updated: number
  possibleLowsProcessed: number
  errors: StudentImportIssue[]
  credentials: StudentImportCredential[]
  credentialsNotice: string | null
}

export type ExperienceLevel = 'JR' | 'STD' | 'SR' | ''

export interface StudentExperienceItem {
  publicId?: string
  name: string
  level: ExperienceLevel | null
  displayOrder?: number
}

export interface StudentExperiencePayload {
  currentTechnologies: StudentExperienceItem[]
  languages: StudentExperienceItem[]
  knownTechnologies: StudentExperienceItem[]
}
