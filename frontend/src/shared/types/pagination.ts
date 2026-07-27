export type SortDirection = 'ASC' | 'DESC'

export type PagedSort = {
  property: string
  direction: SortDirection
}

export type PagedResponse<T> = {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first?: boolean
  last?: boolean
  hasNext?: boolean
  hasPrevious?: boolean
  sort?: PagedSort
}

export const PAGE_SIZE_OPTIONS = [10, 25, 50, 100] as const
export type PageSize = (typeof PAGE_SIZE_OPTIONS)[number]

export function parsePage(value: string | null): number {
  if (!value) return 0
  const parsed = Number.parseInt(value, 10)
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : 0
}

export function parsePageSize(value: string | null): PageSize {
  if (!value) return 10
  const parsed = Number.parseInt(value, 10)
  return PAGE_SIZE_OPTIONS.includes(parsed as PageSize) ? parsed as PageSize : 10
}

export function normalizePagedResponse<T>(response: PagedResponse<T>): PagedResponse<T> {
  const totalElements = Math.max(0, response.totalElements ?? 0)
  const size = parsePageSize(String(response.size ?? 10))
  const totalPages = totalElements === 0
    ? 0
    : Math.max(1, response.totalPages ?? Math.ceil(totalElements / size))
  const page = totalPages === 0
    ? 0
    : Math.min(Math.max(0, response.page ?? 0), totalPages - 1)

  return {
    ...response,
    content: Array.isArray(response.content) ? response.content : [],
    page,
    size,
    totalElements,
    totalPages,
    first: page === 0,
    last: totalPages === 0 || page >= totalPages - 1,
    hasPrevious: page > 0,
    hasNext: totalPages > 0 && page < totalPages - 1
  }
}
