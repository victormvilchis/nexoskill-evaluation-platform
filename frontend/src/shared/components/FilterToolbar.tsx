import type { ReactNode } from 'react'

type FilterToolbarProps = {
  children: ReactNode
  resultLabel?: string
  onClear?: () => void
  hasActiveFilters?: boolean
}

export function FilterToolbar({ children, resultLabel, onClear, hasActiveFilters }: FilterToolbarProps) {
  return (
    <section className="ns-filter-toolbar" aria-label="Filtros">
      <div className="ns-filter-fields">{children}</div>
      <div className="ns-filter-meta">
        {resultLabel && <span className="ns-result-count">{resultLabel}</span>}
        {hasActiveFilters && onClear && (
          <button className="ns-clear-filters" type="button" onClick={onClear}>Limpiar filtros</button>
        )}
      </div>
    </section>
  )
}
