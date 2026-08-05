import type { ApiError } from '../types/auth'

const API_ROOT = '/api/v1'
export const AUTH_INVALID_EVENT = 'nexoskill:auth-invalid'
export const PASSWORD_CHANGE_REQUIRED_EVENT = 'nexoskill:password-change-required'
export const STUDENT_AUTH_INVALID_EVENT = 'nexoskill:student-auth-invalid'

const COLLABORATOR_TERMINOLOGY: ReadonlyArray<readonly [RegExp, string]> = [
  [/\bPersonas estudiantes\b/g, 'Colaboradores'],
  [/\bpersonas estudiantes\b/g, 'colaboradores'],
  [/\bPersona estudiante\b/g, 'Persona colaboradora'],
  [/\bpersona estudiante\b/g, 'persona colaboradora'],
  [/\bEstudiantes\b/g, 'Colaboradores'],
  [/\bestudiantes\b/g, 'colaboradores'],
  [/\bEstudiante\b/g, 'Colaborador'],
  [/\bestudiante\b/g, 'colaborador']
]

function applyCollaboratorTerminology(value: string): string {
  return COLLABORATOR_TERMINOLOGY.reduce(
    (current, [pattern, replacement]) => current.replace(pattern, replacement),
    value
  )
}

function normalizeFieldErrors(fieldErrors?: Record<string, string>): Record<string, string> | undefined {
  if (!fieldErrors) return undefined
  return Object.fromEntries(
    Object.entries(fieldErrors).map(([field, message]) => [field, applyCollaboratorTerminology(message)])
  )
}

export interface AuthInvalidEventDetail {
  code: string
  message: string
}

export class ApiRequestError extends Error {
  readonly code: string
  readonly status: number
  readonly fieldErrors?: Record<string, string>

  constructor(
    message: string,
    code = 'REQUEST_FAILED',
    status = 0,
    fieldErrors?: Record<string, string>
  ) {
    super(message)
    this.name = 'ApiRequestError'
    this.code = code
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

function publishAuthenticationFailure(code: string, message: string) {
  if (
    code === 'ACCESS_EXPIRED' ||
    code === 'SESSION_EXPIRED' ||
    code === 'ACCOUNT_UNAVAILABLE' ||
    code === 'ACCOUNT_INACTIVE' ||
    code === 'ROLE_INACTIVE' ||
    code === 'ACCOUNT_SUSPENDED' ||
    code === 'ORGANIZATION_INACTIVE' ||
    code === 'ORGANIZATION_EXPIRED' ||
    code === 'TEMP_PASSWORD_EXPIRED'
  ) {
    window.dispatchEvent(
      new CustomEvent<AuthInvalidEventDetail>(AUTH_INVALID_EVENT, {
        detail: { code, message }
      })
    )
  }

  if (code === 'PASSWORD_CHANGE_REQUIRED') {
    window.dispatchEvent(
      new CustomEvent<AuthInvalidEventDetail>(PASSWORD_CHANGE_REQUIRED_EVENT, {
        detail: { code, message }
      })
    )
  }

  if (
    code === 'STUDENT_SESSION_EXPIRED' ||
    code === 'STUDENT_ACCESS_EXPIRED' ||
    code === 'STUDENT_ACCOUNT_UNAVAILABLE' ||
    code === 'STUDENT_TEMP_PASSWORD_EXPIRED' ||
    code === 'STUDENT_UNAUTHORIZED'
  ) {
    window.dispatchEvent(
      new CustomEvent<AuthInvalidEventDetail>(STUDENT_AUTH_INVALID_EVENT, {
        detail: { code, message }
      })
    )
  }
}

export async function apiRequest<T>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const isFormData = typeof FormData !== 'undefined' && options.body instanceof FormData
  const response = await fetch(`${API_ROOT}${path}`, {
    ...options,
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      ...(options.body && !isFormData ? { 'Content-Type': 'application/json' } : {}),
      ...options.headers
    }
  })

  if (response.status === 204) {
    return undefined as T
  }

  const contentType = response.headers.get('content-type') ?? ''
  const body = contentType.includes('application/json')
    ? await response.json()
    : null

  if (!response.ok) {
    const error = body as ApiError | null
    const code = error?.code ?? 'REQUEST_FAILED'
    const message = applyCollaboratorTerminology(error?.message ?? 'No fue posible completar la solicitud.')
    publishAuthenticationFailure(code, message)
    throw new ApiRequestError(
      message,
      code,
      response.status,
      normalizeFieldErrors(error?.fieldErrors)
    )
  }

  return body as T
}

function normalizeApiPath(path: string): string {
  const normalizedPath = path.trim()

  if (normalizedPath.startsWith('/api/v1/')) {
    return normalizedPath.substring('/api/v1'.length)
  }

  if (normalizedPath === '/api/v1') {
    return ''
  }

  return normalizedPath.startsWith('/')
    ? normalizedPath
    : `/${normalizedPath}`
}

export const apiClient = {
  get<T>(path: string): Promise<T> {
    return apiRequest<T>(normalizeApiPath(path), {
      method: 'GET'
    })
  },

  post<T>(path: string, body?: unknown): Promise<T> {
    return apiRequest<T>(normalizeApiPath(path), {
      method: 'POST',
      body: body === undefined ? undefined : JSON.stringify(body)
    })
  },

  put<T>(path: string, body?: unknown): Promise<T> {
    return apiRequest<T>(normalizeApiPath(path), {
      method: 'PUT',
      body: body === undefined ? undefined : JSON.stringify(body)
    })
  },

  patch<T>(path: string, body?: unknown): Promise<T> {
    return apiRequest<T>(normalizeApiPath(path), {
      method: 'PATCH',
      body: body === undefined ? undefined : JSON.stringify(body)
    })
  },

  delete<T>(path: string): Promise<T> {
    return apiRequest<T>(normalizeApiPath(path), {
      method: 'DELETE'
    })
  }
}
