import { Icon } from './Icon'

type SortDirection = 'ASC' | 'DESC'

type SortIndicatorProps = {
  active: boolean
  direction: SortDirection
}

export function SortIndicator({ active, direction }: SortIndicatorProps) {
  return (
    <span className="ns-sort-indicator" aria-hidden="true">
      <Icon name={active ? (direction === 'ASC' ? 'sortAsc' : 'sortDesc') : 'sort'} size={16} />
    </span>
  )
}
