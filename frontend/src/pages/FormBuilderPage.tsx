import { FormEvent, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  createForm,
  getForm,
  getFormCategoryOptions,
  getFormOrganizations,
  getFormQuestionOptions,
  updateForm
} from '../features/forms/api/formApi'
import { useAuth } from '../features/authentication/context/AuthContext'
import { ApiRequestError } from '../shared/api/apiClient'
import { BackButton } from '../shared/components/BackButton'
import { ConfirmDialog } from '../shared/components/ConfirmDialog'
import { FullScreenDialog } from '../shared/components/FullScreenDialog'
import { FormActions } from '../shared/components/FormActions'
import { Icon } from '../shared/components/Icon'
import { ResourceSearchField } from '../shared/components/ResourceFilters'
import { SelectField } from '../shared/components/SelectField'
import { TablePagination } from '../shared/components/TablePagination'
import { useDebouncedValue } from '../shared/hooks/useDebouncedValue'
import { useSaveNavigation } from '../shared/hooks/useSaveNavigation'
import { useToast } from '../shared/components/ToastProvider'
import { FormQuestionReadOnly } from '../features/forms/components/FormQuestionReadOnly'
import type {
  FormCategoryOption,
  FormContentMode,
  FormContentScope,
  FormDetail,
  FormMode,
  FormPayload,
  FormPoolItem,
  FormQuestionItem,
  FormQuestionOption,
  FormQuestionOptionPage,
  FormOrganizationOption
} from '../shared/types/forms'
import type { PageSize } from '../shared/types/pagination'

const EMPTY_QUESTION_PAGE: FormQuestionOptionPage = {
  content: [], page: 0, size: 10, totalElements: 0, totalPages: 0
}

