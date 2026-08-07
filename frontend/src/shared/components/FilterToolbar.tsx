import type { ReactNode } from 'react'

type FilterToolbarProps = {
  children: ReactNode
  onClear?: () => void
  hasActiveFilters?: boolean
  /** Compatibilidad: el total se muestra exclusivamente en TablePagination. */
  resultLabel?: string
}

export function FilterToolbar({ children }: FilterToolbarProps) {
  return (
    <section className="ns-filter-toolbar" aria-label="Filtros">
      <div className="ns-filter-fields">{children}</div>
    </section>
  )
}
