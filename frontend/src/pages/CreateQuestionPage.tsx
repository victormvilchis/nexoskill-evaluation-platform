import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  createQuestion,
  getQuestionCatalogs
} from '../features/questions/api/questionApi'
import { ApiRequestError } from '../shared/api/apiClient'
import type {
  CreateQuestionOptionPayload,
  QuestionCatalogs,
  QuestionDifficultyCode,
  QuestionTypeCode
} from '../shared/types/questions'

interface EditableOption extends CreateQuestionOptionPayload {
  id: string
}

function newOption(text = '', correct = false): EditableOption {
  return {
    id: crypto.randomUUID(),
    text,
    correct
  }
}

function initialOptions(type: QuestionTypeCode): EditableOption[] {
  if (type === 'TRUE_FALSE') {
    return [newOption('Verdadero', true), newOption('Falso', false)]
  }
  return [newOption(), newOption()]
}

export function CreateQuestionPage() {
  const navigate = useNavigate()
  const [catalogs, setCatalogs] = useState<QuestionCatalogs | null>(null)
  const [typeCode, setTypeCode] =
    useState<QuestionTypeCode>('SINGLE_CHOICE')
  const [difficultyCode, setDifficultyCode] =
    useState<QuestionDifficultyCode>('BASIC')
  const [categoryPublicId, setCategoryPublicId] = useState('')
  const [statement, setStatement] = useState('')
  const [explanation, setExplanation] = useState('')
  const [options, setOptions] = useState<EditableOption[]>(
    initialOptions('SINGLE_CHOICE')
  )
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  useEffect(() => {
    getQuestionCatalogs()
      .then((response) => {
        setCatalogs(response)
        const category = response.categories[0]
        const type = response.types[0]
        const difficulty = response.difficulties[0]
        if (category) setCategoryPublicId(category.publicId)
        if (type) setTypeCode(type.code as QuestionTypeCode)
        if (difficulty) {
          setDifficultyCode(difficulty.code as QuestionDifficultyCode)
        }
      })
      .catch(() => setError('No fue posible cargar los catálogos.'))
  }, [])

  const correctCount = useMemo(
    () => options.filter((option) => option.correct).length,
    [options]
  )

  function handleTypeChange(nextType: QuestionTypeCode) {
    setTypeCode(nextType)
    setOptions(initialOptions(nextType))
  }

  function updateOption(
    id: string,
    field: 'text' | 'correct',
    value: string | boolean
  ) {
    setOptions((current) =>
      current.map((option) => {
        if (option.id !== id) return option
        if (field === 'text') {
          return { ...option, text: value as string }
        }

        if (typeCode === 'SINGLE_CHOICE' || typeCode === 'TRUE_FALSE') {
          return option
        }
        return { ...option, correct: value as boolean }
      })
    )

    if (
      field === 'correct' &&
      (typeCode === 'SINGLE_CHOICE' || typeCode === 'TRUE_FALSE')
    ) {
      setOptions((current) =>
        current.map((option) => ({
          ...option,
          correct: option.id === id
        }))
      )
    }
  }

  function addOption() {
    if (typeCode === 'TRUE_FALSE' || options.length >= 10) return
    setOptions((current) => [...current, newOption()])
  }

  function removeOption(id: string) {
    if (typeCode === 'TRUE_FALSE' || options.length <= 2) return
    setOptions((current) => current.filter((option) => option.id !== id))
  }

  function validateClient() {
    if (!statement.trim()) return 'El enunciado es obligatorio.'
    if (!categoryPublicId) return 'Selecciona una categoría.'
    if (options.some((option) => !option.text.trim())) {
      return 'Todas las opciones deben tener contenido.'
    }
    if (typeCode === 'SINGLE_CHOICE' && correctCount !== 1) {
      return 'Selecciona exactamente una respuesta correcta.'
    }
    if (
      typeCode === 'MULTIPLE_CHOICE' &&
      (correctCount < 2 || correctCount >= options.length)
    ) {
      return 'Selecciona al menos dos respuestas correctas y una incorrecta.'
    }
    if (typeCode === 'TRUE_FALSE' && correctCount !== 1) {
      return 'Selecciona Verdadero o Falso como respuesta correcta.'
    }
    return null
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setFieldErrors({})

    const validationError = validateClient()
    if (validationError) {
      setError(validationError)
      return
    }

    setSubmitting(true)
    try {
      const created = await createQuestion({
        typeCode,
        difficultyCode,
        categoryPublicId,
        statement: statement.trim(),
        explanation: explanation.trim() || undefined,
        options: options.map(({ text, correct }) => ({
          text: text.trim(),
          correct
        }))
      })
      navigate(`/admin/questions/${created.publicId}?created=1`, {
        replace: true
      })
    } catch (requestError) {
      if (requestError instanceof ApiRequestError) {
        setError(requestError.message)
        setFieldErrors(requestError.fieldErrors ?? {})
      } else {
        setError('No fue posible crear la pregunta.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="content-page narrow-content">
      <div className="page-heading">
        <div>
          <p className="eyebrow">Banco de preguntas</p>
          <h1>Crear pregunta</h1>
          <p className="muted">
            La pregunta se guardará como borrador y podrá revisarse antes de su
            publicación.
          </p>
        </div>
        <Link className="secondary-button button-link" to="/admin/questions">
          Volver
        </Link>
      </div>

      {catalogs && catalogs.categories.length === 0 && (
        <div className="warning-message">
          No existen categorías activas. Crea o activa una categoría antes de
          registrar preguntas. {' '}
          <Link to="/admin/question-categories">Administrar categorías</Link>
        </div>
      )}

      <form
        className="entity-form question-form"
        onSubmit={(event) => void handleSubmit(event)}
      >
        {error && <div className="error-message form-wide">{error}</div>}

        <div className="form-field">
          <label htmlFor="typeCode">Tipo de pregunta</label>
          <select
            id="typeCode"
            value={typeCode}
            required
            onChange={(event) =>
              handleTypeChange(event.target.value as QuestionTypeCode)
            }
          >
            {catalogs?.types.map((type) => (
              <option key={type.code} value={type.code}>
                {type.name}
              </option>
            ))}
          </select>
          {fieldErrors.typeCode && (
            <small className="field-error">{fieldErrors.typeCode}</small>
          )}
        </div>

        <div className="form-field">
          <label htmlFor="difficultyCode">Dificultad</label>
          <select
            id="difficultyCode"
            value={difficultyCode}
            required
            onChange={(event) =>
              setDifficultyCode(
                event.target.value as QuestionDifficultyCode
              )
            }
          >
            {catalogs?.difficulties.map((difficulty) => (
              <option key={difficulty.code} value={difficulty.code}>
                {difficulty.name}
              </option>
            ))}
          </select>
        </div>

        <div className="form-field form-wide">
          <label htmlFor="categoryPublicId">Categoría</label>
          <select
            id="categoryPublicId"
            value={categoryPublicId}
            required
            onChange={(event) => setCategoryPublicId(event.target.value)}
          >
            <option value="" disabled>
              Selecciona una categoría
            </option>
            {catalogs?.categories.map((category) => (
              <option key={category.publicId} value={category.publicId}>
                {category.name}
              </option>
            ))}
          </select>
        </div>

        <div className="form-field form-wide">
          <label htmlFor="statement">Enunciado</label>
          <textarea
            id="statement"
            value={statement}
            rows={6}
            maxLength={10000}
            required
            placeholder="Escribe la pregunta completa."
            onChange={(event) => setStatement(event.target.value)}
          />
          <small>{statement.length.toLocaleString('es-MX')} / 10,000</small>
          {fieldErrors.statement && (
            <small className="field-error">{fieldErrors.statement}</small>
          )}
        </div>

        <fieldset className="question-options-fieldset form-wide">
          <div className="question-options-heading">
            <div>
              <legend>Opciones de respuesta</legend>
              <p className="muted">
                {typeCode === 'MULTIPLE_CHOICE'
                  ? 'Marca todas las respuestas correctas.'
                  : 'Marca una sola respuesta correcta.'}
              </p>
            </div>
            {typeCode !== 'TRUE_FALSE' && (
              <button
                className="secondary-button"
                type="button"
                disabled={options.length >= 10}
                onClick={addOption}
              >
                Agregar opción
              </button>
            )}
          </div>

          <div className="question-option-list">
            {options.map((option, index) => (
              <div className="question-option-row" key={option.id}>
                <label className="option-correct-control">
                  <input
                    type={
                      typeCode === 'MULTIPLE_CHOICE' ? 'checkbox' : 'radio'
                    }
                    name="correctOption"
                    checked={option.correct}
                    onChange={(event) =>
                      updateOption(option.id, 'correct', event.target.checked)
                    }
                  />
                  Correcta
                </label>
                <div className="form-field">
                  <label htmlFor={`option-${option.id}`}>
                    Opción {index + 1}
                  </label>
                  <textarea
                    id={`option-${option.id}`}
                    value={option.text}
                    rows={2}
                    maxLength={2000}
                    readOnly={typeCode === 'TRUE_FALSE'}
                    required
                    onChange={(event) =>
                      updateOption(option.id, 'text', event.target.value)
                    }
                  />
                </div>
                {typeCode !== 'TRUE_FALSE' && (
                  <button
                    className="danger-button compact-button"
                    type="button"
                    disabled={options.length <= 2}
                    onClick={() => removeOption(option.id)}
                  >
                    Quitar
                  </button>
                )}
              </div>
            ))}
          </div>
        </fieldset>

        <div className="form-field form-wide">
          <label htmlFor="explanation">Explicación</label>
          <textarea
            id="explanation"
            value={explanation}
            rows={5}
            maxLength={10000}
            placeholder="Explica por qué la respuesta es correcta. Esta información no se mostrará durante el examen."
            onChange={(event) => setExplanation(event.target.value)}
          />
          <small>{explanation.length.toLocaleString('es-MX')} / 10,000</small>
        </div>

        <div className="form-actions form-wide">
          <Link className="secondary-button button-link" to="/admin/questions">
            Cancelar
          </Link>
          <button
            className="primary-button"
            type="submit"
            disabled={submitting || !catalogs || catalogs.categories.length === 0}
          >
            {submitting ? 'Guardando…' : 'Guardar borrador'}
          </button>
        </div>
      </form>
    </main>
  )
}