function newOperationId() {
  return typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function initialPayload(globalAdministrator: boolean): FormPayload {
  return {
    title: '',
    description: '',
    modeCode: 'ASSESSMENT',
    passingScore: 80,
    retryUntilPassed: true,
    acceptResponses: false,
    showResults: true,
    showCorrectAnswers: false,
    randomizeQuestions: false,
    randomizeOptions: false,
    showProgress: true,
    hideQuestionNumbers: false,
    allowSaveResume: true,
    oneActiveAttempt: true,
    thankYouMessage: 'Gracias por completar el formulario.',
    contentScope: globalAdministrator ? 'GLOBAL' : 'ORGANIZATION',
    contentMode: 'MANUAL',
    operationId: newOperationId(),
    questions: [],
    pools: []
  }
}

function Toggle({ checked, onChange, title, description, disabled = false }: {
  checked: boolean
  onChange: (value: boolean) => void
  title: string
  description?: string
  disabled?: boolean
}) {
  return <label className="ns-toggle-row">
    <input type="checkbox" checked={checked} disabled={disabled} onChange={event => onChange(event.target.checked)} />
    <span className="ns-toggle-control" aria-hidden="true" />
    <span className="ns-toggle-copy"><strong>{title}</strong>{description && <small>{description}</small>}</span>
  </label>
}

type PendingChange =
  | { type: 'MODE'; mode: FormContentMode }
  | { type: 'TARGET'; scope: FormContentScope; organizationPublicId?: string }

type FormBuilderTab = 'GENERAL' | 'CONTENT' | 'CONFIGURATION'

export function FormBuilderPage({ readOnly = false }: { readOnly?: boolean }) {
  const { id } = useParams()
  const editing = Boolean(id)
  const navigate = useNavigate()
  const { user } = useAuth()
  const globalAdministrator = Boolean(user?.roles.includes('ADMINISTRATOR'))
  const completeSave = useSaveNavigation('/admin/forms')
  const toast = useToast()
  const [model, setModel] = useState<FormPayload>(() => initialPayload(globalAdministrator))
  const [selectedQuestions, setSelectedQuestions] = useState<FormQuestionItem[]>([])
  const [configuredPools, setConfiguredPools] = useState<FormPoolItem[]>([])
  const [organizations, setOrganizations] = useState<FormOrganizationOption[]>([])
  const [organizationsLoading, setOrganizationsLoading] = useState(false)
  const [organizationsLoaded, setOrganizationsLoaded] = useState(false)
  const [organizationsError, setOrganizationsError] = useState('')
  const [organizationsReloadKey, setOrganizationsReloadKey] = useState(0)
  const [categories, setCategories] = useState<FormCategoryOption[]>([])
  const [questionPage, setQuestionPage] = useState<FormQuestionOptionPage>(EMPTY_QUESTION_PAGE)
  const [questionQuery, setQuestionQuery] = useState('')
  const debouncedQuestionQuery = useDebouncedValue(questionQuery, 300)
  const [questionPageIndex, setQuestionPageIndex] = useState(0)
  const [questionPageSize, setQuestionPageSize] = useState<PageSize>(10)
  const [loadingOptions, setLoadingOptions] = useState(false)
  const [saving, setSaving] = useState(false)
  const [loading, setLoading] = useState(editing)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [pendingChange, setPendingChange] = useState<PendingChange | null>(null)
  const [bankOpen, setBankOpen] = useState(false)
  const [previewQuestion, setPreviewQuestion] = useState<FormQuestionOption | null>(null)
  const [selectedQuestionPreview, setSelectedQuestionPreview] = useState<FormQuestionItem | null>(null)
  const [formPreviewOpen, setFormPreviewOpen] = useState(false)
  const [removeCandidate, setRemoveCandidate] = useState<FormQuestionItem | null>(null)
  const selectedQuestionIdsRef = useRef(new Set<string>())
  const [activeTab, setActiveTab] = useState<FormBuilderTab>('GENERAL')
  const [questionCategoryPublicId, setQuestionCategoryPublicId] = useState('')

  useEffect(() => {
    selectedQuestionIdsRef.current = new Set(selectedQuestions.map(question => question.questionPublicId))
  }, [selectedQuestions])

  useEffect(() => {
    if (!editing && globalAdministrator && model.title === '' && model.contentScope === 'ORGANIZATION'
      && !model.organizationPublicId && selectedQuestions.length === 0 && configuredPools.length === 0) {
      setModel(current => ({ ...current, contentScope: 'GLOBAL' }))
    }
  }, [configuredPools.length, editing, globalAdministrator, model.contentScope, model.organizationPublicId, model.title, selectedQuestions.length])

  useEffect(() => {
    if (!globalAdministrator || editing || model.contentScope !== 'ORGANIZATION'
      || organizationsLoaded) return
    const controller = new AbortController()
    setOrganizationsLoading(true)
    setOrganizationsError('')
    getFormOrganizations(controller.signal)
      .then((available) => {
        setOrganizations(available)
        setOrganizationsLoaded(true)
      })
      .catch((requestError) => {
        if (controller.signal.aborted) return
        setOrganizationsError(requestError instanceof ApiRequestError
          ? requestError.message
          : 'No fue posible cargar las organizaciones disponibles.')
      })
      .finally(() => {
        if (!controller.signal.aborted) setOrganizationsLoading(false)
      })
    return () => controller.abort()
  }, [editing, globalAdministrator, model.contentScope, organizationsLoaded, organizationsReloadKey])

  function retryOrganizations() {
    setOrganizationsLoaded(false)
    setOrganizationsError('')
    setOrganizationsReloadKey(value => value + 1)
  }

  useEffect(() => {
    if (!id) return
    const controller = new AbortController()
    setLoading(true)
    getForm(id, controller.signal)
      .then((form: FormDetail) => {
        setModel({
          title: form.title,
          description: form.description ?? '',
          modeCode: form.modeCode,
          passingScore: form.passingScore,
          maxAttempts: form.maxAttempts,
          retryUntilPassed: form.retryUntilPassed,
          acceptResponses: form.acceptResponses,
          startsAt: form.startsAt,
          endsAt: form.endsAt,
          durationMinutes: form.durationMinutes,
          showResults: form.showResults,
          showCorrectAnswers: form.showCorrectAnswers,
          randomizeQuestions: form.randomizeQuestions,
          randomizeOptions: form.randomizeOptions,
          showProgress: form.showProgress,
          hideQuestionNumbers: form.hideQuestionNumbers,
          allowSaveResume: form.allowSaveResume,
          oneActiveAttempt: form.oneActiveAttempt,
          thankYouMessage: form.thankYouMessage ?? '',
          version: form.version,
          contentScope: form.contentScope,
          organizationPublicId: form.contentScope === 'ORGANIZATION' ? form.ownerOrganizationPublicId : undefined,
          contentMode: form.contentMode,
          operationId: newOperationId(),
          questions: form.questions.map(question => ({
            questionPublicId: question.questionPublicId,
            order: question.order,
            points: question.points,
            required: question.required
          })),
          pools: form.pools.map(pool => ({
            publicId: pool.publicId,
            sourceType: 'CATEGORY',
            sourcePublicId: pool.categoryPublicId,
            questionCount: pool.questionCount,
            difficultyCode: pool.difficultyCode,
            order: pool.order
          }))
        })
        setSelectedQuestions(form.questions)
        setConfiguredPools(form.pools)
      })
      .catch(requestError => {
        if (controller.signal.aborted) return
        setError(requestError instanceof ApiRequestError ? requestError.message : 'No fue posible cargar el formulario.')
      })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [id])

  const targetReady = model.contentScope === 'GLOBAL'
    || !globalAdministrator
    || Boolean(model.organizationPublicId)

  const headerIssues = useMemo(() => {
    const issues: string[] = []
    if (model.title.trim().length < 3) issues.push('Agrega un título de al menos 3 caracteres.')
    if (model.passingScore < 0 || model.passingScore > 100) issues.push('El puntaje mínimo debe estar entre 0 y 100.')
    if (model.durationMinutes !== undefined && model.durationMinutes < 1) issues.push('La duración debe ser mayor que cero.')
    if (globalAdministrator && !editing && model.contentScope === 'ORGANIZATION' && !model.organizationPublicId) {
      issues.push('Selecciona la organización propietaria del formulario.')
    }
    return issues
  }, [editing, globalAdministrator, model.contentScope, model.durationMinutes, model.organizationPublicId, model.passingScore, model.title])

  const contentIssues = useMemo(() => {
    if (headerIssues.length > 0) return []
    if (model.contentMode === 'MANUAL' && selectedQuestions.length === 0) {
      return ['Agrega al menos una pregunta activa al formulario.']
    }
    if (model.contentMode === 'RANDOM_POOL' && configuredPools.length === 0) {
      return ['Agrega al menos una categoría al Pool aleatorio.']
    }
    if (configuredPools.some(pool => pool.questionCount < 1)) {
      return ['La cantidad de preguntas de cada categoría debe ser mayor que cero.']
    }
    return []
  }, [configuredPools, headerIssues.length, model.contentMode, selectedQuestions.length])

  const readiness = [...headerIssues, ...contentIssues]
  const hasConfiguredContent = selectedQuestions.length > 0 || configuredPools.length > 0

  useEffect(() => {
    if (headerIssues.length > 0 || !targetReady) {
      setQuestionPage(EMPTY_QUESTION_PAGE)
      setCategories([])
      return
    }
    const controller = new AbortController()
    setLoadingOptions(true)
    setError('')

    const categoryRequest = getFormCategoryOptions(
      model.contentScope ?? 'ORGANIZATION',
      model.organizationPublicId,
      controller.signal
    )

    if (model.contentMode === 'MANUAL') {
      Promise.all([
        getFormQuestionOptions({
          scope: model.contentScope ?? 'ORGANIZATION',
          organizationPublicId: model.organizationPublicId,
          query: debouncedQuestionQuery,
          categoryPublicId: questionCategoryPublicId || undefined,
          page: questionPageIndex,
          size: questionPageSize,
          signal: controller.signal
        }),
        categoryRequest
      ])
        .then(([questions, availableCategories]) => {
          setQuestionPage(questions)
          setCategories(availableCategories)
        })
        .catch(requestError => {
          if (!controller.signal.aborted) {
            setError(requestError instanceof ApiRequestError
              ? requestError.message
              : 'No fue posible consultar las preguntas disponibles.')
          }
        })
        .finally(() => { if (!controller.signal.aborted) setLoadingOptions(false) })
    } else {
      categoryRequest
        .then(setCategories)
        .catch(requestError => {
          if (!controller.signal.aborted) {
            setError(requestError instanceof ApiRequestError
              ? requestError.message
              : 'No fue posible consultar las categorías disponibles.')
          }
        })
        .finally(() => { if (!controller.signal.aborted) setLoadingOptions(false) })
    }
    return () => controller.abort()
  }, [
    debouncedQuestionQuery,
    headerIssues.length,
    model.contentMode,
    model.contentScope,
    model.organizationPublicId,
    questionCategoryPublicId,
    questionPageIndex,
    questionPageSize,
    targetReady
  ])

  function set<K extends keyof FormPayload>(key: K, value: FormPayload[K]) {
    setModel(current => ({ ...current, [key]: value }))
    setFieldErrors(current => {
      if (!current[key]) return current
      const next = { ...current }
      delete next[key]
      return next
    })
  }

  function clearContent() {
    selectedQuestionIdsRef.current.clear()
    setSelectedQuestions([])
    setConfiguredPools([])
    setQuestionPageIndex(0)
    setQuestionQuery('')
    setQuestionCategoryPublicId('')
  }

  function applyMode(mode: FormContentMode) {
    clearContent()
    set('contentMode', mode)
  }

  function requestMode(mode: FormContentMode) {
    if (mode === model.contentMode) return
    if (hasConfiguredContent) setPendingChange({ type: 'MODE', mode })
    else applyMode(mode)
  }

  function applyTarget(scope: FormContentScope, organizationPublicId?: string) {
    clearContent()
    setModel(current => ({
      ...current,
      contentScope: scope,
      organizationPublicId: scope === 'ORGANIZATION' ? organizationPublicId : undefined
    }))
  }

  function requestTarget(scope: FormContentScope, organizationPublicId?: string) {
    const same = scope === model.contentScope
      && (scope === 'GLOBAL' || organizationPublicId === model.organizationPublicId)
    if (same) return
    if (hasConfiguredContent) setPendingChange({ type: 'TARGET', scope, organizationPublicId })
    else applyTarget(scope, organizationPublicId)
  }

  function confirmPendingChange() {
    if (!pendingChange) return
    if (pendingChange.type === 'MODE') applyMode(pendingChange.mode)
    else applyTarget(pendingChange.scope, pendingChange.organizationPublicId)
    setPendingChange(null)
  }

  function addQuestion(publicId: string) {
    if (selectedQuestionIdsRef.current.has(publicId)) {
      toast.info('Esta pregunta ya forma parte del formulario.')
      return
    }
    const option = questionPage.content.find(question => question.publicId === publicId)
    if (!option) return
    selectedQuestionIdsRef.current.add(publicId)
    setSelectedQuestions(current => [...current, {
      questionPublicId: option.publicId,
      statement: option.statement,
      typeCode: option.typeCode,
      typeName: option.typeName,
      difficultyCode: option.difficultyCode,
      difficultyName: option.difficultyName,
      technologyName: option.technologyName,
      levelCode: option.levelCode,
      categoryNames: option.categoryNames,
      contentScope: option.contentScope,
      organizationName: option.organizationName,
      explanation: option.explanation,
      promptMedia: option.promptMedia,
      codeLanguage: option.codeLanguage,
      codeContent: option.codeContent,
      acceptedAnswersJson: option.acceptedAnswersJson,
      options: option.options,
      status: 'ACTIVE',
      order: current.length + 1,
      points: option.defaultPoints || 1,
      required: true
    }])
    toast.success('La pregunta se agregó correctamente al formulario.')
  }

  function moveQuestion(index: number, direction: -1 | 1) {
    const target = index + direction
    if (target < 0 || target >= selectedQuestions.length) return
    setSelectedQuestions(current => {
      const next = [...current]
      const [item] = next.splice(index, 1)
      if (!item) return current
      next.splice(target, 0, item)
      return next.map((question, position) => ({ ...question, order: position + 1 }))
    })
  }

  function removeQuestion(publicId: string) {
    selectedQuestionIdsRef.current.delete(publicId)
    setSelectedQuestions(current => current
      .filter(question => question.questionPublicId !== publicId)
      .map((question, index) => ({ ...question, order: index + 1 })))
    setRemoveCandidate(null)
    toast.success('La pregunta fue retirada del formulario. El Banco de preguntas no fue modificado.')
  }

  function updateQuestion(publicId: string, patch: Partial<Pick<FormQuestionItem, 'points' | 'required'>>) {
    setSelectedQuestions(current => current.map(question => question.questionPublicId === publicId
      ? { ...question, ...patch }
      : question))
  }

  function addPool(categoryPublicId: string) {
    if (!categoryPublicId || configuredPools.some(pool => pool.categoryPublicId === categoryPublicId)) return
    const category = categories.find(value => value.publicId === categoryPublicId)
    if (!category) return
    setConfiguredPools(current => [...current, {
      categoryPublicId: category.publicId,
      categoryName: category.name,
      questionCount: Math.min(1, category.activeQuestionCount),
      order: current.length + 1,
      availableQuestionCount: category.activeQuestionCount
    }])
  }

  function updatePool(categoryPublicId: string, patch: Partial<Pick<FormPoolItem, 'questionCount' | 'difficultyCode'>>) {
    setConfiguredPools(current => current.map(pool => pool.categoryPublicId === categoryPublicId
      ? { ...pool, ...patch }
      : pool))
  }

  function removePool(categoryPublicId: string) {
    setConfiguredPools(current => current
      .filter(pool => pool.categoryPublicId !== categoryPublicId)
      .map((pool, index) => ({ ...pool, order: index + 1 })))
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (readOnly || saving) return
    const firstIssue = readiness[0]
    if (firstIssue) {
      setError(firstIssue)
      return
    }

    const payload: FormPayload = {
      ...model,
      title: model.title.trim(),
      description: model.description.trim(),
      questions: model.contentMode === 'MANUAL'
        ? selectedQuestions.map((question, index) => ({
          questionPublicId: question.questionPublicId,
          order: index + 1,
          points: question.points,
          required: question.required
        }))
        : [],
      pools: model.contentMode === 'RANDOM_POOL'
        ? configuredPools.map((pool, index) => ({
          publicId: pool.publicId,
          sourceType: 'CATEGORY',
          sourcePublicId: pool.categoryPublicId,
          questionCount: pool.questionCount,
          difficultyCode: pool.difficultyCode,
          order: index + 1
        }))
        : []
    }

    setSaving(true)
    setError('')
    setFieldErrors({})
    try {
      if (editing && id) {
        const saved = await updateForm(id, payload)
        await getForm(saved.publicId)
        completeSave({ title: 'El formulario se actualizó correctamente.' })
      } else {
        const created = await createForm(payload)
        await getForm(created.publicId)
        completeSave({ title: 'El formulario se creó correctamente.' })
      }
    } catch (requestError) {
      if (requestError instanceof ApiRequestError) {
        setError(requestError.message)
        setFieldErrors(requestError.fieldErrors ?? {})
      } else {
        setError('No fue posible guardar el formulario. Revisa la configuración e intenta nuevamente.')
      }
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <main className="content-page"><div className="ns-loading-card">Cargando formulario…</div></main>

  if (readOnly) {
    return <main className="content-page ns-form-builder">
      <BackButton fallback="/admin/forms" />
      {error && <div className="ns-inline-alert" role="alert"><strong>No fue posible abrir el formulario</strong><span>{error}</span></div>}
      {!error && <div className="ns-builder-layout">
        <div className="ns-builder-main">
          <section className="ns-card"><div className="ns-card-heading"><h2>Información general</h2></div>
            <div className="catalog-readonly-grid">
              <div><span>Título</span><strong>{model.title}</strong></div>
              <div><span>Modalidad</span><strong>{model.modeCode === 'PRACTICE' ? 'Práctica' : 'Evaluación'}</strong></div>
              <div><span>Puntaje mínimo</span><strong>{model.passingScore}%</strong></div>
              <div><span>Alcance</span><strong>{model.contentScope === 'GLOBAL' ? 'Global' : 'Organizacional'}</strong></div>
              <div className="catalog-readonly-wide"><span>Descripción</span><strong>{model.description || 'Sin descripción'}</strong></div>
            </div>
          </section>
          <section className="ns-card"><div className="ns-card-heading"><h2>Contenido</h2><p>{model.contentMode === 'MANUAL' ? 'Preguntas manuales' : 'Pool aleatorio'}</p></div>
            {model.contentMode === 'MANUAL'
              ? <ol className="form-readonly-content-list">{selectedQuestions.map(question => <li key={question.questionPublicId}><strong>{question.statement}</strong><span>{question.typeName} · {question.points} punto{question.points === 1 ? '' : 's'}</span></li>)}</ol>
              : <div className="form-readonly-content-list">{configuredPools.map(pool => <div key={pool.categoryPublicId}><strong>{pool.categoryName}</strong><span>{pool.questionCount} pregunta{pool.questionCount === 1 ? '' : 's'}{pool.difficultyCode ? ` · ${pool.difficultyCode}` : ''}</span></div>)}</div>}
          </section>
        </div>
        <aside className="ns-builder-sidebar"><section className="ns-card ns-settings-card"><div className="ns-card-heading"><h2>Configuración</h2></div>
          <div className="catalog-readonly-grid">
            <div><span>Aceptar respuestas</span><strong>{model.acceptResponses ? 'Sí' : 'No'}</strong></div>
            <div><span>Reintentar hasta aprobar</span><strong>{model.retryUntilPassed ? 'Sí' : 'No'}</strong></div>
            <div><span>Mostrar resultados</span><strong>{model.showResults ? 'Sí' : 'No'}</strong></div>
            <div><span>Mostrar respuestas correctas</span><strong>{model.showCorrectAnswers ? 'Sí' : 'No'}</strong></div>
            <div><span>Duración</span><strong>{model.durationMinutes ? `${model.durationMinutes} min` : 'Sin límite'}</strong></div>
            <div><span>Intentos máximos</span><strong>{model.retryUntilPassed ? 'Hasta aprobar' : model.maxAttempts ?? 'Sin límite'}</strong></div>
          </div>
        </section></aside>
      </div>}
    </main>
  }

  const availableCategories = categories.filter(category => !configuredPools.some(pool => pool.categoryPublicId === category.publicId))

  return <main className="content-page ns-form-builder">
    <BackButton fallback="/admin/forms" />
    {error && <div className="ns-inline-alert" role="alert"><strong>Revisa el formulario</strong><span>{error}</span></div>}
    <form id="form-builder" onSubmit={submit}>
      <nav className="form-builder-tabs" aria-label="Secciones del formulario" role="tablist">
        <button
          className={activeTab === 'GENERAL' ? 'is-active' : ''}
          type="button"
          role="tab"
          aria-selected={activeTab === 'GENERAL'}
          onClick={() => setActiveTab('GENERAL')}
        >
          <span>1</span> Información general
        </button>
        <button
          className={activeTab === 'CONTENT' ? 'is-active' : ''}
          type="button"
          role="tab"
          aria-selected={activeTab === 'CONTENT'}
          onClick={() => setActiveTab('CONTENT')}
        >
          <span>2</span> Contenido
        </button>
        <button
          className={activeTab === 'CONFIGURATION' ? 'is-active' : ''}
          type="button"
          role="tab"
          aria-selected={activeTab === 'CONFIGURATION'}
          onClick={() => setActiveTab('CONFIGURATION')}
        >
          <span>3</span> Configuración
        </button>
      </nav>

      <div className="form-builder-tab-content">
        {activeTab === 'GENERAL' && <section className="ns-card" role="tabpanel">
          <div className="ns-card-heading">
            <div><span className="ns-step">1</span><h2>Información general</h2></div>
            <p>Define cómo se identificará y quién podrá utilizar el formulario.</p>
          </div>
          <div className="ns-form-grid">
            <label className="ns-field ns-field-wide">
              <span>Título <b>*</b></span>
              <input required minLength={3} aria-invalid={Boolean(fieldErrors.title)} value={model.title} onChange={event => set('title', event.target.value)} placeholder="Ej. Certificación APX — Nivel 1" />
              {fieldErrors.title ? <small className="ns-field-error">{fieldErrors.title}</small> : <small>Usa un nombre claro para administradores y participantes.</small>}
            </label>
            <label className="ns-field ns-field-wide">
              <span>Descripción</span>
              <textarea rows={4} value={model.description} onChange={event => set('description', event.target.value)} placeholder="Describe el objetivo y alcance del formulario." />
            </label>
            {globalAdministrator && !editing && <label className="ns-field">
              <span>Alcance <b>*</b></span>
              <SelectField value={model.contentScope ?? 'GLOBAL'} onChange={value => requestTarget(value as FormContentScope, value === 'ORGANIZATION' ? model.organizationPublicId : undefined)} ariaLabel="Alcance del formulario" options={[{ value: 'GLOBAL', label: 'Global' }, { value: 'ORGANIZATION', label: 'Organizacional' }]} />
              <small>El alcance determina qué preguntas y categorías estarán disponibles.</small>
            </label>}
            {globalAdministrator && !editing && model.contentScope === 'ORGANIZATION' && <div className="ns-field">
              <span>Organización <b>*</b></span>
              <SelectField
                required
                value={model.organizationPublicId ?? ''}
                disabled={organizationsLoading || Boolean(organizationsError)}
                onChange={value => requestTarget('ORGANIZATION', value)}
                ariaLabel="Organización propietaria"
                placeholder={organizationsLoading ? 'Cargando organizaciones…' : 'Seleccionar organización'}
                options={organizations.map(organization => ({ value: organization.publicId, label: `${organization.name} · ${organization.code}` }))}
              />
              <small>Solo se muestran organizaciones con seguimiento de certificaciones habilitado.</small>
              {organizationsError && <div className="form-option-load-error" role="alert"><span>{organizationsError}</span><button className="secondary-button compact-button" type="button" onClick={retryOrganizations}>Reintentar</button></div>}
              {fieldErrors.organizationPublicId && <small className="ns-field-error">{fieldErrors.organizationPublicId}</small>}
            </div>}
            {editing && <div className="ns-field">
              <span>Alcance</span>
              <div className="form-static-field">
                <strong>{model.contentScope === 'GLOBAL' ? 'Global' : 'Organizacional'}</strong>
                <small>{model.contentScope === 'ORGANIZATION' ? 'La organización propietaria se conserva durante la edición.' : 'El formulario pertenece al catálogo global.'}</small>
              </div>
            </div>}
            <label className="ns-field">
              <span>Modalidad</span>
              <SelectField value={model.modeCode} onChange={value => set('modeCode', value as FormMode)} ariaLabel="Modalidad" options={[{ value: 'ASSESSMENT', label: 'Evaluación' }, { value: 'PRACTICE', label: 'Práctica' }]} />
              <small>{model.modeCode === 'PRACTICE' ? 'Permite aprender y recibir retroalimentación.' : 'Califica el desempeño al finalizar.'}</small>
            </label>
            <label className="ns-field">
              <span>Puntaje mínimo</span>
              <div className="ns-input-suffix"><input type="number" min="0" max="100" value={model.passingScore} onChange={event => set('passingScore', Number(event.target.value))} /><span>%</span></div>
              <small>Porcentaje requerido para aprobar.</small>
            </label>
          </div>
        </section>}

        {activeTab === 'CONTENT' && <section className={`ns-card form-content-card${headerIssues.length > 0 ? ' is-disabled' : ''}`} role="tabpanel">
          <div className="ns-card-heading">
            <div><span className="ns-step">2</span><h2>Contenido</h2></div>
            <p>Elige una sola modalidad: preguntas manuales o Pool aleatorio.</p>
          </div>
          {headerIssues.length > 0 ? <div className="form-content-blocked" role="status">
            <Icon name="info" size={24} />
            <div><strong>Completa la información general para configurar el contenido</strong><p>{headerIssues[0]}</p></div>
          </div> : <>
            <div className="form-content-mode-grid" role="radiogroup" aria-label="Modalidad de contenido">
              <button className={`form-content-mode${model.contentMode === 'MANUAL' ? ' is-selected' : ''}`} type="button" role="radio" aria-checked={model.contentMode === 'MANUAL'} onClick={() => requestMode('MANUAL')}><Icon name="questions" size={22} /><span><strong>Preguntas manuales</strong><small>Selecciona, ordena y retira preguntas individualmente.</small></span></button>
              <button className={`form-content-mode${model.contentMode === 'RANDOM_POOL' ? ' is-selected' : ''}`} type="button" role="radio" aria-checked={model.contentMode === 'RANDOM_POOL'} onClick={() => requestMode('RANDOM_POOL')}><Icon name="categories" size={22} /><span><strong>Pool aleatorio</strong><small>Configura categorías; las preguntas activas se elegirán aleatoriamente.</small></span></button>
            </div>

            {model.contentMode === 'MANUAL' && <div className="form-manual-content form-manual-content--visual">
              <div className="form-content-section-heading form-builder-content-toolbar">
                <div>
                  <h3>Construcción visual del formulario</h3>
                  <p>Las preguntas provienen del Banco de preguntas y se muestran en modo de solo lectura.</p>
                </div>
                <div className="form-builder-content-actions">
                  <button className="primary-button" type="button" onClick={() => { setBankOpen(true); setPreviewQuestion(null) }}>
                    <Icon name="plus" size={16} /> Agregar preguntas del Banco
                  </button>
                  <button className="secondary-button" type="button" disabled={selectedQuestions.length === 0} onClick={() => setFormPreviewOpen(true)}>
                    <Icon name="eye" size={16} /> Vista previa del formulario
                  </button>
                </div>
              </div>

              {selectedQuestions.length === 0 && <div className="ns-builder-empty form-builder-empty-visual">
                <Icon name="clipboard" size={32} />
                <h3>Aún no agregas preguntas</h3>
                <p>Abre el Banco de preguntas, revisa el detalle completo y agrega las preguntas que integrarán el formulario.</p>
                <button className="primary-button" type="button" onClick={() => setBankOpen(true)}>
                  <Icon name="plus" size={16} /> Agregar preguntas del Banco
                </button>
              </div>}

              <div className="form-builder-question-list" aria-label="Preguntas incorporadas al formulario">
                {selectedQuestions.map((question, index) => <section className="form-builder-question-block" key={question.questionPublicId}>
                  <header className="form-builder-question-block__toolbar">
                    <div>
                      <span>Pregunta {index + 1} de {selectedQuestions.length}</span>
                      <strong>{question.typeName} · {question.points} {question.points === 1 ? 'punto' : 'puntos'}</strong>
                    </div>
                    <label className="form-builder-required-toggle">
                      <input type="checkbox" checked={question.required} onChange={event => updateQuestion(question.questionPublicId, { required: event.target.checked })} />
                      <span>Obligatoria</span>
                    </label>
                    <div className="form-question-actions">
                      <button aria-label="Ver detalle completo" type="button" onClick={() => setSelectedQuestionPreview(question)}><Icon name="eye" size={16} /></button>
                      <button aria-label="Subir pregunta" type="button" disabled={index === 0} onClick={() => moveQuestion(index, -1)}><Icon name="arrowUp" size={16} /></button>
                      <button aria-label="Bajar pregunta" type="button" disabled={index === selectedQuestions.length - 1} onClick={() => moveQuestion(index, 1)}><Icon name="arrowDown" size={16} /></button>
                      <button aria-label="Retirar pregunta" className="is-danger" type="button" onClick={() => setRemoveCandidate(question)}><Icon name="trash" size={16} /></button>
                    </div>
                  </header>
                  <FormQuestionReadOnly question={question} number={index + 1} points={question.points} required={question.required} status={question.status} />
                </section>)}
              </div>
            </div>}

            {model.contentMode === 'RANDOM_POOL' && <div className="form-pool-content">
              <div className="form-content-section-heading"><div><h3>Categorías del Pool</h3><p>Las preguntas se seleccionarán aleatoriamente entre las activas y disponibles.</p></div></div>
              <div className="form-pool-add-row"><SelectField value="" onChange={addPool} ariaLabel="Agregar categoría al Pool" placeholder="Seleccionar categoría" options={availableCategories.map(category => ({ value: category.publicId, label: `${category.name} · ${category.activeQuestionCount} activas` }))} /><small>Una categoría solo puede agregarse una vez.</small></div>
              {loadingOptions && <div className="ns-table-empty">Consultando categorías…</div>}
              {!loadingOptions && categories.length === 0 && <div className="ns-table-empty"><strong>No hay categorías disponibles</strong><span>El alcance seleccionado no tiene categorías activas con preguntas válidas.</span></div>}
              <div className="form-pool-list">
                {configuredPools.map(pool => <article className="form-pool-item" key={pool.categoryPublicId}>
                  <div className="form-pool-copy"><strong>{pool.categoryName}</strong><small>{pool.availableQuestionCount ?? 0} preguntas activas disponibles</small></div>
                  <label className="ns-field"><span>Cantidad</span><input type="number" min="1" max={Math.max(1, pool.availableQuestionCount ?? 1)} value={pool.questionCount} onChange={event => updatePool(pool.categoryPublicId, { questionCount: Number(event.target.value) })} /></label>
                  <label className="ns-field"><span>Dificultad</span><SelectField value={pool.difficultyCode ?? ''} onChange={value => updatePool(pool.categoryPublicId, { difficultyCode: value || undefined })} ariaLabel={`Dificultad para ${pool.categoryName}`} options={[{ value: '', label: 'Todas' }, { value: 'JR', label: 'JR' }, { value: 'STD', label: 'STD' }, { value: 'SR', label: 'SR' }]} /></label>
                  <button aria-label={`Retirar ${pool.categoryName}`} className="form-remove-pool" type="button" onClick={() => removePool(pool.categoryPublicId)}><Icon name="trash" size={17} /></button>
                </article>)}
              </div>
              {configuredPools.length === 0 && <div className="ns-builder-empty compact"><Icon name="categories" size={25} /><h3>Aún no configuras el Pool</h3><p>Agrega al menos una categoría y define cuántas preguntas debe tomar.</p></div>}
            </div>}
          </>}
        </section>}

        {activeTab === 'CONFIGURATION' && <section className="ns-card ns-settings-card form-settings-tab" role="tabpanel">
          <div className="ns-card-heading"><div><span className="ns-step">3</span><h2>Configuración</h2></div><p>Los cambios se aplican como una sola unidad al guardar.</p></div>
          <div className="form-settings-grid">
            <div className="ns-settings-group"><h3>Disponibilidad</h3><Toggle checked={model.acceptResponses} onChange={value => set('acceptResponses', value)} title="Aceptar respuestas" description="Habilita el acceso de participantes cuando el formulario esté activo." /></div>
            <div className="ns-settings-group"><h3>Calificación e intentos</h3>
              <Toggle checked={model.retryUntilPassed} onChange={value => set('retryUntilPassed', value)} title="Reintentar hasta aprobar" />
              <Toggle checked={model.showResults} onChange={value => set('showResults', value)} title="Mostrar resultados automáticamente" />
              <Toggle checked={model.showCorrectAnswers} onChange={value => set('showCorrectAnswers', value)} title="Mostrar respuestas correctas" />
              <div className="ns-inline-fields"><label className="ns-field"><span>Duración</span><div className="ns-input-suffix"><input type="number" min="1" value={model.durationMinutes ?? ''} onChange={event => set('durationMinutes', event.target.value ? Number(event.target.value) : undefined)} /><span>min</span></div></label><label className="ns-field"><span>Intentos máximos</span><input type="number" min="1" disabled={model.retryUntilPassed} value={model.maxAttempts ?? ''} onChange={event => set('maxAttempts', event.target.value ? Number(event.target.value) : undefined)} /></label></div>
            </div>
            <div className="ns-settings-group"><h3>Presentación</h3>
              <Toggle checked={model.randomizeQuestions} onChange={value => set('randomizeQuestions', value)} title="Orden aleatorio de preguntas" />
              <Toggle checked={model.randomizeOptions} onChange={value => set('randomizeOptions', value)} title="Orden aleatorio de opciones" />
              <Toggle checked={model.showProgress} onChange={value => set('showProgress', value)} title="Mostrar barra de progreso" />
              <Toggle checked={model.hideQuestionNumbers} onChange={value => set('hideQuestionNumbers', value)} title="Ocultar número de pregunta" />
              <Toggle checked={model.allowSaveResume} onChange={value => set('allowSaveResume', value)} title="Guardar y continuar después" />
            </div>
            <div className="ns-settings-group"><label className="ns-field"><span>Mensaje de agradecimiento</span><textarea rows={5} value={model.thankYouMessage} onChange={event => set('thankYouMessage', event.target.value)} /></label></div>
          </div>
        </section>}
      </div>

      <FormActions sticky>
        <button className="secondary-button" type="button" disabled={saving} onClick={() => navigate('/admin/forms')}>Cancelar</button>
        <button className="primary-button" disabled={saving || readiness.length > 0} type="submit">{saving ? 'Guardando…' : editing ? 'Guardar cambios' : 'Crear formulario'}</button>
      </FormActions>
    </form>

    <FullScreenDialog
      open={bankOpen}
      eyebrow="Contenido del formulario"
      title="Agregar preguntas del Banco"
      description="Busca y revisa la configuración completa de cada pregunta antes de incorporarla. Desde Formularios no puede modificarse."
      className="form-question-bank-dialog"
      onClose={() => { setBankOpen(false); setPreviewQuestion(null) }}
      footer={<>
        <button className="secondary-button" type="button" onClick={() => { setBankOpen(false); setPreviewQuestion(null) }}>Cerrar</button>
        {previewQuestion && <button
          className="primary-button"
          type="button"
          disabled={selectedQuestions.some(item => item.questionPublicId === previewQuestion.publicId)}
          onClick={() => addQuestion(previewQuestion.publicId)}
        >
          {selectedQuestions.some(item => item.questionPublicId === previewQuestion.publicId)
            ? <><Icon name="check" size={16} /> Ya agregada</>
            : <><Icon name="plus" size={16} /> Agregar pregunta</>}
        </button>}
      </>}
    >
      <div className="form-question-bank-layout">
        <aside className="form-question-bank-browser" aria-label="Preguntas disponibles">
          <div className="form-question-bank-filters">
            <ResourceSearchField value={questionQuery} onChange={value => { setQuestionQuery(value); setQuestionPageIndex(0) }} placeholder="Buscar por enunciado" disabled={loadingOptions} />
            <SelectField
              value={questionCategoryPublicId}
              onChange={value => { setQuestionCategoryPublicId(value); setQuestionPageIndex(0) }}
              ariaLabel="Filtrar preguntas por categoría"
              placeholder="Todas las categorías"
              options={[
                { value: '', label: 'Todas las categorías' },
                ...categories.map(category => ({ value: category.publicId, label: `${category.name} · ${category.activeQuestionCount}` }))
              ]}
            />
          </div>
          <div className="form-question-bank-results" aria-busy={loadingOptions}>
            {loadingOptions && <div className="ns-table-empty">Consultando preguntas…</div>}
            {!loadingOptions && questionPage.content.length === 0 && <div className="ns-table-empty">
              <strong>Sin preguntas disponibles</strong>
              <span>No existen preguntas activas disponibles para los filtros y el alcance seleccionados.</span>
            </div>}
            {!loadingOptions && questionPage.content.map(question => {
              const selected = selectedQuestions.some(item => item.questionPublicId === question.publicId)
              const active = previewQuestion?.publicId === question.publicId
              return <button
                className={`form-question-bank-result${active ? ' is-active' : ''}`}
                key={question.publicId}
                type="button"
                onClick={() => setPreviewQuestion(question)}
              >
                <span className="form-question-bank-result__statement">{question.statement}</span>
                <span>{question.typeName}{question.technologyName ? ` · ${question.technologyName}` : ''}</span>
                <small>{question.categoryNames.length ? question.categoryNames.join(' · ') : 'Sin categoría'} · {question.contentScope === 'GLOBAL' ? 'Global' : question.organizationName}</small>
                {selected && <em><Icon name="check" size={13} /> Agregada</em>}
              </button>
            })}
          </div>
          <TablePagination compact currentPage={questionPage.page} pageSize={questionPage.size} totalElements={questionPage.totalElements} totalPages={questionPage.totalPages} isLoading={loadingOptions} onPageChange={setQuestionPageIndex} onPageSizeChange={value => { setQuestionPageSize(value); setQuestionPageIndex(0) }} />
        </aside>
        <section className="form-question-bank-preview" aria-live="polite">
          {previewQuestion
            ? <FormQuestionReadOnly question={previewQuestion} points={previewQuestion.defaultPoints} variant="preview" />
            : <div className="form-question-bank-preview-empty"><Icon name="eye" size={34} /><h3>Selecciona una pregunta</h3><p>La vista previa mostrará el enunciado, imágenes, código, opciones, respuesta correcta y clasificación completa.</p></div>}
        </section>
      </div>
    </FullScreenDialog>

    <FullScreenDialog
      open={Boolean(selectedQuestionPreview)}
      eyebrow="Pregunta incorporada"
      title="Vista previa de la pregunta"
      description="Consulta la configuración definida en el Banco de preguntas. Desde Formularios no puede modificarse."
      onClose={() => setSelectedQuestionPreview(null)}
      footer={<button className="secondary-button" type="button" onClick={() => setSelectedQuestionPreview(null)}>Cerrar</button>}
    >
      {selectedQuestionPreview && <div className="form-single-question-preview"><FormQuestionReadOnly question={selectedQuestionPreview} points={selectedQuestionPreview.points} required={selectedQuestionPreview.required} status={selectedQuestionPreview.status} variant="preview" /></div>}
    </FullScreenDialog>

    <FullScreenDialog
      open={formPreviewOpen}
      eyebrow="Vista previa del formulario"
      title={model.title.trim() || 'Formulario sin título'}
      description={model.description.trim() || 'Sin descripción'}
      className="form-complete-preview-dialog"
      onClose={() => setFormPreviewOpen(false)}
      footer={<button className="secondary-button" type="button" onClick={() => setFormPreviewOpen(false)}>Cerrar vista previa</button>}
    >
      <div className="form-complete-preview">
        <header><span>{model.modeCode === 'PRACTICE' ? 'Práctica' : 'Evaluación'} · Puntaje mínimo {model.passingScore}%</span><strong>{selectedQuestions.length} {selectedQuestions.length === 1 ? 'pregunta' : 'preguntas'}</strong></header>
        {selectedQuestions.map((question, index) => <FormQuestionReadOnly key={question.questionPublicId} question={question} number={index + 1} points={question.points} required={question.required} status={question.status} variant="preview" />)}
      </div>
    </FullScreenDialog>

    <ConfirmDialog
      open={Boolean(removeCandidate)}
      title="Retirar pregunta del formulario"
      description="La pregunta será retirada únicamente de este formulario. El Banco de preguntas no será modificado."
      confirmLabel="Retirar pregunta"
      tone="danger"
      onConfirm={() => { if (removeCandidate) removeQuestion(removeCandidate.questionPublicId) }}
      onCancel={() => setRemoveCandidate(null)}
    />

    <ConfirmDialog open={Boolean(pendingChange)} title="Cambiar configuración del contenido" description="El contenido configurado no es compatible con el nuevo alcance o modalidad y será descartado." confirmLabel="Cambiar y limpiar contenido" tone="danger" onConfirm={confirmPendingChange} onCancel={() => setPendingChange(null)} />
  </main>
}
