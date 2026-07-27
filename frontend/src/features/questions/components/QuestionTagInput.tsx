import { useEffect, useMemo, useRef, useState, type KeyboardEvent } from 'react'
import { suggestQuestionTags } from '../api/questionApi'
import type { QuestionTag } from '../../../shared/types/questions'
import { Icon } from '../../../shared/components/Icon'

const MAX_TAGS = 10
const MAX_LENGTH = 40

type QuestionTagInputProps = {
  value: string[]
  onChange: (tags: string[]) => void
  disabled?: boolean
}

function comparable(value: string) {
  return value
    .trim()
    .replace(/^#+/, '')
    .replace(/\s+/g, ' ')
    .normalize('NFD')
    .replace(/\p{M}+/gu, '')
    .toLocaleLowerCase('es-MX')
}

function displayValue(value: string) {
  return value.trim().replace(/^#+/, '').replace(/\s+/g, ' ')
}

function slug(value: string) {
  return comparable(value).replace(/[_\s]+/g, '-').replace(/-+/g, '-')
}

export function QuestionTagInput({ value, onChange, disabled = false }: QuestionTagInputProps) {
  const [input, setInput] = useState('')
  const [suggestions, setSuggestions] = useState<QuestionTag[]>([])
  const [error, setError] = useState('')
  const requestId = useRef(0)

  useEffect(() => {
    const query = input.trim()
    if (!query) {
      setSuggestions([])
      return
    }
    const controller = new AbortController()
    const currentRequest = ++requestId.current
    const timeout = window.setTimeout(() => {
      suggestQuestionTags(query, controller.signal)
        .then((response) => {
          if (currentRequest === requestId.current) setSuggestions(response)
        })
        .catch(() => {
          if (!controller.signal.aborted && currentRequest === requestId.current) {
            setSuggestions([])
          }
        })
    }, 250)
    return () => {
      window.clearTimeout(timeout)
      controller.abort()
    }
  }, [input])

  const selected = useMemo(() => new Set(value.map(comparable)), [value])

  function add(rawValue: string) {
    const nextValue = displayValue(rawValue)
    setError('')
    if (!nextValue) return
    if (nextValue.length > MAX_LENGTH) {
      setError('Cada etiqueta puede tener hasta 40 caracteres.')
      return
    }
    if (!/^[\p{L}\p{N}_ -]+$/u.test(nextValue)) {
      setError('La etiqueta contiene símbolos no permitidos.')
      return
    }
    if (selected.has(comparable(nextValue))) {
      setInput('')
      return
    }
    if (value.length >= MAX_TAGS) {
      setError('Puedes agregar un máximo de 10 etiquetas.')
      return
    }
    onChange([...value, nextValue])
    setInput('')
    setSuggestions([])
  }

  function remove(index: number) {
    onChange(value.filter((_, currentIndex) => currentIndex !== index))
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault()
      add(input)
    }
    if (event.key === 'Backspace' && !input && value.length > 0) {
      remove(value.length - 1)
    }
  }

  return (
    <div className="form-field question-tag-field">
      <div className="question-tag-heading">
        <div>
          <label htmlFor="question-tags">Etiquetas</label>
          <small>Opcionales · máximo {MAX_TAGS}</small>
        </div>
        <span>{value.length}/{MAX_TAGS}</span>
      </div>

      <div className={`question-tag-input${disabled ? ' is-disabled' : ''}`}>
        {value.map((tag, index) => (
          <span className="question-tag-chip" key={`${comparable(tag)}-${index}`}>
            #{slug(tag)}
            <button
              type="button"
              onClick={() => remove(index)}
              disabled={disabled}
              aria-label={`Quitar etiqueta ${tag}`}
            >
              <Icon name="close" size={12} />
            </button>
          </span>
        ))}
        <input
          id="question-tags"
          value={input}
          disabled={disabled || value.length >= MAX_TAGS}
          maxLength={MAX_LENGTH}
          onChange={(event) => setInput(event.target.value)}
          onKeyDown={onKeyDown}
          placeholder={value.length ? 'Agregar otra etiqueta' : 'Ej. herencia, polimorfismo'}
          autoComplete="off"
          aria-describedby={error ? 'question-tags-error' : 'question-tags-help'}
        />
        <button
          className="question-tag-add"
          type="button"
          disabled={disabled || !input.trim() || value.length >= MAX_TAGS}
          onClick={() => add(input)}
        >
          Agregar
        </button>
      </div>

      <small id="question-tags-help">Presiona Enter o coma para agregar. El símbolo # se genera automáticamente.</small>
      {error && <small className="field-error" id="question-tags-error" role="alert">{error}</small>}

      {suggestions.length > 0 && (
        <div className="question-tag-suggestions" role="listbox" aria-label="Etiquetas sugeridas">
          {suggestions
            .filter((suggestion) => !selected.has(comparable(suggestion.displayName)))
            .map((suggestion) => (
              <button
                type="button"
                role="option"
                key={suggestion.publicId}
                onClick={() => add(suggestion.displayName)}
              >
                #{suggestion.slug}
              </button>
            ))}
        </div>
      )}
    </div>
  )
}
