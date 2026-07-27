import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import { AuthProvider } from './features/authentication/context/AuthContext'
import { StudentAuthProvider } from './features/students/context/StudentAuthContext'
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
import './styles/global-content-governance-r1.css'

const basename = import.meta.env.BASE_URL.replace(/\/$/, '')
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter basename={basename}>
      <ToastProvider>
        <AuthProvider>
          <StudentAuthProvider>
            <App />
          </StudentAuthProvider>
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  </StrictMode>
)
