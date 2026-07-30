import { Navigate, Route, Routes } from 'react-router-dom'
import { ApplicationLayout } from './layouts/ApplicationLayout'
import { AdminQuestionDetailPage } from './pages/AdminQuestionDetailPage'
import { AdminCollectionsPage } from './pages/AdminCollectionsPage'
import { CreateCollectionPage } from './pages/CreateCollectionPage'
import { CollectionDetailPage } from './pages/CollectionDetailPage'
import { AdminQuestionsPage } from './pages/AdminQuestionsPage'
import { AdminUserDetailPage } from './pages/AdminUserDetailPage'
import { AdminUsersPage } from './pages/AdminUsersPage'
import { AdminUserManagementPage } from './pages/AdminUserManagementPage'
import { ChangePasswordPage } from './pages/ChangePasswordPage'
import { CreateQuestionPage } from './pages/CreateQuestionPage'
import { CreateUserPage } from './pages/CreateUserPage'
import { DashboardPage } from './pages/DashboardPage'
import { EditQuestionPage } from './pages/EditQuestionPage'
import { AdminFormsPage } from './pages/AdminFormsPage'
import { FormBuilderPage } from './pages/FormBuilderPage'
import { LoginPage } from './pages/LoginPage'
import { ProfilePage } from './pages/ProfilePage'
import { CatalogItemsPage } from './pages/CatalogItemsPage'
import { AdminOrganizationsPage } from './pages/AdminOrganizationsPage'
import { OrganizationEditorPage } from './pages/OrganizationEditorPage'
import { OrganizationManagementPage } from './pages/OrganizationManagementPage'
import { AdminStudentsPage } from './pages/AdminStudentsPage'
import { StudentEditorPage } from './pages/StudentEditorPage'
import { StudentManagementPage } from './pages/StudentManagementPage'
import { StudentCertificationsPage } from './pages/StudentCertificationsPage'
import { StudentLoginPage } from './pages/StudentLoginPage'
import { StudentPortalPage } from './pages/StudentPortalPage'
import { StudentChangePasswordPage } from './pages/StudentChangePasswordPage'
import { StudentProtectedRoute } from './shared/components/StudentProtectedRoute'
import { PermissionRoute } from './shared/components/PermissionRoute'
import { ProtectedRoute } from './shared/components/ProtectedRoute'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/student-login" element={<StudentLoginPage />} />
      <Route element={<StudentProtectedRoute />}>
        <Route path="/student" element={<StudentPortalPage />} />
        <Route path="/student/change-password" element={<StudentChangePasswordPage />} />
      </Route>
      <Route element={<ProtectedRoute />}>
        <Route path="/change-password" element={<ChangePasswordPage />} />

        <Route element={<ApplicationLayout />}>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route element={<PermissionRoute permission="PROFILE_VIEW" />}>
            <Route path="/profile" element={<ProfilePage />} />
          </Route>
          <Route element={<PermissionRoute permission="ORGANIZATION_VIEW" />}>
            <Route path="/admin/organizations" element={<AdminOrganizationsPage />} />
            <Route path="/admin/organizations/:publicId" element={<OrganizationEditorPage mode="view" />} />
          </Route>
          <Route element={<PermissionRoute permission="ORGANIZATION_CREATE" />}>
            <Route path="/admin/organizations/new" element={<OrganizationEditorPage mode="create" />} />
          </Route>
          <Route element={<PermissionRoute permission="ORGANIZATION_UPDATE" />}>
            <Route path="/admin/organizations/:publicId/edit" element={<OrganizationEditorPage mode="edit" />} />
          </Route>
          <Route element={<PermissionRoute permission="ORGANIZATION_STATUS_CHANGE" />}>
            <Route path="/admin/organizations/:publicId/manage" element={<OrganizationManagementPage />} />
          </Route>
          <Route element={<PermissionRoute permission="USER_VIEW" />}>
            <Route path="/admin/users" element={<AdminUsersPage />} />
            <Route path="/admin/users/:publicId" element={<AdminUserDetailPage mode="view" />} />
          </Route>
          <Route element={<PermissionRoute permission="USER_UPDATE" />}>
            <Route path="/admin/users/:publicId/edit" element={<AdminUserDetailPage mode="edit" />} />
          </Route>
          <Route element={<PermissionRoute permission="USER_STATUS_CHANGE" />}>
            <Route path="/admin/users/:publicId/manage" element={<AdminUserManagementPage />} />
          </Route>
          <Route element={<PermissionRoute permission="STUDENT_VIEW" />}>
            <Route path="/admin/students" element={<AdminStudentsPage />} />
            <Route path="/admin/students/:publicId" element={<StudentEditorPage mode="view" />} />
          </Route>
          <Route element={<PermissionRoute permission="STUDENT_CREATE" />}>
            <Route path="/admin/students/new" element={<StudentEditorPage mode="create" />} />
          </Route>
          <Route element={<PermissionRoute permission="STUDENT_UPDATE" />}>
            <Route path="/admin/students/:publicId/edit" element={<StudentEditorPage mode="edit" />} />
          </Route>
          <Route element={<PermissionRoute anyOf={['STUDENT_STATUS_CHANGE', 'STUDENT_SESSION_MANAGE', 'STUDENT_DELETE']} />}>
            <Route path="/admin/students/:publicId/manage" element={<StudentManagementPage />} />
          </Route>
          <Route element={<PermissionRoute permission="STUDENT_CERTIFICATION_MANAGE" roles={['MANAGER', 'SUPERVISOR']} />}>
            <Route path="/admin/students/:publicId/certifications" element={<StudentCertificationsPage />} />
          </Route>
          <Route element={<PermissionRoute permission="USER_CREATE" />}>
            <Route path="/admin/users/new" element={<CreateUserPage />} />
          </Route>
          <Route element={<PermissionRoute permission="QUESTION_VIEW" />}>
            <Route path="/admin/questions" element={<AdminQuestionsPage />} />
            <Route path="/admin/questions/:publicId" element={<AdminQuestionDetailPage />} />
            <Route path="/admin/questions/:publicId/manage" element={<Navigate to="/admin/questions" replace />} />
          </Route>
          <Route element={<PermissionRoute permission="QUESTION_CREATE" />}>
            <Route path="/admin/questions/new" element={<CreateQuestionPage />} />
          </Route>
          <Route element={<PermissionRoute permission="QUESTION_UPDATE" />}>
            <Route path="/admin/questions/:publicId/edit" element={<EditQuestionPage />} />
          </Route>
          <Route element={<PermissionRoute permission="COLLECTION_VIEW" />}>
            <Route path="/admin/collections" element={<AdminCollectionsPage />} />
            <Route path="/admin/collections/:publicId" element={<CollectionDetailPage readOnly />} />
          </Route>
          <Route element={<PermissionRoute permission="COLLECTION_MANAGE" />}>
            <Route path="/admin/collections/new" element={<CreateCollectionPage />} />
            <Route path="/admin/collections/:publicId/edit" element={<CollectionDetailPage />} />
          </Route>
          <Route path="/admin/question-collections" element={<Navigate to="/admin/collections" replace />} />
          <Route path="/admin/question-collections/new" element={<Navigate to="/admin/collections/new" replace />} />
          <Route path="/admin/question-collections/:publicId" element={<Navigate to="/admin/collections" replace />} />
          <Route element={<PermissionRoute permission="FORM_VIEW" />}>
            <Route path="/admin/forms" element={<AdminFormsPage />} />
            <Route path="/admin/forms/:id" element={<FormBuilderPage readOnly />} />
          </Route>
          <Route element={<PermissionRoute permission="FORM_CREATE" />}>
            <Route path="/admin/forms/new" element={<FormBuilderPage />} />
          </Route>
          <Route element={<PermissionRoute permission="FORM_UPDATE" />}>
            <Route path="/admin/forms/:id/edit" element={<FormBuilderPage />} />
          </Route>
          <Route element={<PermissionRoute permission="CATALOG_VIEW" />}>
            <Route path="/admin/catalogs" element={<Navigate to="/admin/catalogs/CATEGORIES" replace />} />
            <Route path="/admin/catalogs/QUESTION_TYPES" element={<Navigate to="/admin/catalogs/CATEGORIES" replace />} />
            <Route path="/admin/catalogs/DIFFICULTIES" element={<Navigate to="/admin/catalogs/CATEGORIES" replace />} />
            <Route path="/admin/catalogs/:type" element={<CatalogItemsPage />} />
            <Route path="/admin/catalogs/:type/:id" element={<CatalogItemsPage />} />
          </Route>
          <Route element={<PermissionRoute permission="CATALOG_MANAGE" />}>
            <Route path="/admin/catalogs/:type/new" element={<CatalogItemsPage />} />
            <Route path="/admin/catalogs/:type/:id/edit" element={<CatalogItemsPage />} />
            <Route path="/admin/catalogs/:type/:id/manage" element={<CatalogItemsPage />} />
          </Route>
          <Route path="/admin/question-categories" element={<Navigate to="/admin/catalogs/CATEGORIES" replace />} />
          <Route path="/admin/question-technologies" element={<Navigate to="/admin/catalogs/TECHNOLOGIES" replace />} />
        </Route>
      </Route>
      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  )
}
