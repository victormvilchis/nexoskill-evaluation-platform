import { useEffect, useMemo } from 'react'
import type { PageSize } from '../types/pagination'

export function useClientPagination<T>(items: readonly T[], page: number, pageSize: PageSize) {
  const totalElements = items.length
  const totalPages = totalElements === 0 ? 0 : Math.ceil(totalElements / pageSize)
  const safePage = totalPages === 0 ? 0 : Math.min(Math.max(0, page), totalPages - 1)

  const content = useMemo(() => {
    const start = safePage * pageSize
    return items.slice(start, start + pageSize)
  }, [items, safePage, pageSize])

  return { content, page: safePage, size: pageSize, totalElements, totalPages }
}

export function useCorrectInvalidPage(page: number, totalPages: number, onPageChange: (page: number) => void) {
  useEffect(() => {
    if (totalPages === 0 && page !== 0) onPageChange(0)
    else if (totalPages > 0 && page >= totalPages) onPageChange(totalPages - 1)
  }, [page, totalPages, onPageChange])
}
