import { useEffect, useId, useMemo, useRef, useState, type CSSProperties } from 'react'
import { createPortal } from 'react-dom'
import { Icon } from './Icon'

interface DateFieldProps {
  value: string
  onChange: (value: string) => void
  id?: string
  name?: string
  min?: string
  max?: string
  disabled?: boolean
  required?: boolean
  ariaInvalid?: boolean
  ariaLabel?: string
  className?: string
  allowClear?: boolean
}

interface DateTimeFieldProps extends Omit<DateFieldProps, 'value' | 'onChange' | 'allowClear'> {
  value: string
  onChange: (value: string) => void
}

type CalendarView = 'DAYS' | 'MONTHS' | 'YEARS'

const MONTH_NAMES = Array.from({ length: 12 }, (_, month) => (
  new Intl.DateTimeFormat('es-MX', { month: 'long' }).format(new Date(2024, month, 1))
))
const DATE_FORMAT = new Intl.DateTimeFormat('es-MX', { day: '2-digit', month: 'short', year: 'numeric' })
const WEEKDAYS = ['L', 'M', 'M', 'J', 'V', 'S', 'D']

function parseIso(value?: string) {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return undefined
  const parts = value.split('-').map(Number)
  const year = parts[0]
  const month = parts[1]
  const day = parts[2]
  if (year === undefined || month === undefined || day === undefined) return undefined
  const parsed = new Date(year, month - 1, day)
  if (Number.isNaN(parsed.getTime()) || parsed.getFullYear() !== year || parsed.getMonth() !== month - 1 || parsed.getDate() !== day) {
    return undefined
  }
  return parsed
}

