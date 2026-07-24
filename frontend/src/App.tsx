import { Navigate, Route, Routes } from 'react-router-dom'
import { ApplicationLayout } from './layouts/ApplicationLayout'
import { AdminUserDetailPage } from './pages/AdminUserDetailPage'
import { AdminUsersPage } from './pages/AdminUsersPage'
import { CreateUserPage } from './pages/CreateUserPage'
import { DashboardPage } from './pages/DashboardPage'
import { LoginPage } from './pages/LoginPage'
import { PermissionRoute } from './shared/components/PermissionRoute'
import { ProtectedRoute } from './shared/components/ProtectedRoute'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<ApplicationLayout />}>
          <Route path="/dashboard" element={<DashboardPage />} />

          <Route element={<PermissionRoute permission="USER_VIEW" />}>
            <Route path="/admin/users" element={<AdminUsersPage />} />
            <Route path="/admin/users/:publicId" element={<AdminUserDetailPage />} />
          </Route>

          <Route element={<PermissionRoute permission="USER_CREATE" />}>
            <Route path="/admin/users/new" element={<CreateUserPage />} />
          </Route>
        </Route>
      </Route>

      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  )
}
