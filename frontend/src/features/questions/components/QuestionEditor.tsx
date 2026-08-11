import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type FormEvent
} from 'react'
import { Link } from 'react-router-dom'
import { getQuestionCatalogs, getQuestionCategoryOptions, getQuestionTechnologyOptions } from '../api/questionApi'
import { ApiRequestError, isPlatformRequestFailure } from '../../../shared/api/apiClient'
import { Icon } from '../../../shared/components/Icon'
import { useToast } from '../../../shared/components/ToastProvider'
import type {
  QuestionAnswerSettings,
  QuestionCatalogs,
  QuestionDetail,
  QuestionMedia,
  QuestionOptionPayload,
  QuestionPayload,
  QuestionTypeCode,
  ContentScope
} from '../../../shared/types/questions'
import { JavaCodeEditor } from './JavaCodePanel'
import { MediaUploadField } from './MediaUploadField'
import { QuestionTagInput } from './QuestionTagInput'
import { QuestionClassificationSelect } from './QuestionClassificationSelect'

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
  targetScope?: ContentScope
  organizationPublicId?: string
  catalogContextReady?: boolean
}

export function QuestionEditor({
  initial,
  onSubmit,
  submitLabel,
  targetScope,
  organizationPublicId,
  catalogContextReady = true
}: QuestionEditorProps) {
  const toast = useToast()
  const [catalogs, setCatalogs] = useState<QuestionCatalogs>()
  const [availableCategories, setAvailableCategories] = useState<QuestionCatalogs['categories']>()
  const [availableTechnologies, setAvailableTechnologies] = useState<QuestionCatalogs['technologies']>([])
  const [catalogLoading, setCatalogLoading] = useState(true)
  const [catalogUnavailable, setCatalogUnavailable] = useState(false)
  const [type, setType] = useState<QuestionTypeCode>(initial?.typeCode ?? 'SINGLE_CHOICE')
  const [difficultyCode, setDifficultyCode] = useState(initial?.difficultyCode ?? 'JR')
  const [technologyPublicId, setTechnologyPublicId] = useState(initial?.technology?.publicId ?? '')
  const [levelCode, setLevelCode] = useState(initial?.levelCode ?? '')
  const [categoryPublicIds, setCategoryPublicIds] = useState<string[]>(
    initial?.categories.map((category) => category.publicId) ?? []
  )
  const [tags, setTags] = useState<string[]>(initial?.tags.map((tag) => tag.displayName) ?? [])
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
    const controller = new AbortController()
    setCatalogLoading(true)
    setCatalogUnavailable(false)
    setAvailableCategories(undefined)
    setAvailableTechnologies([])
    Promise.allSettled([
      getQuestionCatalogs(controller.signal),
      catalogContextReady
        ? getQuestionCategoryOptions(initial?.publicId, targetScope, organizationPublicId, controller.signal)
        : Promise.resolve([]),
      catalogContextReady
        ? getQuestionTechnologyOptions(initial?.publicId, targetScope, organizationPublicId, controller.signal)
        : Promise.resolve([])
    ]).then(([catalogResult, categoryResult, technologyResult]) => {
      if (controller.signal.aborted) return
      const failures: string[] = []
      if (catalogResult.status === 'fulfilled') {
        setCatalogs(catalogResult.value)
      } else {
        failures.push('No fue posible cargar los tipos de pregunta disponibles.')
      }
      if (categoryResult.status === 'fulfilled') {
        setAvailableCategories(categoryResult.value)
        if (!initial) {
          const allowed = new Set(categoryResult.value.map((category) => category.publicId))
          setCategoryPublicIds((current) => current.filter((id) => allowed.has(id)))
        }
      } else {
        failures.push('No fue posible cargar las categorías disponibles.')
      }
      if (technologyResult.status === 'fulfilled') {
        setAvailableTechnologies(technologyResult.value)
        if (!initial) {
          const allowed = new Set(technologyResult.value.map((technology) => technology.publicId))
          setTechnologyPublicId((current) => allowed.has(current) ? current : '')
        }
      } else {
        failures.push('No fue posible cargar las tecnologías disponibles.')
      }
      if (failures.length > 0) {
        const failedResults = [catalogResult, categoryResult, technologyResult]
          .filter((result) => result.status === 'rejected')
        const platformFailure = failedResults.some((result) =>
          result.status === 'rejected' && isPlatformRequestFailure(result.reason)
        )
        setCatalogUnavailable(true)
        if (!platformFailure) {
          toast.error('No fue posible preparar el formulario de la pregunta.', failures.join(' '))
        }
      }
      setCatalogLoading(false)
    })
    return () => controller.abort()
  }, [catalogContextReady, initial?.publicId, targetScope, organizationPublicId, toast])

  const usesOptions = optionTypes.has(type)
  const categoryOptions = useMemo(() => {
    const activeAndRetained = availableCategories ?? []
    const missingInitial = initial?.categories.filter(
      (category) => !activeAndRetained.some((option) => option.publicId === category.publicId)
    ) ?? []
    return [...activeAndRetained, ...missingInitial]
  }, [availableCategories, initial?.categories])

  const selectedCategories = useMemo(() => categoryOptions.filter(
    (category) => categoryPublicIds.includes(category.publicId)
  ), [categoryOptions, categoryPublicIds])

  function toggleCategory(publicId: string) {
    setCategoryPublicIds((current) => current.includes(publicId)
      ? current.filter((id) => id !== publicId)
      : [...current, publicId])
  }

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
        difficultyCode: difficultyCode || undefined,
        technologyPublicId: technologyPublicId || undefined,
        levelCode: levelCode || undefined,
        categoryPublicIds,
        tags,
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
      if (isPlatformRequestFailure(error)) {
        // apiClient ya publicó el único feedback global para fallas de plataforma.
      } else if (error instanceof ApiRequestError && error.status === 409) {
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
        <div className="editor-grid question-governance-grid">
          <QuestionClassificationSelect
            id="question-type"
            label="Tipo de pregunta"
            value={type}
            options={(catalogs?.types ?? []).map((catalogType) => ({
              value: catalogType.code,
              label: catalogType.name
            }))}
            help="El contenido escrito se conserva cuando cambias de tipo."
            disabled={catalogLoading || !catalogs}
            onChange={(value) => changeType(value as QuestionTypeCode)}
          />
          <QuestionClassificationSelect
            id="question-difficulty"
            label="Dificultad"
            value={difficultyCode}
            options={[
              { value: 'JR', label: 'JR' },
              { value: 'STD', label: 'STD' },
              { value: 'SR', label: 'SR' }
            ]}
            help="Selecciona JR, STD o SR."
            required
            disabled={busy}
            onChange={setDifficultyCode}
          />
          <QuestionClassificationSelect
            id="question-technology"
            label="Tecnología"
            value={technologyPublicId}
            options={[
              { value: '', label: 'Sin tecnología' },
              ...availableTechnologies.map((technology) => ({
                value: technology.publicId,
                label: technology.name
              }))
            ]}
            help="Solo se muestran tecnologías válidas para el alcance seleccionado."
            disabled={busy || catalogLoading}
            onChange={setTechnologyPublicId}
          />
          <QuestionClassificationSelect
            id="question-seniority"
            label="Seniority"
            value={levelCode}
            options={[
              { value: '', label: 'Sin seniority' },
              { value: 'JR', label: 'JR' },
              { value: 'STD', label: 'STD' },
              { value: 'SR', label: 'SR' }
            ]}
            help="Clasificación de seniority independiente de la dificultad."
            disabled={busy}
            onChange={setLevelCode}
          />
        </div>

        <div className="form-field category-picker-field">
          <div className="category-picker-header">
            <div>
              <label>Categorías</label>
              <span className="selection-count">{categoryPublicIds.length} seleccionada{categoryPublicIds.length === 1 ? '' : 's'}</span>
            </div>
            <input
              className="category-filter-input"
              type="search"
              value={categoryQuery}
              onChange={(event) => setCategoryQuery(event.target.value)}
              placeholder="Buscar categoría"
              aria-label="Buscar categoría"
              disabled={catalogLoading || catalogUnavailable}
            />
          </div>
          {selectedCategories.length > 0 && (
            <div className="selected-category-chips" aria-label="Categorías seleccionadas">
              {selectedCategories.slice(0, 3).map((category) => (
                <button
                  className="selected-category-chip"
                  key={category.publicId}
                  type="button"
                  disabled={busy}
                  onClick={() => toggleCategory(category.publicId)}
                  aria-label={`Retirar ${category.name}`}
                >
                  {category.name}{category.status === 'INACTIVE' ? ' · Inactiva' : ''}<span aria-hidden="true">×</span>
                </button>
              ))}
              {selectedCategories.length > 3 && (
                <span className="selected-category-more">+{selectedCategories.length - 3}</span>
              )}
            </div>
          )}
          {catalogLoading && !catalogUnavailable && <small>Cargando categorías disponibles…</small>}
          {availableCategories && !catalogUnavailable && categoryOptions.length === 0 && (
            <p className="empty-inline-message">No existen categorías activas disponibles para crear una pregunta.</p>
          )}
          {availableCategories && !catalogUnavailable && categoryOptions.length > 0 && filteredCategoryOptions.length === 0 && (
            <p className="empty-inline-message">No hay categorías que coincidan con la búsqueda.</p>
          )}
          {filteredCategoryOptions.length > 0 && (
            <div className="category-selector-compact" role="group" aria-label="Categorías disponibles">
              {filteredCategoryOptions.map((category) => {
                const selected = categoryPublicIds.includes(category.publicId)
                const inactive = category.status !== 'ACTIVE'
                return (
                  <button
                    className={`category-choice-compact${selected ? ' selected' : ''}${inactive ? ' inactive' : ''}`}
                    key={category.publicId}
                    type="button"
                    disabled={busy || (inactive && !selected)}
                    onClick={() => toggleCategory(category.publicId)}
                    aria-pressed={selected}
                  >
                    <span className="category-choice-indicator" aria-hidden="true">{selected ? '✓' : ''}</span>
                    <span className="category-choice-label">{category.name}</span>
                    {inactive && <span className="category-choice-meta">Inactiva</span>}
                  </button>
                )
              })}
            </div>
          )}
          {categoryPublicIds.length === 0 && <small className="field-error">Selecciona al menos una categoría.</small>}
        </div>
        <QuestionTagInput value={tags} onChange={setTags} disabled={busy} />
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
                  ><Icon name="arrowUp" size={15} /></button>
                  <button
                    aria-label={`Bajar opción ${index + 1}`}
                    type="button"
                    disabled={index === options.length - 1}
                    onClick={() => moveOption(index, 1)}
                  ><Icon name="arrowDown" size={15} /></button>
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
        <button className="primary-button" disabled={busy || catalogLoading || catalogUnavailable || !catalogs || categoryOptions.length === 0 || categoryPublicIds.length === 0} type="submit">
          {busy ? 'Guardando…' : submitLabel}
        </button>
      </div>
    </form>
  )
}
