import type { ReactNode } from 'react'

type FilterToolbarProps = {
  children: ReactNode
  onClear?: () => void
  hasActiveFilters?: boolean
  /** Compatibilidad: el total se muestra exclusivamente en TablePagination. */
  resultLabel?: string
}

export function FilterToolbar({ children, onClear, hasActiveFilters }: FilterToolbarProps) {
  return (
    <section className="ns-filter-toolbar" aria-label="Filtros">
      <div className="ns-filter-fields">{children}</div>
      <div className="ns-filter-meta">
        {hasActiveFilters && onClear && (
          <button className="ns-clear-filters" type="button" onClick={onClear}>Limpiar filtros</button>
        )}
      </div>
    </section>
  )
}
