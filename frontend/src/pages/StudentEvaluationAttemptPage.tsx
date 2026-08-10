import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { answerEvaluation, getEvaluationAttempt, submitEvaluation } from '../features/development/api/developmentApi'
import type { EvaluationAttempt, EvaluationQuestion, EvaluationSubmission } from '../features/development/types/development'
import { useToast } from '../shared/components/ToastProvider'

function remaining(expiresAt: string | null) {
  if (!expiresAt) return null
  return Math.max(0, Math.ceil((new Date(expiresAt).getTime() - Date.now()) / 60000))
}

export function StudentEvaluationAttemptPage() {
  const { attemptPublicId = '' } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const [attempt, setAttempt] = useState<EvaluationAttempt | null>(null)
  const [submission, setSubmission] = useState<EvaluationSubmission | null>(null)
  const [selected, setSelected] = useState<string[]>([])
  const [pairs, setPairs] = useState<Record<string, string>>({})
  const [text, setText] = useState('')
  const [saving, setSaving] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [, tick] = useState(0)

  const load = useCallback(async () => {
    try {
      setAttempt(await getEvaluationAttempt(attemptPublicId))
    } catch (error) {
      toast.error('No fue posible cargar la evaluación', error instanceof Error ? error.message : undefined)
    }
  }, [attemptPublicId, toast])

  useEffect(() => { void load() }, [load])
  useEffect(() => {
    const timer = window.setInterval(() => tick((value) => value + 1), 30000)
    return () => window.clearInterval(timer)
  }, [])

  const question = attempt?.currentQuestion
  useEffect(() => {
    setSelected([])
    setPairs({})
    setText('')
  }, [question?.attemptQuestionPublicId])

  const minutes = remaining(attempt?.expiresAt ?? null)
  const questionById = useMemo(() => new Map(attempt?.questions.map((item) => [item.attemptQuestionPublicId, item]) ?? []), [attempt])

  function toggle(currentQuestion: EvaluationQuestion, optionId: string) {
    setSelected((current) => currentQuestion.type === 'MULTIPLE_CHOICE'
      ? (current.includes(optionId) ? current.filter((value) => value !== optionId) : [...current, optionId])
      : [optionId])
  }

  async function save() {
    if (!attempt || !question) return
    setSaving(true)
    try {
      const next = await answerEvaluation(attempt.publicId, question.attemptQuestionPublicId, {
        selectedOptionPublicIds: selected,
        matchingPairs: pairs,
        textAnswer: text,
      })
      setAttempt(next)
      setSelected([])
      setPairs({})
      setText('')
    } catch (error) {
      toast.error('No fue posible guardar la respuesta', error instanceof Error ? error.message : undefined)
    } finally {
      setSaving(false)
    }
  }

  async function submit() {
    if (!attempt) return
    setSubmitting(true)
    try {
      const result = await submitEvaluation(attempt.publicId)
      setSubmission(result)
      toast.success('Evaluación enviada', result.message)
    } catch (error) {
      toast.error('No fue posible enviar la evaluación', error instanceof Error ? error.message : undefined)
    } finally {
      setSubmitting(false)
    }
  }

  if (!attempt) return <div className="development-loading"><span/><span/><span/></div>

  if (submission) {
    return <div className="development-page evaluation-result-page">
      <section className="development-card evaluation-result-card">
        <p className="development-eyebrow">Evaluación enviada</p>
        <h1>{attempt.title}</h1>
        <p>{submission.message}</p>
        {submission.score != null && <div className="evaluation-visible-result">
          <strong>{submission.score}%</strong>
          {submission.passed != null && <span>{submission.passed ? 'Puntaje mínimo alcanzado' : 'Puntaje mínimo no alcanzado'}</span>}
        </div>}
        {submission.status === 'PENDING_REVIEW' && <div className="development-info-note"><strong>Revisión pendiente</strong><span>Algunas respuestas requieren revisión antes de que exista un resultado final.</span></div>}
      </section>

      {submission.answerReview.length > 0 && <section className="development-card evaluation-answer-review">
        <div className="development-card-heading"><div><p className="development-eyebrow">Revisión</p><h2>Respuestas de la evaluación</h2></div></div>
        <div className="evaluation-review-list">
          {submission.answerReview.map((review) => {
            const reviewedQuestion = questionById.get(review.attemptQuestionPublicId)
            if (!reviewedQuestion) return null
            const optionText = new Map(reviewedQuestion.options.map((option) => [option.publicId, option.text]))
            const rightText = new Map(reviewedQuestion.matchingRightOptions.map((option) => [option.publicId, option.text]))
            return <article key={review.attemptQuestionPublicId} className={`evaluation-review-item ${review.correct ? 'correct' : 'incorrect'}`}>
              <div className="evaluation-review-status"><strong>{review.correct ? 'Correcto' : 'Incorrecto'}</strong></div>
              <h3>{reviewedQuestion.statement}</h3>
              {review.correctOptionPublicIds.length > 0 && <p><b>Respuesta correcta:</b> {review.correctOptionPublicIds.map((id) => optionText.get(id) ?? id).join(', ')}</p>}
              {Object.keys(review.correctMatchingPairs).length > 0 && <div className="evaluation-review-pairs">
                <b>Relaciones correctas:</b>
                {Object.entries(review.correctMatchingPairs).map(([left, right]) => <span key={left}>{optionText.get(left) ?? left} → {rightText.get(right) ?? right}</span>)}
              </div>}
              {review.acceptedTextAnswers.length > 0 && <p><b>Respuesta esperada:</b> {review.acceptedTextAnswers.join(' / ')}</p>}
            </article>
          })}
        </div>
      </section>}
      <div className="development-actions"><button className="development-primary" onClick={() => navigate('/student/evaluations')}>Volver a Mis evaluaciones</button></div>
    </div>
  }

  if (attempt.status !== 'IN_PROGRESS') {
    return <section className="development-empty-state">
      <h1>{attempt.title}</h1>
      <p>{attempt.status === 'PENDING_REVIEW' ? 'Tu resultado está pendiente de revisión.' : 'Este intento ya finalizó.'}</p>
      {attempt.score != null && <strong className="evaluation-score">{attempt.score}%</strong>}
      <button className="development-primary" onClick={() => navigate('/student/evaluations')}>Volver a Mis evaluaciones</button>
    </section>
  }

  const allAnswered = attempt.questions.every((item) => item.answered)
  const answerReady = question
    ? question.type === 'OPEN_TEXT'
      ? text.trim().length > 0
      : question.type === 'MATCHING'
        ? Object.keys(pairs).length === question.options.length
        : selected.length > 0
    : false

  return <div className="development-page evaluation-attempt-page">
    <header className="formal-evaluation-header">
      <div><span className="development-badge formal">Evaluación</span><h1>{attempt.title}</h1><p>Intento {attempt.attemptNumber}{attempt.maxAttempts ? ` de ${attempt.maxAttempts}` : ''} · Puntaje mínimo {attempt.passingScore}%</p></div>
      {minutes != null && <div className={`evaluation-timer ${minutes <= 5 ? 'critical' : ''}`}><small>Tiempo restante</small><strong>{minutes} min</strong></div>}
    </header>
    {attempt.showProgress && <div className="formal-progress"><span>{attempt.questions.filter((item) => item.answered).length} de {attempt.questions.length} respondidas</span><div><i style={{ width: `${attempt.questions.length ? attempt.questions.filter((item) => item.answered).length / attempt.questions.length * 100 : 0}%` }}/></div></div>}

    {question ? <article className="practice-question-card formal-question">
      <div className="practice-question-meta">{attempt.showQuestionNumbers && <span>Pregunta {question.position} de {question.total}</span>}<span>{question.points} {question.points === 1 ? 'punto' : 'puntos'}</span></div>
      <h2>{question.statement}</h2>
      {question.codeContent && <pre><code>{question.codeContent}</code></pre>}
      {question.type === 'OPEN_TEXT'
        ? <textarea className="evaluation-text-answer" value={text} onChange={(event) => setText(event.target.value)} placeholder="Escribe tu respuesta" rows={7}/>
        : question.type === 'MATCHING'
          ? <div className="matching-grid">{question.options.map((option) => <label key={option.publicId}><span>{option.text}</span><select value={pairs[option.publicId] ?? ''} onChange={(event) => setPairs((current) => ({ ...current, [option.publicId]: event.target.value }))}><option value="">Selecciona una relación</option>{question.matchingRightOptions.map((right) => <option key={right.publicId} value={right.publicId}>{right.text}</option>)}</select></label>)}</div>
          : <div className="practice-options">{question.options.map((option, index) => <button key={option.publicId} className={selected.includes(option.publicId) ? 'selected' : ''} onClick={() => toggle(question, option.publicId)}><span>{String.fromCharCode(65 + index)}</span><strong>{option.text}</strong></button>)}</div>}
      <div className="practice-question-actions"><button className="development-primary" disabled={!answerReady || saving} onClick={() => void save()}>{saving ? 'Guardando…' : 'Guardar y continuar'}</button></div>
    </article> : <section className="development-card evaluation-ready-submit"><h2>Ya respondiste todas las preguntas</h2><p>Revisa que estés listo antes de enviar. Después del envío este intento quedará cerrado.</p><button className="development-primary" disabled={!allAnswered || submitting} onClick={() => void submit()}>{submitting ? 'Enviando…' : 'Enviar evaluación'}</button></section>}
  </div>
}
