import type { ApiError } from '../types/auth'

const API_ROOT = '/api/v1'
export const AUTH_INVALID_EVENT = 'nexoskill:auth-invalid'
export const PASSWORD_CHANGE_REQUIRED_EVENT = 'nexoskill:password-change-required'
export const STUDENT_AUTH_INVALID_EVENT = 'nexoskill:student-auth-invalid'
export const PLATFORM_REQUEST_FAILURE_EVENT = 'nexoskill:platform-request-failure'
export const ORGANIZATION_CONTEXT_KEY = 'nexoskill:organization-context'
export const ORGANIZATION_CONTEXT_CHANGED_EVENT = 'nexoskill:organization-context-changed'

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

export interface PlatformRequestFailureDetail {
  blocking: boolean
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

export function isPlatformRequestFailure(error: unknown): error is ApiRequestError {
  return error instanceof ApiRequestError && (error.status === 0 || error.status >= 500)
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


function publishPlatformRequestFailure(method: string, message: string) {
  window.dispatchEvent(
    new CustomEvent<PlatformRequestFailureDetail>(PLATFORM_REQUEST_FAILURE_EVENT, {
      detail: {
        blocking: method === 'GET' || method === 'HEAD',
        message
      }
    })
  )
}

export async function apiRequest<T>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const isFormData = typeof FormData !== 'undefined' && options.body instanceof FormData
  const method = (options.method ?? 'GET').toUpperCase()
  let response: Response
  try {
    response = await fetch(`${API_ROOT}${path}`, {
      ...options,
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        ...(window.localStorage.getItem(ORGANIZATION_CONTEXT_KEY)
          ? { 'X-Organization-Context': window.localStorage.getItem(ORGANIZATION_CONTEXT_KEY)! }
          : {}),
        ...(options.body && !isFormData ? { 'Content-Type': 'application/json' } : {}),
        ...options.headers
      }
    })
  } catch (requestError) {
    if (requestError instanceof DOMException && requestError.name === 'AbortError') throw requestError
    const message = method === 'GET' || method === 'HEAD'
      ? 'La información del módulo no pudo cargarse. Verifica tu conexión e intenta nuevamente.'
      : 'No fue posible completar la operación. Intenta nuevamente.'
    publishPlatformRequestFailure(method, message)
    throw new ApiRequestError(message, 'PLATFORM_UNAVAILABLE', 0)
  }

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
    if (response.status >= 500) {
      const platformMessage = method === 'GET' || method === 'HEAD'
        ? 'La información del módulo no pudo cargarse. Verifica tu conexión e intenta nuevamente.'
        : 'No fue posible completar la operación. Intenta nuevamente.'
      publishPlatformRequestFailure(method, platformMessage)
    }
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
