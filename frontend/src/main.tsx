import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import { AuthProvider } from './features/authentication/context/AuthContext'
import { StudentAuthProvider } from './features/students/context/StudentAuthContext'
import { PlatformErrorProvider } from './shared/components/PlatformErrorProvider'
import { ToastProvider } from './shared/components/ToastProvider'
import './styles/global.css'
import './styles/r3-logical-deletion.css'
import './styles/ui-foundation-r1.css'
import './styles/question-bank-java-r3.css'
import './styles/layout-wide-r4.css'
import './styles/resource-management-r5.css'
import './styles/multitenancy-core-r1.css'
import './styles/students-part2-r1.css'
import './styles/internal-users-auth-r1.css'
import './styles/category-lifecycle-r1.css'
import './styles/student-certifications-r1.css'
import './styles/catalog-navigation-r2.css'
import './styles/table-pagination-r1.css'
import './styles/question-tags-r1.css'
import './styles/platform-foundation-r1.css'
import './styles/student-import.css'
import './styles/valtieris-brand-r1.css'
import './styles/frontend-visual-checkpoint-r1.css'
import './styles/frontend-visual-corrections-r2.css'
import './styles/administrative-homologation-r1.css'
import './styles/visual-homologation-r3.css'
import './styles/talent-bank-r1.css'
import './styles/form-builder-content-r1.css'
import './styles/form-builder-visual-r2.css'
import './styles/role-management-r1.css'
import './styles/authorization-integrity-r3.css'
import './styles/platform-errors-r1.css'
import './styles/table-density-r1.css'
import './styles/filter-density-r2.css'
import './styles/dashboard-executive-r1.css'
import './styles/student-development-r1.css'
import './styles/paths-r1.css'
const basename = import.meta.env.BASE_URL.replace(/\/$/, '')
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter basename={basename}>
      <ToastProvider>
        <PlatformErrorProvider>
          <AuthProvider>
            <StudentAuthProvider>
              <App />
            </StudentAuthProvider>
          </AuthProvider>
        </PlatformErrorProvider>
      </ToastProvider>
    </BrowserRouter>
  </StrictMode>
)
