import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../../features/authentication/context/AuthContext'
import { LoadingScreen } from './LoadingScreen'

interface PermissionRouteProps {
  permission?: string
  anyOf?: string[]
  roles?: string[]
}

export function PermissionRoute({ permission, anyOf = [], roles = [] }: PermissionRouteProps) {
  const { user, loading } = useAuth()

  if (loading) return <LoadingScreen />

  const administrator = user?.roles.includes('ADMINISTRATOR') ?? false
  const required = permission ? [permission] : anyOf
  const hasPermission = administrator || required.length === 0
    || required.some((item) => user?.permissions.includes(item))
  const hasRole = administrator || roles.length === 0
    || roles.some((role) => user?.roles.includes(role))

  if (!hasPermission || !hasRole) {
    return <Navigate to="/dashboard" replace />
  }

  return <Outlet />
}
