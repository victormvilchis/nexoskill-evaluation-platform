import type { ApiError } from '../types/auth'

const API_ROOT = '/api/v1'
export const AUTH_INVALID_EVENT = 'nexoskill:auth-invalid'
export const PASSWORD_CHANGE_REQUIRED_EVENT = 'nexoskill:password-change-required'

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
    const message = error?.message ?? 'No fue posible completar la solicitud.'
    publishAuthenticationFailure(code, message)
    throw new ApiRequestError(
      message,
      code,
      response.status,
      error?.fieldErrors
    )
  }

  return body as T
}
