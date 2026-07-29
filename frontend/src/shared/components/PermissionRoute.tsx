import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../../features/authentication/context/AuthContext'
import { LoadingScreen } from './LoadingScreen'

interface PermissionRouteProps {
  permission?: string
  anyOf?: string[]
}

export function PermissionRoute({ permission, anyOf = [] }: PermissionRouteProps) {
  const { user, loading } = useAuth()

  if (loading) return <LoadingScreen />

  const required = permission ? [permission] : anyOf
  const authorized = required.length > 0 && required.some((item) => user?.permissions.includes(item))
  if (!authorized) {
    return <Navigate to="/dashboard" replace />
  }

  return <Outlet />
}
