import { Navigate, Route, Routes } from 'react-router-dom'
import { ApplicationLayout } from './layouts/ApplicationLayout'
import { AdminQuestionDetailPage } from './pages/AdminQuestionDetailPage'
import { AdminCollectionsPage } from './pages/AdminCollectionsPage'
import { CreateCollectionPage } from './pages/CreateCollectionPage'
import { CollectionDetailPage } from './pages/CollectionDetailPage'
import { AdminQuestionsPage } from './pages/AdminQuestionsPage'
import { AdminUserDetailPage } from './pages/AdminUserDetailPage'
import { AdminUsersPage } from './pages/AdminUsersPage'
import { ChangePasswordPage } from './pages/ChangePasswordPage'
import { CreateQuestionPage } from './pages/CreateQuestionPage'
import { CreateUserPage } from './pages/CreateUserPage'
import { DashboardPage } from './pages/DashboardPage'
import { EditQuestionPage } from './pages/EditQuestionPage'
import { AdminFormsPage } from './pages/AdminFormsPage'
import { FormBuilderPage } from './pages/FormBuilderPage'
import { LoginPage } from './pages/LoginPage'
import { ProfilePage } from './pages/ProfilePage'
import { QuestionCategoriesPage } from './pages/QuestionCategoriesPage'
import { AdminOrganizationsPage } from './pages/AdminOrganizationsPage'
import { OrganizationEditorPage } from './pages/OrganizationEditorPage'
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

          <Route element={<PermissionRoute permission="ORGANIZATION_VIEW" />}>
<Route path="/admin/organizations" element={<AdminOrganizationsPage />} />
<Route path="/admin/organizations/:publicId" element={<OrganizationEditorPage />} />
<Route path="/admin/organizations/:publicId/edit" element={<OrganizationEditorPage />} />
</Route>
<Route element={<PermissionRoute permission="ORGANIZATION_CREATE" />}>
<Route path="/admin/organizations/new" element={<OrganizationEditorPage />} />
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

          <Route element={<PermissionRoute permission="QUESTION_UPDATE" />}>
            <Route
              path="/admin/questions/:publicId/edit"
              element={<EditQuestionPage />}
            />
          </Route>


          <Route element={<PermissionRoute permission="COLLECTION_VIEW" />}>
            <Route path="/admin/collections" element={<AdminCollectionsPage />} />
            <Route path="/admin/collections/:publicId" element={<CollectionDetailPage />} />
          </Route>
          <Route element={<PermissionRoute permission="COLLECTION_MANAGE" />}>
            <Route path="/admin/collections/new" element={<CreateCollectionPage />} />
          </Route>
          <Route path="/admin/question-collections" element={<Navigate to="/admin/collections" replace />} />
          <Route path="/admin/question-collections/new" element={<Navigate to="/admin/collections/new" replace />} />
          <Route path="/admin/question-collections/:publicId" element={<Navigate to="/admin/collections" replace />} />
          <Route element={<PermissionRoute permission="FORM_VIEW" />}>
            <Route path="/admin/forms" element={<AdminFormsPage />} />
            <Route path="/admin/forms/:id/edit" element={<FormBuilderPage />} />
          </Route>
          <Route element={<PermissionRoute permission="FORM_CREATE" />}>
            <Route path="/admin/forms/new" element={<FormBuilderPage />} />
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
