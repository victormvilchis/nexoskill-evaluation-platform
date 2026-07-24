import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../../features/authentication/context/AuthContext'
import { LoadingScreen } from './LoadingScreen'

export function ProtectedRoute() {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading) {
    return <LoadingScreen />
  }

  if (!user) {
    return (
      <Navigate
        to="/login"
        replace
        state={{ from: location.pathname }}
      />
    )
  }

  return <Outlet />
}
