import { Navigate, Route, Routes } from 'react-router-dom'
import { ApplicationLayout } from './layouts/ApplicationLayout'
import { AdminQuestionDetailPage } from './pages/AdminQuestionDetailPage'
import { AdminQuestionsPage } from './pages/AdminQuestionsPage'
import { AdminUserDetailPage } from './pages/AdminUserDetailPage'
import { AdminUsersPage } from './pages/AdminUsersPage'
import { ChangePasswordPage } from './pages/ChangePasswordPage'
import { CreateQuestionPage } from './pages/CreateQuestionPage'
import { CreateUserPage } from './pages/CreateUserPage'
import { DashboardPage } from './pages/DashboardPage'
import { LoginPage } from './pages/LoginPage'
import { ProfilePage } from './pages/ProfilePage'
import { QuestionCategoriesPage } from './pages/QuestionCategoriesPage'
import { PermissionRoute } from './shared/components/PermissionRoute'
import { ProtectedRoute } from './shared/components/ProtectedRoute'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      <Route element={<ProtectedRoute />}>
        <Route path="/change-password" element={<ChangePasswordPage />} />

        <Route element={<ApplicationLayout />}>
          <Route path="/dashboard" element={<DashboardPage />} />

          <Route element={<PermissionRoute permission="PROFILE_VIEW" />}>
            <Route path="/profile" element={<ProfilePage />} />
          </Route>

          <Route element={<PermissionRoute permission="USER_VIEW" />}>
            <Route path="/admin/users" element={<AdminUsersPage />} />
            <Route path="/admin/users/:publicId" element={<AdminUserDetailPage />} />
          </Route>

          <Route element={<PermissionRoute permission="USER_CREATE" />}>
            <Route path="/admin/users/new" element={<CreateUserPage />} />
          </Route>

          <Route element={<PermissionRoute permission="QUESTION_VIEW" />}>
            <Route path="/admin/questions" element={<AdminQuestionsPage />} />
            <Route
              path="/admin/questions/:publicId"
              element={<AdminQuestionDetailPage />}
            />
          </Route>

          <Route element={<PermissionRoute permission="QUESTION_CREATE" />}>
            <Route
              path="/admin/questions/new"
              element={<CreateQuestionPage />}
            />
          </Route>

          <Route
            element={<PermissionRoute permission="QUESTION_CATEGORY_MANAGE" />}
          >
            <Route
              path="/admin/question-categories"
              element={<QuestionCategoriesPage />}
            />
          </Route>
        </Route>
      </Route>

      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  )
}
