import { useEffect } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
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
import { StudentImportPage } from './pages/StudentImportPage'
import { StudentManagementPage } from './pages/StudentManagementPage'
import { StudentCertificationsPage } from './pages/StudentCertificationsPage'
import { StudentLoginPage } from './pages/StudentLoginPage'
import { StudentPortalPage } from './pages/StudentPortalPage'
import { TalentBankPage } from './pages/TalentBankPage'
import { TalentTypeSelectionPage } from './pages/TalentTypeSelectionPage'
import { AcademyTalentEditorPage } from './pages/AcademyTalentEditorPage'
import { TalentBankDetailPage } from './pages/TalentBankDetailPage'
import { TalentBankEditRouter } from './pages/TalentBankEditRouter'
import { StudentChangePasswordPage } from './pages/StudentChangePasswordPage'
import { StudentProtectedRoute } from './shared/components/StudentProtectedRoute'
import { PermissionRoute } from './shared/components/PermissionRoute'
import { ProtectedRoute } from './shared/components/ProtectedRoute'

function resolveDocumentSection(pathname: string) {
  if (pathname === '/login') return 'Acceso'
  if (pathname === '/student-login') return 'Acceso de colaboradores'
  if (pathname.startsWith('/student/change-password')) return 'Cambiar contraseña'
  if (pathname.startsWith('/student')) return 'Portal de colaboradores'
  if (pathname.startsWith('/admin/organizations')) return 'Organizaciones'
  if (pathname.startsWith('/admin/talent-bank')) return 'Talent Bank'
  if (pathname.startsWith('/admin/collaborators')) return 'Colaboradores'
  if (pathname.startsWith('/admin/users')) return 'Usuarios'
  if (pathname.startsWith('/admin/questions')) return 'Preguntas'
  if (pathname.startsWith('/admin/forms')) return 'Formularios'
  if (pathname.startsWith('/admin/collections')) return 'Colecciones'
  if (pathname.startsWith('/admin/catalogs')) return 'Catálogos'
  if (pathname === '/dashboard') return 'Inicio'
  if (pathname === '/profile') return 'Perfil'
  if (pathname === '/change-password') return 'Cambiar contraseña'
  return undefined
}

export default function App() {
  const location = useLocation()

  useEffect(() => {
    const section = resolveDocumentSection(location.pathname)
    document.title = section
      ? `${section} | Valtieris Talent Platform`
      : 'Valtieris Talent Platform'
  }, [location.pathname])

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
            <Route path="/admin/collaborators" element={<AdminStudentsPage />} />
            <Route path="/admin/collaborators/:publicId" element={<StudentEditorPage mode="view" />} />
          </Route>
          <Route element={<PermissionRoute permission="STUDENT_VIEW" />}>
            <Route path="/admin/talent-bank" element={<TalentBankPage />} />
            <Route path="/admin/talent-bank/:publicId" element={<TalentBankDetailPage />} />
          </Route>
          <Route element={<PermissionRoute permission="STUDENT_CREATE" />}>
            <Route path="/admin/talent-bank/new" element={<TalentTypeSelectionPage />} />
            <Route path="/admin/talent-bank/new/academy" element={<AcademyTalentEditorPage mode="create" />} />
            <Route path="/admin/talent-bank/new/prospect" element={<StudentEditorPage mode="create" workspace="talent-bank" />} />
            <Route element={<PermissionRoute permission="STUDENT_UPDATE" />}>
              <Route path="/admin/talent-bank/:publicId/convert" element={<StudentEditorPage mode="edit" workspace="talent-bank" conversion />} />
            </Route>
          </Route>
          <Route element={<PermissionRoute permission="STUDENT_UPDATE" />}>
            <Route path="/admin/talent-bank/:publicId/edit" element={<TalentBankEditRouter />} />
          </Route>
          <Route element={<PermissionRoute permission="STUDENT_CREATE" />}>
            <Route path="/admin/collaborators/new" element={<StudentEditorPage mode="create" />} />
          </Route>
          <Route element={<PermissionRoute permission="STUDENT_UPDATE" />}>
            <Route path="/admin/collaborators/:publicId/edit" element={<StudentEditorPage mode="edit" />} />
          </Route>
          <Route element={<PermissionRoute permission="STUDENT_CREATE" roles={['ADMINISTRATOR', 'MANAGER', 'SUPERVISOR']} />}>
            <Route element={<PermissionRoute permission="STUDENT_UPDATE" />}>
              <Route path="/admin/collaborators/import" element={<StudentImportPage />} />
            </Route>
          </Route>
          <Route element={<PermissionRoute anyOf={['STUDENT_STATUS_CHANGE', 'STUDENT_SESSION_MANAGE', 'STUDENT_DELETE']} />}>
            <Route path="/admin/collaborators/:publicId/manage" element={<StudentManagementPage />} />
          </Route>
          <Route element={<PermissionRoute permission="STUDENT_CERTIFICATION_MANAGE" roles={['ADMINISTRATOR', 'MANAGER', 'SUPERVISOR']} />}>
            <Route path="/admin/collaborators/:publicId/certifications" element={<StudentCertificationsPage />} />
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
          <Route element={<PermissionRoute permission="FORM_CREATE" />}>
            <Route path="/admin/forms/new" element={<FormBuilderPage mode="create" />} />
          </Route>
          <Route element={<PermissionRoute permission="FORM_VIEW" />}>
            <Route path="/admin/forms" element={<AdminFormsPage />} />
            <Route path="/admin/forms/:id" element={<FormBuilderPage mode="view" />} />
          </Route>
          <Route element={<PermissionRoute permission="FORM_UPDATE" />}>
            <Route path="/admin/forms/:id/edit" element={<FormBuilderPage mode="edit" />} />
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
