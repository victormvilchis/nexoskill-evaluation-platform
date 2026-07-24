import { apiRequest } from '../../../shared/api/apiClient'
import type { WelcomeDashboard } from '../../../shared/types/dashboard'

export function getWelcomeDashboard() {
  return apiRequest<WelcomeDashboard>('/dashboard/welcome')
}
