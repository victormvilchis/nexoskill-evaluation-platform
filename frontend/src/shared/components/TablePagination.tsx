import { useMemo } from 'react'
import { PAGE_SIZE_OPTIONS, type PageSize } from '../types/pagination'

type PageToken = number | 'ellipsis-start' | 'ellipsis-end'

type TablePaginationProps = {
  currentPage: number
  pageSize: number
  totalElements: number
  totalPages: number
  onPageChange: (page: number) => void
  onPageSizeChange: (pageSize: PageSize) => void
  isLoading?: boolean
  compact?: boolean
}

function pageTokens(currentPage: number, totalPages: number): PageToken[] {
  if (totalPages <= 7) return Array.from({ length: totalPages }, (_, index) => index)

  const pages = new Set<number>([0, totalPages - 1])
  for (let page = currentPage - 1; page <= currentPage + 1; page += 1) {
    if (page > 0 && page < totalPages - 1) pages.add(page)
  }

  const sorted = [...pages].sort((left, right) => left - right)
  const tokens: PageToken[] = []
  sorted.forEach((page, index) => {
    const previous = sorted[index - 1]
    if (previous !== undefined && page - previous > 1) {
      tokens.push(index === 1 ? 'ellipsis-start' : 'ellipsis-end')
    }
    tokens.push(page)
  })
  return tokens
}

function visibleRange(currentPage: number, pageSize: number, totalElements: number) {
  if (totalElements <= 0) return '0–0 de 0'
  const start = currentPage * pageSize + 1
  const end = Math.min(totalElements, start + pageSize - 1)
  return `${start}–${end} de ${totalElements}`
}

export function TablePagination({
  currentPage,
  pageSize,
  totalElements,
  totalPages,
  onPageChange,
  onPageSizeChange,
  isLoading = false,
  compact = false
}: TablePaginationProps) {
  const safeTotalPages = Math.max(0, totalPages)
  const safeCurrentPage = safeTotalPages === 0
    ? 0
    : Math.min(Math.max(0, currentPage), safeTotalPages - 1)
  const first = safeCurrentPage === 0
  const last = safeTotalPages === 0 || safeCurrentPage === safeTotalPages - 1
  const tokens = useMemo(
    () => pageTokens(safeCurrentPage, safeTotalPages),
    [safeCurrentPage, safeTotalPages]
  )

  return (
    <nav
      className={`ns-table-pagination${compact ? ' ns-table-pagination--compact' : ''}`}
      aria-label="Paginación de la tabla"
      aria-busy={isLoading}
    >
      <p className="ns-table-pagination__summary" aria-live="polite">
        {visibleRange(safeCurrentPage, pageSize, totalElements)}
      </p>

      <div className="ns-table-pagination__controls">
        <button
          type="button"
          className="ns-page-button ns-page-button--first"
          onClick={() => onPageChange(0)}
          disabled={isLoading || first}
          aria-label="Ir a la primera página"
        >
          <span aria-hidden="true">«</span>
        </button>
        <button
          type="button"
          className="ns-page-button"
          onClick={() => onPageChange(Math.max(0, safeCurrentPage - 1))}
          disabled={isLoading || first}
          aria-label="Ir a la página anterior"
        >
          <span aria-hidden="true">‹</span>
        </button>

        <div className="ns-table-pagination__pages" aria-label="Páginas disponibles">
          {tokens.map((token) => {
            if (typeof token !== 'number') {
              return <span className="ns-page-ellipsis" aria-hidden="true" key={token}>…</span>
            }
            const active = token === safeCurrentPage
            return (
              <button
                type="button"
                className={`ns-page-number${active ? ' is-current' : ''}`}
                key={token}
                onClick={() => onPageChange(token)}
                disabled={isLoading || active}
                aria-label={`Ir a la página ${token + 1}`}
                aria-current={active ? 'page' : undefined}
              >
                {token + 1}
              </button>
            )
          })}
        </div>

        <span className="ns-table-pagination__mobile-page" aria-live="polite">
          Página {safeTotalPages === 0 ? 0 : safeCurrentPage + 1} de {safeTotalPages}
        </span>

        <button
          type="button"
          className="ns-page-button"
          onClick={() => onPageChange(Math.min(safeTotalPages - 1, safeCurrentPage + 1))}
          disabled={isLoading || last}
          aria-label="Ir a la página siguiente"
        >
          <span aria-hidden="true">›</span>
        </button>
        <button
          type="button"
          className="ns-page-button ns-page-button--last"
          onClick={() => onPageChange(Math.max(0, safeTotalPages - 1))}
          disabled={isLoading || last}
          aria-label="Ir a la última página"
        >
          <span aria-hidden="true">»</span>
        </button>
      </div>

      <label className="ns-table-pagination__size">
        <select
          value={pageSize}
          onChange={(event) => onPageSizeChange(Number(event.target.value) as PageSize)}
          disabled={isLoading}
          aria-label="Registros por página"
        >
          {PAGE_SIZE_OPTIONS.map((option) => (
            <option key={option} value={option}>{option}</option>
          ))}
        </select>
        <span>por página</span>
      </label>
    </nav>
  )
}
