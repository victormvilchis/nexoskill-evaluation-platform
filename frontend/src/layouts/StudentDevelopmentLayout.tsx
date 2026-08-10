import { useEffect, useMemo, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { getStudentDevelopmentConfig } from '../features/development/api/developmentApi'
import type { OrganizationBrand } from '../features/development/types/development'
import { useStudentAuth } from '../features/students/context/StudentAuthContext'

const navigation = [
  ['/student', 'Mi Desarrollo'],
  ['/student/study', 'Estudiar'],
  ['/student/evaluations', 'Mis evaluaciones'],
  ['/student/paths', 'Mis Paths'],
  ['/student/certifications', 'Mis certificaciones'],
  ['/student/progress', 'Mi progreso']
] as const

export function StudentDevelopmentLayout() {
  const { student, logout } = useStudentAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [brand, setBrand] = useState<OrganizationBrand | null>(null)
  const [certificationsEnabled, setCertificationsEnabled] = useState(false)

  useEffect(() => {
    let active = true
    setCertificationsEnabled(false)
    void getStudentDevelopmentConfig().then((value) => {
      if (!active) return
      setBrand(value.branding)
      setCertificationsEnabled(value.certificationsEnabled)
    }).catch(() => {
      if (active && student) {
        setBrand({ global: false, organizationPublicId: student.organizationPublicId, name: student.organizationName, hasLogo: false, logoUrl: null })
        setCertificationsEnabled(false)
      }
    })
    return () => { active = false }
  }, [student])

  const section = useMemo(() => {
    if (location.pathname.includes('/study')) return 'Estudiar'
    if (location.pathname.includes('/evaluations')) return 'Mis evaluaciones'
    if (location.pathname.includes('/paths')) return 'Mis Paths'
    if (location.pathname.includes('/certifications')) return 'Mis certificaciones'
    if (location.pathname.includes('/progress')) return 'Mi progreso'
    if (location.pathname.includes('/change-password')) return 'Cambiar contraseña'
    return 'Mi Desarrollo'
  }, [location.pathname])

  useEffect(() => {
    document.title = `${section} | ${brand?.name ?? student?.organizationName ?? 'Mi Desarrollo'}`
  }, [brand?.name, section, student?.organizationName])

  if (!student) return null
  const brandName = brand?.name ?? student.organizationName
  const initials = brandName.split(/\s+/).filter(Boolean).slice(0, 2).map((item) => item.charAt(0)).join('').toUpperCase()

  async function handleLogout() {
    await logout()
    navigate('/student-login', { replace: true })
  }

  return (
    <div className="development-shell">
      <header className="development-header">
        <NavLink to="/student" className="development-brand" aria-label={`Ir a Mi Desarrollo de ${brandName}`}>
          {brand?.hasLogo && brand.logoUrl
            ? <img src={brand.logoUrl} alt={`Logotipo de ${brandName}`} />
            : <span className="development-brand-fallback" aria-hidden="true">{initials || 'MD'}</span>}
          <span><strong>{brandName}</strong><small>Mi Desarrollo</small></span>
        </NavLink>
        <nav className="development-nav" aria-label="Navegación de Mi Desarrollo">
          {navigation.map(([to, label]) => {
            if (to === '/student/certifications' && !certificationsEnabled) return null
            return <NavLink key={to} to={to} end={to === '/student'} className={({ isActive }) => isActive ? 'active' : undefined}>{label}</NavLink>
          })}
        </nav>
        <div className="development-account">
          <button className="development-avatar" onClick={() => navigate('/student/change-password')} aria-label="Perfil y seguridad">{student.firstName.charAt(0)}{student.lastName.charAt(0)}</button>
          <button className="development-logout" onClick={() => void handleLogout()}>Salir</button>
        </div>
      </header>
      <main className="development-main"><Outlet /></main>
    </div>
  )
}
