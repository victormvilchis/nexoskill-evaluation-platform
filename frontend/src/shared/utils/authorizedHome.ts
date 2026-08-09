import type { CurrentUser } from '../types/auth'

export function authorizedHome(user: CurrentUser | null | undefined) {
  if (!user) return '/login'
  const administrator = user.roles.includes('ADMINISTRATOR')
  if (administrator || user.permissions.includes('DASHBOARD_VIEW')) return '/dashboard'
  if (user.permissions.includes('STUDENT_VIEW')) return '/admin/collaborators'
  if (user.permissions.includes('TALENT_VIEW')) return '/admin/talent-bank'
  if (user.permissions.includes('QUESTION_VIEW')) return '/admin/questions'
  if (user.permissions.includes('PROFILE_VIEW')) return '/profile'
  return '/access-denied'
}
