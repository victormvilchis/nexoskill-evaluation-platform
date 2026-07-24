import type { ApiError } from '../types/auth'

const API_ROOT = '/api/v1'

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

export async function apiRequest<T>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const response = await fetch(`${API_ROOT}${path}`, {
    ...options,
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
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
    throw new ApiRequestError(
      error?.message ?? 'No fue posible completar la solicitud.',
      error?.code,
      response.status,
      error?.fieldErrors
    )
  }

  return body as T
}
