import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type FormEvent
} from 'react'
import { Link } from 'react-router-dom'
import { getQuestionCatalogs } from '../api/questionApi'
import { ApiRequestError } from '../../../shared/api/apiClient'
import { Icon } from '../../../shared/components/Icon'
import { useToast } from '../../../shared/components/ToastProvider'
import type {
  QuestionAnswerSettings,
  QuestionCatalogs,
  QuestionDetail,
  QuestionMedia,
  QuestionOptionPayload,
  QuestionPayload,
  QuestionTypeCode
} from '../../../shared/types/questions'
import { JavaCodeEditor } from './JavaCodePanel'
import { MediaUploadField } from './MediaUploadField'

type DraftOption = {
  id: string
  text: string
  media?: QuestionMedia
  matchText: string
  matchMedia?: QuestionMedia
  correct: boolean
  feedback: string
}

const optionTypes = new Set<QuestionTypeCode>([
  'SINGLE_CHOICE',
  'MULTIPLE_CHOICE',
  'TRUE_FALSE',
  'MATCHING'
])

const emptySettings: QuestionAnswerSettings = {
  acceptedAnswers: [],
  caseSensitive: false,
  manualReview: false
}

function newOption(overrides: Partial<DraftOption> = {}): DraftOption {
  return {
    id: crypto.randomUUID(),
    text: '',
    matchText: '',
    correct: false,
    feedback: '',
    ...overrides
  }
}

function defaultOptions(type: QuestionTypeCode): DraftOption[] {
  if (type === 'TRUE_FALSE') {
    return [
      newOption({ text: 'Verdadero', correct: true }),
      newOption({ text: 'Falso', correct: false })
    ]
  }
  if (type === 'MATCHING') {
    return [newOption({ correct: true }), newOption({ correct: true })]
  }
  return [newOption({ correct: true }), newOption()]
}

function optionsFromInitial(initial?: QuestionDetail): DraftOption[] {
  if (!initial?.options.length) return defaultOptions(initial?.typeCode ?? 'SINGLE_CHOICE')
  return initial.options.map((option) => ({
    id: option.publicId,
    text: option.text ?? '',
    media: option.media,
    matchText: option.matchText ?? '',
    matchMedia: option.matchMedia,
    correct: option.correct,
    feedback: option.feedback ?? ''
  }))
}

interface QuestionEditorProps {
  initial?: QuestionDetail
  onSubmit: (payload: QuestionPayload) => Promise<void>
  submitLabel: string
}

