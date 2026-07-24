import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../../features/authentication/context/AuthContext'
import { LoadingScreen } from './LoadingScreen'

interface PermissionRouteProps {
  permission: string
}

export function PermissionRoute({ permission }: PermissionRouteProps) {
  const { user, loading } = useAuth()

  if (loading) return <LoadingScreen />

  if (!user?.permissions.includes(permission)) {
    return <Navigate to="/dashboard" replace />
  }

  return <Outlet />
}
