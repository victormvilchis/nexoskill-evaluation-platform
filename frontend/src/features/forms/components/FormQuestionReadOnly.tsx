import { JavaCodePreview } from '../../questions/components/JavaCodePanel'
import { Icon } from '../../../shared/components/Icon'
import type { FormQuestionContent } from '../../../shared/types/forms'

interface FormQuestionReadOnlyProps {
  question: FormQuestionContent
  number?: number
  points?: number
  required?: boolean
  status?: 'ACTIVE' | 'ARCHIVED' | 'DELETED'
  variant?: 'builder' | 'preview'
}

function acceptedAnswers(value?: string) {
  if (!value) return []
  try {
    const parsed: unknown = JSON.parse(value)
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string') : []
  } catch {
    return []
  }
}

function classificationValue(value?: string) {
  return value?.trim() || 'N/A'
}

export function FormQuestionReadOnly({
  question,
  number,
  points,
  required,
  status = 'ACTIVE',
  variant = 'builder'
}: FormQuestionReadOnlyProps) {
  const answers = acceptedAnswers(question.acceptedAnswersJson)
  const scope = question.contentScope === 'GLOBAL' ? 'Global' : question.contentScope === 'ORGANIZATION' ? 'Organizacional' : 'N/A'

  return (
    <article className={`form-question-readonly form-question-readonly--${variant}`}>
      <div className="form-question-readonly__heading">
        <div className="form-question-readonly__number">{number ?? <Icon name="questions" size={18} />}</div>
        <div>
          <span className="form-question-readonly__origin">Pregunta del Banco de preguntas</span>
          <h3>{question.statement}</h3>
        </div>
        <div className="form-question-readonly__badges">
          {required !== undefined && <span>{required ? 'Obligatoria' : 'Opcional'}</span>}
          {points !== undefined && <span>{points} {points === 1 ? 'punto' : 'puntos'}</span>}
        </div>
      </div>

      {question.promptMedia && (
        <figure className="form-question-readonly__media">
          <img src={question.promptMedia.url} alt={question.promptMedia.originalName} />
          <figcaption>{question.promptMedia.originalName}</figcaption>
        </figure>
      )}

      {question.codeContent && (
        <div className="form-question-readonly__code">
          <JavaCodePreview code={question.codeContent} />
        </div>
      )}

      <div className="form-question-readonly__classification">
        <div><span>Tipo</span><strong>{classificationValue(question.typeName)}</strong></div>
        <div><span>Categoría</span><strong>{question.categoryNames.length ? question.categoryNames.join(', ') : 'N/A'}</strong></div>
        <div><span>Tecnología</span><strong>{classificationValue(question.technologyName)}</strong></div>
        <div><span>Seniority</span><strong>{classificationValue(question.levelCode)}</strong></div>
        <div><span>Dificultad</span><strong>{classificationValue(question.difficultyName)}</strong></div>
        <div><span>Alcance</span><strong>{scope}</strong></div>
        <div><span>Organización</span><strong>{classificationValue(question.organizationName)}</strong></div>
        <div><span>Estado</span><strong>{status === 'ACTIVE' ? 'Activa' : status === 'ARCHIVED' ? 'Inactiva' : 'Eliminada'}</strong></div>
        <div><span>Puntos</span><strong>{points ?? 'N/A'}</strong></div>
      </div>

      {question.options.length > 0 && question.typeCode !== 'MATCHING' && (
        <div className="form-question-readonly__options" aria-label="Opciones de respuesta">
          {question.options.map((option, index) => (
            <div className={option.correct ? 'is-correct' : ''} key={option.publicId}>
              <span className="form-question-readonly__option-marker">{option.correct ? <Icon name="check" size={16} /> : String.fromCharCode(65 + index)}</span>
              <div>
                {option.text && <p>{option.text}</p>}
                {option.media && <img src={option.media.url} alt={option.media.originalName} />}
                {option.correct && <span className="form-question-readonly__correct-label">Respuesta correcta</span>}
                {option.feedback && <small>{option.feedback}</small>}
              </div>
            </div>
          ))}
        </div>
      )}

      {question.typeCode === 'MATCHING' && (
        <div className="form-question-readonly__matching" aria-label="Relaciones correctas">
          {question.options.map((option, index) => (
            <div key={option.publicId}>
              <span>{index + 1}</span>
              <div>{option.text && <p>{option.text}</p>}{option.media && <img src={option.media.url} alt={option.media.originalName} />}</div>
              <strong>↔</strong>
              <div>{option.matchText && <p>{option.matchText}</p>}{option.matchMedia && <img src={option.matchMedia.url} alt={option.matchMedia.originalName} />}</div>
            </div>
          ))}
        </div>
      )}

      {question.typeCode === 'OPEN_TEXT' && (
        <div className="form-question-readonly__open-answer">
          <strong>Respuesta abierta</strong>
          {answers.length > 0
            ? <div>{answers.map(answer => <span key={answer}>{answer}</span>)}</div>
            : <p>La respuesta se revisará conforme a la configuración del Banco de preguntas.</p>}
        </div>
      )}

      {question.explanation && (
        <div className="form-question-readonly__explanation">
          <strong>Explicación</strong>
          <p>{question.explanation}</p>
        </div>
      )}
    </article>
  )
}