export function QuestionEditor({ initial, onSubmit, submitLabel }: QuestionEditorProps) {
  const toast = useToast()
  const [catalogs, setCatalogs] = useState<QuestionCatalogs>()
  const [type, setType] = useState<QuestionTypeCode>(initial?.typeCode ?? 'SINGLE_CHOICE')
  const [categories, setCategories] = useState<string[]>(initial?.categories.map((category) => category.publicId) ?? [])
  const [statement, setStatement] = useState(initial?.statement ?? '')
  const [explanation, setExplanation] = useState(initial?.explanation ?? '')
  const [promptMedia, setPromptMedia] = useState<QuestionMedia | undefined>(initial?.promptMedia)
  const [codeContent, setCodeContent] = useState(initial?.codeContent ?? '')
  const [codeOpen, setCodeOpen] = useState(Boolean(initial?.codeContent))
  const [feedbackOpen, setFeedbackOpen] = useState(Boolean(initial?.explanation))
  const [settings, setSettings] = useState<QuestionAnswerSettings>(initial?.answerSettings ?? emptySettings)
  const [options, setOptions] = useState<DraftOption[]>(() => optionsFromInitial(initial))
  const [busy, setBusy] = useState(false)
  const [categoryQuery, setCategoryQuery] = useState('')
  const nonTrueFalseOptions = useRef<DraftOption[]>(type === 'TRUE_FALSE' ? [] : optionsFromInitial(initial))

  useEffect(() => {
    getQuestionCatalogs()
      .then((response) => {
        setCatalogs(response)
        if (categories.length === 0 && response.categories[0]) {
          setCategories([response.categories[0].publicId])
        }
      })
      .catch(() => toast.error('No fue posible cargar los catálogos'))
  }, [])

  const usesOptions = optionTypes.has(type)
  const categoryOptions = useMemo(() => {
    const active = catalogs?.categories.filter((category) => category.status === 'ACTIVE') ?? []
    const retained = initial?.categories.filter(
      (category) => !active.some((activeCategory) => activeCategory.publicId === category.publicId)
    ) ?? []
    return [...active, ...retained]
  }, [catalogs, initial])

  const filteredCategoryOptions = useMemo(() => {
    const query = categoryQuery.trim().toLocaleLowerCase('es-MX')
    if (!query) return categoryOptions
    return categoryOptions.filter((category) =>
      `${category.name} ${category.code}`.toLocaleLowerCase('es-MX').includes(query)
    )
  }, [categoryOptions, categoryQuery])

  function changeType(next: QuestionTypeCode) {
    if (next === type) return

    if (next === 'TRUE_FALSE') {
      if (type !== 'TRUE_FALSE') nonTrueFalseOptions.current = options
      const previousCorrect = options.findIndex((option) => option.correct)
      setOptions([
        newOption({ text: 'Verdadero', correct: previousCorrect !== 1, feedback: options[0]?.feedback ?? '' }),
        newOption({ text: 'Falso', correct: previousCorrect === 1, feedback: options[1]?.feedback ?? '' })
      ])
    } else if (type === 'TRUE_FALSE') {
      const restored = nonTrueFalseOptions.current
      setOptions(restored.length ? restored : defaultOptions(next))
    } else if (next === 'MATCHING') {
      setOptions((current) => current.length >= 2
        ? current.map((option) => ({ ...option, correct: true }))
        : defaultOptions(next))
    }

    setType(next)
    setSettings((current) => ({
      ...current,
      manualReview: next === 'OPEN_TEXT'
    }))
  }

  function updateOption(id: string, patch: Partial<DraftOption>) {
    setOptions((current) => current.map((option) => option.id === id ? { ...option, ...patch } : option))
  }

  function toggleCorrect(id: string, checked: boolean) {
    setOptions((current) => current.map((option) => {
      if (option.id === id) return { ...option, correct: checked }
      return type === 'MULTIPLE_CHOICE' ? option : { ...option, correct: false }
    }))
  }

  function moveOption(index: number, direction: -1 | 1) {
    setOptions((current) => {
      const target = index + direction
      if (target < 0 || target >= current.length) return current
      const next = [...current]
      const [item] = next.splice(index, 1)
      if (!item) return current
      next.splice(target, 0, item)
      return next
    })
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (busy) return
    setBusy(true)
    try {
      const payload: QuestionPayload = {
        typeCode: type,
        categoryPublicIds: categories,
        statement: statement.trim(),
        explanation: explanation.trim() || undefined,
        promptMediaPublicId: promptMedia?.publicId,
        codeContent: codeContent.trim() || undefined,
        answerSettings: {
          ...settings,
          manualReview: type === 'OPEN_TEXT'
        },
        options: usesOptions
          ? options.map<QuestionOptionPayload>((option) => ({
              text: option.text.trim() || undefined,
              mediaPublicId: option.media?.publicId,
              matchText: type === 'MATCHING' ? option.matchText.trim() || undefined : undefined,
              matchMediaPublicId: type === 'MATCHING' ? option.matchMedia?.publicId : undefined,
              correct: type === 'MATCHING' ? true : option.correct,
              feedback: option.feedback.trim() || undefined
            }))
          : []
      }
      await onSubmit(payload)
    } catch (error) {
      if (error instanceof ApiRequestError && error.status === 409) {
        toast.warning(
          'La pregunta cambió mientras la editabas',
          `${error.message} Tus datos permanecen en el formulario para que puedas revisarlos.`
        )
      } else {
        toast.error(
          'No fue posible guardar la pregunta',
          error instanceof ApiRequestError ? error.message : undefined
        )
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="question-editor question-editor-v2" onSubmit={(event) => void submit(event)}>
      <section className="editor-card editor-primary">
        <div className="form-field">
          <label htmlFor="statement">Enunciado</label>
          <textarea
            id="statement"
            rows={4}
            maxLength={10000}
            required
            value={statement}
            onChange={(event) => setStatement(event.target.value)}
            placeholder="Escribe el enunciado de la pregunta…"
          />
        </div>

        <MediaUploadField
          label="Imagen del enunciado"
          value={promptMedia}
          onChange={setPromptMedia}
        />

        <details
          className="inline-details code-details"
          open={codeOpen}
          onToggle={(event) => setCodeOpen(event.currentTarget.open)}
        >
          <summary>
            <span><Icon name="code" size={17} /> Agregar bloque de código Java</span>
            <small>Opcional</small>
          </summary>
          <div className="optional-form-content">
            <JavaCodeEditor value={codeContent} onChange={setCodeContent} />
          </div>
        </details>
      </section>

      <section className="editor-card question-classification-card">
        <div className="editor-grid single-type-grid">
          <div className="form-field">
            <label>Tipo de pregunta</label>
            <select value={type} onChange={(event) => changeType(event.target.value as QuestionTypeCode)}>
              {catalogs?.types.map((catalogType) => (
                <option key={catalogType.code} value={catalogType.code}>{catalogType.name}</option>
              ))}
            </select>
            <small>El contenido escrito se conserva cuando cambias de tipo.</small>
          </div>
        </div>

        <div className="form-field category-picker-field">
          <div className="category-picker-header">
            <div>
              <label>Categorías</label>
              <span className="selection-count">
                {categories.length} seleccionada{categories.length === 1 ? '' : 's'}
              </span>
            </div>
            {categoryOptions.length > 6 && (
              <input
                className="category-filter-input"
                type="search"
                value={categoryQuery}
                onChange={(event) => setCategoryQuery(event.target.value)}
                placeholder="Buscar categoría"
                aria-label="Buscar categoría"
              />
            )}
          </div>
          <div className="category-selector-compact" role="group" aria-label="Categorías de la pregunta">
            {filteredCategoryOptions.map((category) => {
              const selected = categories.includes(category.publicId)
              return (
                <button
                  className={`category-choice-compact ${selected ? 'selected' : ''} ${category.status === 'INACTIVE' ? 'inactive' : ''}`}
                  type="button"
                  role="checkbox"
                  aria-checked={selected}
                  key={category.publicId}
                  onClick={() => setCategories((current) =>
                    selected
                      ? current.filter((id) => id !== category.publicId)
                      : [...current, category.publicId]
                  )}
                >
                  <span className="category-choice-indicator" aria-hidden="true">
                    {selected && <Icon name="check" size={13} />}
                  </span>
                  <span className="category-choice-label">{category.name}</span>
                  {category.status === 'INACTIVE' && <span className="category-choice-meta">Inactiva</span>}
                </button>
              )
            })}
          </div>
          {filteredCategoryOptions.length === 0 && (
            <p className="empty-inline-message">No hay categorías que coincidan con la búsqueda.</p>
          )}
        </div>
      </section>

      {usesOptions ? (
        <section className="editor-card option-editor-card">
          <div className="card-title-row">
            <div>
              <h2>{type === 'MATCHING' ? 'Relaciones' : 'Opciones de respuesta'}</h2>
              <p className="muted">
                {type === 'MATCHING'
                  ? 'Cada fila representa una relación correcta entre ambas columnas.'
                  : 'Puedes combinar texto e imagen y agregar retroalimentación individual.'}
              </p>
            </div>
            {type !== 'TRUE_FALSE' && (
              <button
                className="secondary-button compact-button"
                type="button"
                onClick={() => setOptions((current) => [...current, newOption({ correct: type === 'MATCHING' })])}
              >
                <Icon name="plus" size={15} /> Agregar
              </button>
            )}
          </div>

          <div className={`question-option-list ${type === 'MATCHING' ? 'matching-option-list' : ''}`}>
            {options.map((option, index) => (
              <article className="advanced-option-row option-card-v2" key={option.id}>
                <div className="option-order-controls">
                  <span>{index + 1}</span>
                  <button
                    aria-label={`Subir opción ${index + 1}`}
                    type="button"
                    disabled={index === 0}
                    onClick={() => moveOption(index, -1)}
                  >↑</button>
                  <button
                    aria-label={`Bajar opción ${index + 1}`}
                    type="button"
                    disabled={index === options.length - 1}
                    onClick={() => moveOption(index, 1)}
                  >↓</button>
                </div>

                {type !== 'MATCHING' && (
                  <label className="option-correct-control">
                    <input
                      type={type === 'MULTIPLE_CHOICE' ? 'checkbox' : 'radio'}
                      name="correct"
                      checked={option.correct}
                      onChange={(event) => toggleCorrect(option.id, event.target.checked)}
                    />
                    <span>{option.correct ? 'Correcta' : 'Marcar correcta'}</span>
                  </label>
                )}

                <div className={`option-content ${type === 'MATCHING' ? 'matching-columns' : ''}`}>
                  <div className="option-column">
                    {type === 'MATCHING' && <strong className="option-column-label">Columna izquierda</strong>}
                    <textarea
                      rows={2}
                      placeholder={type === 'MATCHING' ? `Elemento ${index + 1}` : `Opción ${index + 1}`}
                      readOnly={type === 'TRUE_FALSE'}
                      value={option.text}
                      onChange={(event) => updateOption(option.id, { text: event.target.value })}
                    />
                    <MediaUploadField
                      compact
                      label="Imagen opcional"
                      value={option.media}
                      onChange={(media) => updateOption(option.id, { media })}
                    />
                  </div>

                  {type === 'MATCHING' && (
                    <div className="matching-connector" aria-hidden="true">↔</div>
                  )}

                  {type === 'MATCHING' && (
                    <div className="option-column">
                      <strong className="option-column-label">Columna derecha</strong>
                      <textarea
                        rows={2}
                        placeholder={`Relación ${index + 1}`}
                        value={option.matchText}
                        onChange={(event) => updateOption(option.id, { matchText: event.target.value })}
                      />
                      <MediaUploadField
                        compact
                        label="Imagen opcional"
                        value={option.matchMedia}
                        onChange={(matchMedia) => updateOption(option.id, { matchMedia })}
                      />
                    </div>
                  )}

                  <details className="option-feedback-details">
                    <summary>Retroalimentación de esta {type === 'MATCHING' ? 'relación' : 'opción'}</summary>
                    <textarea
                      rows={3}
                      maxLength={4000}
                      placeholder="Mensaje que se mostrará al revisar esta respuesta."
                      value={option.feedback}
                      onChange={(event) => updateOption(option.id, { feedback: event.target.value })}
                    />
                  </details>
                </div>

                {type !== 'TRUE_FALSE' && (
                  <button
                    aria-label={`Eliminar opción ${index + 1}`}
                    className="icon-button danger-icon-button option-delete-button"
                    type="button"
                    onClick={() => setOptions((current) => current.filter((item) => item.id !== option.id))}
                  >
                    <Icon name="close" size={15} />
                  </button>
                )}
              </article>
            ))}
          </div>
        </section>
      ) : (
        <section className="editor-card open-answer-card">
          <div className="card-title-row">
            <div>
              <h2>Respuesta abierta</h2>
              <p className="muted">La respuesta será revisada manualmente.</p>
            </div>
          </div>
          <div className="form-field compact-number-field">
            <label>Máximo de caracteres</label>
            <input
              type="number"
              min="1"
              max="100000"
              value={settings.maxLength ?? ''}
              onChange={(event) => setSettings((current) => ({
                ...current,
                maxLength: event.target.value ? Number(event.target.value) : undefined,
                manualReview: true
              }))}
              placeholder="Sin límite"
            />
          </div>
        </section>
      )}

      <details
        className="editor-card inline-details feedback-card"
        open={feedbackOpen}
        onToggle={(event) => setFeedbackOpen(event.currentTarget.open)}
      >
        <summary>
          <span>Explicación y retroalimentación general</span>
          <small>Opcional</small>
        </summary>
        <div className="form-field optional-form-content">
          <label>Explicación general</label>
          <textarea
            rows={4}
            maxLength={10000}
            value={explanation}
            onChange={(event) => setExplanation(event.target.value)}
            placeholder="Explica la respuesta correcta o agrega material de repaso."
          />
        </div>
      </details>

      <div className="sticky-form-actions">
        <Link className="secondary-button button-link" to="/admin/questions">Cancelar</Link>
        <button className="primary-button" disabled={busy || categories.length === 0} type="submit">
          {busy ? 'Guardando…' : submitLabel}
        </button>
      </div>
    </form>
  )
}
