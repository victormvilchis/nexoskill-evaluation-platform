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
  collaborator: string
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
  conflicts: StudentImportIssue[]
  errors: StudentImportIssue[]
  notice: string
}

export interface StudentImportApplyCommand {
  token: string
  newStudents: Array<{ rowKey: string; email: string; selected: boolean }>
  changedStudents: Array<{ studentPublicId: string; fields: string[] }>
  possibleLows: Array<{ studentPublicId: string; action: 'KEEP' | 'DEACTIVATE' | 'IGNORE' }>
}

export interface StudentImportCredential {
  organization: string
  organizationCode: string
  collaborator: string
  email: string
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