function toIso(value: Date) {
  const year = String(value.getFullYear()).padStart(4, '0')
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function sameDay(first: Date, second: Date) {
  return first.getFullYear() === second.getFullYear()
    && first.getMonth() === second.getMonth()
    && first.getDate() === second.getDate()
}

function monthStart(value: Date) {
  return new Date(value.getFullYear(), value.getMonth(), 1)
}

function addMonths(value: Date, amount: number) {
  return new Date(value.getFullYear(), value.getMonth() + amount, 1)
}

function addYears(value: Date, amount: number) {
  return new Date(value.getFullYear() + amount, value.getMonth(), 1)
}

function calendarDays(month: Date) {
  const first = monthStart(month)
  const mondayIndex = (first.getDay() + 6) % 7
  const gridStart = new Date(first.getFullYear(), first.getMonth(), 1 - mondayIndex)
  return Array.from({ length: 42 }, (_, index) => new Date(
    gridStart.getFullYear(), gridStart.getMonth(), gridStart.getDate() + index
  ))
}

function isOutsideRange(value: string, min?: string, max?: string) {
  return Boolean((min && value < min) || (max && value > max))
}

function monthOutsideRange(year: number, month: number, min?: string, max?: string) {
  const first = `${String(year).padStart(4, '0')}-${String(month + 1).padStart(2, '0')}-01`
  const lastDay = new Date(year, month + 1, 0).getDate()
  const last = `${String(year).padStart(4, '0')}-${String(month + 1).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`
  return Boolean((min && last < min) || (max && first > max))
}

function yearOutsideRange(year: number, min?: string, max?: string) {
  const first = `${String(year).padStart(4, '0')}-01-01`
  const last = `${String(year).padStart(4, '0')}-12-31`
  return Boolean((min && last < min) || (max && first > max))
}

function capitalize(value: string) {
  return value.charAt(0).toUpperCase() + value.slice(1)
}

export function DateField({
  value,
  onChange,
  id,
  name,
  min,
  max,
  disabled = false,
  required = false,
  ariaInvalid = false,
  ariaLabel = 'Seleccionar fecha',
  className = '',
  allowClear = true
}: DateFieldProps) {
  const generatedId = useId()
  const controlId = id ?? `vt-date-${generatedId.replace(/:/g, '')}`
  const triggerRef = useRef<HTMLButtonElement>(null)
  const popoverRef = useRef<HTMLDivElement>(null)
  const selected = useMemo(() => parseIso(value), [value])
  const today = useMemo(() => new Date(), [])
  const [open, setOpen] = useState(false)
  const [view, setView] = useState<CalendarView>('DAYS')
  const [visibleMonth, setVisibleMonth] = useState(() => monthStart(selected ?? today))
  const [yearPageStart, setYearPageStart] = useState(() => Math.floor((selected ?? today).getFullYear() / 12) * 12)
  const [position, setPosition] = useState<CSSProperties>({})

  useEffect(() => {
    if (!selected) return
    setVisibleMonth(monthStart(selected))
    setYearPageStart(Math.floor(selected.getFullYear() / 12) * 12)
  }, [selected])

  useEffect(() => {
    if (!open) return
    setView('DAYS')
    setVisibleMonth(monthStart(selected ?? today))
    setYearPageStart(Math.floor((selected ?? today).getFullYear() / 12) * 12)
  }, [open, selected, today])

  useEffect(() => {
    if (!open) return
    const updatePosition = () => {
      const trigger = triggerRef.current
      if (!trigger) return
      const rect = trigger.getBoundingClientRect()
      const viewportPadding = 12
      const width = Math.min(360, window.innerWidth - viewportPadding * 2)
      const left = Math.max(viewportPadding, Math.min(rect.left, window.innerWidth - width - viewportPadding))
      const estimatedHeight = 430
      const below = window.innerHeight - rect.bottom - viewportPadding
      const above = rect.top - viewportPadding
      const openAbove = below < 300 && above > below
      const viewportHeight = Math.max(120, window.innerHeight - viewportPadding * 2)
      const availableSpace = openAbove ? above - 8 : below - 8
      const availableHeight = Math.min(estimatedHeight, viewportHeight, Math.max(120, availableSpace))
      const top = openAbove
        ? Math.max(viewportPadding, rect.top - availableHeight - 8)
        : Math.max(viewportPadding, Math.min(rect.bottom + 8, window.innerHeight - viewportPadding - availableHeight))
      setPosition({ width, left, top, maxHeight: availableHeight })
    }
    updatePosition()
    const handlePointer = (event: PointerEvent) => {
      const target = event.target as Node
      if (triggerRef.current?.contains(target) || popoverRef.current?.contains(target)) return
      setOpen(false)
    }
    const handleKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        setOpen(false)
        triggerRef.current?.focus()
      }
    }
    window.addEventListener('resize', updatePosition)
    window.addEventListener('scroll', updatePosition, true)
    document.addEventListener('pointerdown', handlePointer)
    document.addEventListener('keydown', handleKey)
    return () => {
      window.removeEventListener('resize', updatePosition)
      window.removeEventListener('scroll', updatePosition, true)
      document.removeEventListener('pointerdown', handlePointer)
      document.removeEventListener('keydown', handleKey)
    }
  }, [open])

  const days = useMemo(() => calendarDays(visibleMonth), [visibleMonth])
  const years = useMemo(() => Array.from({ length: 12 }, (_, index) => yearPageStart + index), [yearPageStart])
  const displayValue = selected ? DATE_FORMAT.format(selected) : 'Seleccionar fecha'

  function selectDate(date: Date) {
    const next = toIso(date)
    if (isOutsideRange(next, min, max)) return
    onChange(next)
    setOpen(false)
    triggerRef.current?.focus()
  }

  function selectToday() {
    const next = toIso(today)
    if (isOutsideRange(next, min, max)) return
    onChange(next)
    setVisibleMonth(monthStart(today))
    setOpen(false)
    triggerRef.current?.focus()
  }

  function selectMonth(month: number) {
    setVisibleMonth((current) => new Date(current.getFullYear(), month, 1))
    setView('DAYS')
  }

  function selectYear(year: number) {
    setVisibleMonth((current) => new Date(year, current.getMonth(), 1))
    setYearPageStart(Math.floor(year / 12) * 12)
    setView('MONTHS')
  }

  function previousPeriod() {
    if (view === 'DAYS') setVisibleMonth((current) => addMonths(current, -1))
    else if (view === 'MONTHS') setVisibleMonth((current) => addYears(current, -1))
    else setYearPageStart((current) => current - 12)
  }

  function nextPeriod() {
    if (view === 'DAYS') setVisibleMonth((current) => addMonths(current, 1))
    else if (view === 'MONTHS') setVisibleMonth((current) => addYears(current, 1))
    else setYearPageStart((current) => current + 12)
  }

  return (
    <div className={`vt-date-field${className ? ` ${className}` : ''}`}>
      {name && <input type="hidden" name={name} value={value} />}
      <button
        ref={triggerRef}
        id={controlId}
        type="button"
        className="vt-date-trigger"
        aria-label={ariaLabel}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-invalid={ariaInvalid || undefined}
        aria-required={required || undefined}
        disabled={disabled}
        onClick={() => setOpen((current) => !current)}
      >
        <span className={selected ? '' : 'vt-date-placeholder'}>{displayValue}</span>
        <Icon name="calendar" size={18} />
      </button>
      {open && createPortal(
        <div
          ref={popoverRef}
          className="vt-calendar-popover"
          role="dialog"
          aria-label="Calendario"
          style={position}
        >
          <div className="vt-calendar-header">
            <button type="button" className="vt-calendar-nav" aria-label="Periodo anterior" onClick={previousPeriod}>
              <Icon name="chevronLeft" size={18} />
            </button>
            <div className="vt-calendar-period-controls">
              {view === 'DAYS' && (
                <>
                  <button type="button" onClick={() => setView('MONTHS')}>{capitalize(MONTH_NAMES[visibleMonth.getMonth()] ?? '')}</button>
                  <button type="button" onClick={() => { setYearPageStart(Math.floor(visibleMonth.getFullYear() / 12) * 12); setView('YEARS') }}>{visibleMonth.getFullYear()}</button>
                </>
              )}
              {view === 'MONTHS' && (
                <button type="button" onClick={() => { setYearPageStart(Math.floor(visibleMonth.getFullYear() / 12) * 12); setView('YEARS') }}>{visibleMonth.getFullYear()}</button>
              )}
              {view === 'YEARS' && <strong>{yearPageStart}–{yearPageStart + 11}</strong>}
            </div>
            <button type="button" className="vt-calendar-nav" aria-label="Periodo siguiente" onClick={nextPeriod}>
              <Icon name="chevronRight" size={18} />
            </button>
          </div>

          {view === 'DAYS' && (
            <>
              <div className="vt-calendar-weekdays" aria-hidden="true">
                {WEEKDAYS.map((weekday, index) => <span key={`${weekday}-${index}`}>{weekday}</span>)}
              </div>
              <div className="vt-calendar-grid">
                {days.map((day) => {
                  const iso = toIso(day)
                  const outsideMonth = day.getMonth() !== visibleMonth.getMonth()
                  const unavailable = isOutsideRange(iso, min, max)
                  return (
                    <button
                      key={iso}
                      type="button"
                      className={[
                        'vt-calendar-day',
                        outsideMonth ? 'outside-month' : '',
                        sameDay(day, today) ? 'today' : '',
                        selected && sameDay(day, selected) ? 'selected' : ''
                      ].filter(Boolean).join(' ')}
                      aria-label={DATE_FORMAT.format(day)}
                      aria-pressed={Boolean(selected && sameDay(day, selected))}
                      disabled={unavailable}
                      onClick={() => selectDate(day)}
                    >
                      {day.getDate()}
                    </button>
                  )
                })}
              </div>
            </>
          )}

          {view === 'MONTHS' && (
            <div className="vt-calendar-choice-grid vt-calendar-month-grid" role="grid" aria-label={`Meses de ${visibleMonth.getFullYear()}`}>
              {MONTH_NAMES.map((monthName, month) => {
                const selectedMonth = selected?.getFullYear() === visibleMonth.getFullYear() && selected.getMonth() === month
                return (
                  <button
                    key={monthName}
                    type="button"
                    className={`vt-calendar-choice${selectedMonth ? ' selected' : ''}`}
                    disabled={monthOutsideRange(visibleMonth.getFullYear(), month, min, max)}
                    onClick={() => selectMonth(month)}
                  >
                    {capitalize(monthName.slice(0, 3))}
                  </button>
                )
              })}
            </div>
          )}

          {view === 'YEARS' && (
            <div className="vt-calendar-choice-grid vt-calendar-year-grid" role="grid" aria-label="Seleccionar año">
              {years.map((year) => (
                <button
                  key={year}
                  type="button"
                  className={`vt-calendar-choice${selected?.getFullYear() === year ? ' selected' : ''}${today.getFullYear() === year ? ' today' : ''}`}
                  disabled={yearOutsideRange(year, min, max)}
                  onClick={() => selectYear(year)}
                >
                  {year}
                </button>
              ))}
            </div>
          )}

          <div className="vt-calendar-actions">
            {allowClear && (
              <button type="button" className="secondary-button compact-button" disabled={!value} onClick={() => { onChange(''); setOpen(false); triggerRef.current?.focus() }}>
                Limpiar
              </button>
            )}
            <button type="button" className="secondary-button compact-button" onClick={() => { setVisibleMonth(monthStart(today)); setYearPageStart(Math.floor(today.getFullYear() / 12) * 12); setView('DAYS') }}>
              Ir a hoy
            </button>
            <button type="button" className="primary-button compact-button" disabled={isOutsideRange(toIso(today), min, max)} onClick={selectToday}>
              Hoy
            </button>
          </div>
        </div>,
        document.body
      )}
    </div>
  )
}

export function DateTimeField({ value, onChange, ...props }: DateTimeFieldProps) {
  const [date = '', time = ''] = value ? value.split('T') : ['', '']
  const normalizedTime = time.slice(0, 5)

  function changeDate(nextDate: string) {
    onChange(nextDate ? `${nextDate}T${normalizedTime || '00:00'}` : '')
  }

  function changeTime(nextTime: string) {
    if (!date) return
    onChange(`${date}T${nextTime || '00:00'}`)
  }

  return (
    <div className="vt-datetime-field">
      <DateField {...props} value={date} onChange={changeDate} allowClear={!props.required} />
      <div className="vt-time-control">
        <Icon name="clock" size={17} />
        <input
          type="time"
          aria-label="Hora"
          value={normalizedTime}
          disabled={props.disabled || !date}
          required={props.required}
          onChange={(event) => changeTime(event.target.value)}
        />
      </div>
    </div>
  )
}
