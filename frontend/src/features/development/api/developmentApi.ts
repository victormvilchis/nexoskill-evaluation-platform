import { apiRequest } from '../../../shared/api/apiClient'
import type { CertificationCard, EvaluationAttempt, EvaluationCard, EvaluationSubmission, HomeView, OrganizationBrand, PathCard, PracticeFeedback, PracticeResult, PracticeSession, StudentExperienceConfig, StudyOptions } from '../types/development'

export const getDevelopmentHome = () => apiRequest<HomeView>('/student/development/home')
export const getStudentBrand = () => apiRequest<OrganizationBrand>('/student/development/branding')
export const getStudentDevelopmentConfig = () => apiRequest<StudentExperienceConfig>('/student/development/config')
export const getStudyOptions = () => apiRequest<StudyOptions>('/student/development/study/options')
export const getMyCertifications = () => apiRequest<CertificationCard[]>('/student/development/certifications')
export const getMyEvaluations = () => apiRequest<EvaluationCard[]>('/student/development/evaluations')
export const getMyPaths = () => apiRequest<PathCard[]>('/student/development/paths')
export const getMyPath = (assignmentPublicId: string) => apiRequest<PathCard>(`/student/development/paths/${assignmentPublicId}`)
export const startPractice = (payload: { mode: string; questionCount: number; technologyPublicId?: string; categoryPublicId?: string }) => apiRequest<PracticeSession>('/student/development/practices', { method: 'POST', body: JSON.stringify(payload) })
export const getPractice = (publicId: string) => apiRequest<PracticeSession>(`/student/development/practices/${publicId}`)
export const answerPractice = (sessionId: string, questionId: string, selectedOptionPublicIds: string[]) => apiRequest<PracticeFeedback>(`/student/development/practices/${sessionId}/questions/${questionId}/answer`, { method: 'POST', body: JSON.stringify({ selectedOptionPublicIds }) })
export const getPracticeResult = (publicId: string) => apiRequest<PracticeResult>(`/student/development/practices/${publicId}/result`)
export const startEvaluation = (assignmentId: string) => apiRequest<EvaluationAttempt>(`/student/development/evaluations/${assignmentId}/start`, { method: 'POST' })
export const getEvaluationAttempt = (attemptId: string) => apiRequest<EvaluationAttempt>(`/student/development/evaluations/attempts/${attemptId}`)
export const answerEvaluation = (attemptId: string, questionId: string, payload: { selectedOptionPublicIds?: string[]; matchingPairs?: Record<string,string>; textAnswer?: string }) => apiRequest<EvaluationAttempt>(`/student/development/evaluations/attempts/${attemptId}/questions/${questionId}/answer`, { method: 'POST', body: JSON.stringify(payload) })
export const submitEvaluation = (attemptId: string) => apiRequest<EvaluationSubmission>(`/student/development/evaluations/attempts/${attemptId}/submit`, { method: 'POST' })
