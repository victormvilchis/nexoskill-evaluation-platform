import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useStudentAuth } from '../../features/students/context/StudentAuthContext'
import { LoadingScreen } from './LoadingScreen'

export function StudentProtectedRoute() {
  const { student, loading } = useStudentAuth()
  const location = useLocation()
  if (loading) return <LoadingScreen />
  if (!student) return <Navigate to="/student-login" replace />
  if (student.passwordChangeRequired && location.pathname !== "/student/change-password") {
    return <Navigate to="/student/change-password" replace />
  }
  return <Outlet />
}
