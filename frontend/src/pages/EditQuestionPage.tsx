import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { getQuestion, getQuestionCatalogs, updateQuestion } from '../features/questions/api/questionApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { Icon } from '../shared/components/Icon'
import { useToast } from '../shared/components/ToastProvider'
import type {
  CreateQuestionOptionPayload,
  QuestionCatalogs,
  QuestionDetail,
  QuestionDifficultyCode,
  QuestionTypeCode
} from '../shared/types/questions'

interface EditableOption extends CreateQuestionOptionPayload { id: string }

function newOption(text = '', correct = false): EditableOption {
  return { id: crypto.randomUUID(), text, correct }
}

function initialOptions(type: QuestionTypeCode): EditableOption[] {
  return type === 'TRUE_FALSE'
    ? [newOption('Verdadero', true), newOption('Falso', false)]
    : [newOption(), newOption()]
}

export function EditQuestionPage() {
  const { publicId } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const [question, setQuestion] = useState<QuestionDetail | null>(null)
  const [catalogs, setCatalogs] = useState<QuestionCatalogs | null>(null)
  const [typeCode, setTypeCode] = useState<QuestionTypeCode>('SINGLE_CHOICE')
  const [difficultyCode, setDifficultyCode] = useState<QuestionDifficultyCode>('BASIC')
  const [categoryPublicId, setCategoryPublicId] = useState('')
  const [statement, setStatement] = useState('')
  const [explanation, setExplanation] = useState('')
  const [options, setOptions] = useState<EditableOption[]>([])
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [criticalError, setCriticalError] = useState<string | null>(null)

  useEffect(() => {
    if (!publicId) {
      setCriticalError('No se indicó la pregunta.')
      setLoading(false)
      return
    }

    Promise.all([getQuestionCatalogs(), getQuestion(publicId)])
      .then(([catalogResponse, questionResponse]) => {
        const categoryExists = catalogResponse.categories.some(
          (category) => category.publicId === questionResponse.categoryPublicId
        )
        setCatalogs(categoryExists ? catalogResponse : {
          ...catalogResponse,
          categories: [
            ...catalogResponse.categories,
            {
              publicId: questionResponse.categoryPublicId,
              code: 'INACTIVE',
              name: `${questionResponse.categoryName} (inactiva)`,
              description: null,
              status: 'INACTIVE',
              createdAt: questionResponse.createdAt
            }
          ]
        })
        setQuestion(questionResponse)
        setTypeCode(questionResponse.typeCode)
        setDifficultyCode(questionResponse.difficultyCode)
        setCategoryPublicId(questionResponse.categoryPublicId)
        setStatement(questionResponse.statement)
        setExplanation(questionResponse.explanation ?? '')
        setOptions(questionResponse.options.map((option) => ({
          id: option.publicId,
          text: option.text,
          correct: option.correct
        })))
      })
      .catch((requestError) => {
        const message = requestError instanceof ApiRequestError
          ? requestError.message
          : 'No fue posible cargar la pregunta.'
        setCriticalError(message)
        toast.error('No fue posible cargar la pregunta', message)
      })
      .finally(() => setLoading(false))
  }, [publicId, toast])

  const correctCount = useMemo(
    () => options.filter((option) => option.correct).length,
    [options]
  )

  function handleTypeChange(nextType: QuestionTypeCode) {
    setTypeCode(nextType)
    setOptions(initialOptions(nextType))
  }

  function updateOption(id: string, field: 'text' | 'correct', value: string | boolean) {
    if (field === 'correct' && typeCode !== 'MULTIPLE_CHOICE') {
      setOptions((current) => current.map((option) => ({ ...option, correct: option.id === id })))
      return
    }
    setOptions((current) => current.map((option) =>
      option.id !== id
        ? option
        : field === 'text'
          ? { ...option, text: value as string }
          : { ...option, correct: value as boolean }
    ))
  }

  function addOption() {
    if (typeCode !== 'TRUE_FALSE' && options.length < 10) {
      setOptions((current) => [...current, newOption()])
    }
  }

  function removeOption(id: string) {
    if (typeCode !== 'TRUE_FALSE' && options.length > 2) {
      setOptions((current) => current.filter((option) => option.id !== id))
    }
  }

  function validationError() {
    if (!statement.trim()) return 'El enunciado es obligatorio.'
    if (!categoryPublicId) return 'Selecciona una categoría.'
    if (options.some((option) => !option.text.trim())) return 'Todas las opciones deben tener contenido.'
    if (typeCode === 'SINGLE_CHOICE' && correctCount !== 1) return 'Selecciona exactamente una respuesta correcta.'
    if (typeCode === 'MULTIPLE_CHOICE' && (options.length < 3 || correctCount < 2 || correctCount >= options.length)) {
      return 'Configura al menos tres opciones, dos correctas y una incorrecta.'
    }
    if (typeCode === 'TRUE_FALSE' && correctCount !== 1) return 'Selecciona Verdadero o Falso como respuesta correcta.'
    return null
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!question || !publicId) return
    const invalid = validationError()
    if (invalid) {
      toast.warning('Revisa la información', invalid)
      return
    }

    setSubmitting(true)
    try {
      const updated = await updateQuestion(publicId, {
        typeCode,
        difficultyCode,
        categoryPublicId,
        statement: statement.trim(),
        explanation: explanation.trim() || undefined,
        expectedEntityVersion: question.entityVersion,
        options: options.map(({ text, correct }) => ({ text: text.trim(), correct }))
      })
      navigate(`/admin/questions/${updated.publicId}?updated=1`, { replace: true })
    } catch (requestError) {
      const message = requestError instanceof ApiRequestError
        ? requestError.message
        : 'No fue posible actualizar la pregunta.'
      if (requestError instanceof ApiRequestError && requestError.code === 'QUESTION_CONCURRENT_MODIFICATION') {
        toast.warning('La pregunta cambió en otra sesión', message)
      } else {
        toast.error('No fue posible guardar los cambios', message)
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return <main className="content-page"><p className="muted">Cargando pregunta…</p></main>
  }

  return (
    <main className="content-page narrow-content">
      <div className="page-heading">
        <div>
          <p className="eyebrow">Banco de preguntas</p>
          <h1>Editar pregunta</h1>
          <p className="muted">Al guardar se publicará una nueva versión y se conservará el historial.</p>
        </div>
        <Link className="secondary-button button-link" to={publicId ? `/admin/questions/${publicId}` : '/admin/questions'}>Cancelar</Link>
      </div>

      {criticalError && <div className="error-message">{criticalError}</div>}

      {question && (
        <form
        className="entity-form question-form"
        onSubmit={(event) => void handleSubmit(event)}
        onKeyDown={(event) => {
          if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
            event.preventDefault()
            event.currentTarget.requestSubmit()
          }
        }}
      >
          <section className="form-section form-wide">
            <div className="form-section-heading">
              <span className="form-section-number">1</span>
              <div><h2>Clasificación</h2><p>Actualiza la organización de la pregunta.</p></div>
            </div>
            <div className="form-grid-three">
              <div className="form-field">
                <label htmlFor="typeCode">Tipo</label>
                <select id="typeCode" value={typeCode} onChange={(event) => handleTypeChange(event.target.value as QuestionTypeCode)}>
                  {catalogs?.types.map((type) => <option key={type.code} value={type.code}>{type.name}</option>)}
                </select>
              </div>
              <div className="form-field">
                <label htmlFor="difficultyCode">Dificultad</label>
                <select id="difficultyCode" value={difficultyCode} onChange={(event) => setDifficultyCode(event.target.value as QuestionDifficultyCode)}>
                  {catalogs?.difficulties.map((difficulty) => <option key={difficulty.code} value={difficulty.code}>{difficulty.name}</option>)}
                </select>
              </div>
              <div className="form-field">
                <label htmlFor="categoryPublicId">Categoría</label>
                <select id="categoryPublicId" value={categoryPublicId} onChange={(event) => setCategoryPublicId(event.target.value)}>
                  {catalogs?.categories.map((category) => <option key={category.publicId} value={category.publicId}>{category.name}</option>)}
                </select>
              </div>
            </div>
          </section>

          <section className="form-section form-wide">
            <div className="form-section-heading">
              <span className="form-section-number">2</span>
              <div><h2>Contenido</h2><p>Edita el enunciado y las respuestas.</p></div>
            </div>
            <div className="form-field">
              <label htmlFor="statement">Enunciado</label>
              <textarea id="statement" rows={5} maxLength={10000} value={statement} onChange={(event) => setStatement(event.target.value)} />
              <small className="field-counter">{statement.length.toLocaleString('es-MX')} / 10,000</small>
            </div>

            <fieldset className="question-options-fieldset">
              <div className="question-options-heading">
                <div><legend>Opciones</legend><p>Configura el contenido y marca las respuestas correctas.</p></div>
                {typeCode !== 'TRUE_FALSE' && (
                  <button className="secondary-button" type="button" disabled={options.length >= 10} onClick={addOption}>
                    <Icon name="plus" size={16} />Agregar opción
                  </button>
                )}
              </div>
              <div className="question-option-list">
                {options.map((option, index) => (
                  <div className="question-option-row" key={option.id}>
                    <label className="option-correct-control">
                      <input type={typeCode === 'MULTIPLE_CHOICE' ? 'checkbox' : 'radio'} name="correctOption" checked={option.correct} onChange={(event) => updateOption(option.id, 'correct', event.target.checked)} />
                      <span>{option.correct ? 'Correcta' : 'Marcar'}</span>
                    </label>
                    <div className="form-field">
                      <label htmlFor={`edit-option-${option.id}`}>Opción {index + 1}</label>
                      <textarea id={`edit-option-${option.id}`} rows={2} maxLength={2000} readOnly={typeCode === 'TRUE_FALSE'} value={option.text} onChange={(event) => updateOption(option.id, 'text', event.target.value)} />
                    </div>
                    {typeCode !== 'TRUE_FALSE' && (
                      <button aria-label={`Quitar opción ${index + 1}`} className="icon-button danger-icon-button" type="button" disabled={options.length <= 2} onClick={() => removeOption(option.id)}>
                        <Icon name="close" size={16} />
                      </button>
                    )}
                  </div>
                ))}
              </div>
            </fieldset>
          </section>

          <details className="form-section optional-form-section form-wide">
            <summary>
              <span><Icon name="info" size={17} />Explicación opcional</span>
              <small>Puedes agregar contexto para la retroalimentación.</small>
            </summary>
            <div className="form-field optional-form-content">
              <label htmlFor="explanation">Explicación</label>
              <textarea id="explanation" rows={4} maxLength={10000} value={explanation} onChange={(event) => setExplanation(event.target.value)} />
            </div>
          </details>

          <div className="form-actions form-wide sticky-form-actions">
            <span className="auto-publish-note"><Icon name="check" size={15} />Nueva versión automática · Ctrl + Enter para guardar</span>
            <div>
              <Link className="secondary-button button-link" to={`/admin/questions/${question.publicId}`}>Cancelar</Link>
              <button className="primary-button" type="submit" disabled={submitting}>
                {submitting ? 'Publicando…' : 'Guardar y publicar'}
              </button>
            </div>
          </div>
        </form>
      )}
    </main>
  )
}
