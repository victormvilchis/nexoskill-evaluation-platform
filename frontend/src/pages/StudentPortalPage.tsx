import { useNavigate } from 'react-router-dom'
import { useStudentAuth } from '../features/students/context/StudentAuthContext'

export function StudentPortalPage() {
  const navigate = useNavigate()
  const { student, logout } = useStudentAuth()
  if (!student) return null
  async function handleLogout() {
    await logout()
    navigate('/student-login', { replace: true })
  }
  return (
    <main className="student-portal-page">
      <header className="student-portal-header">
        <div><strong>NexoSkill</strong><span>{student.organizationName}</span></div>
        <button className="secondary-button" onClick={() => void handleLogout()}>Cerrar sesión</button>
      </header>
      <section className="student-welcome-card">
        <p className="eyebrow">Portal de colaboradores</p>
        <h1>Hola, {student.firstName}</h1>
        <p>Tu cuenta está activa. Las asignaciones, avances y resultados se habilitarán en la Parte 5.</p>
        <dl className="student-portal-metadata">
          <div><dt>Organización</dt><dd>{student.organizationName}</dd></div>
          <div><dt>Código</dt><dd>{student.studentCode}</dd></div>
          <div><dt>Correo</dt><dd>{student.email}</dd></div>
          <div><dt>Vigencia</dt><dd>{student.expiresAt ? new Intl.DateTimeFormat('es-MX', { dateStyle: 'long' }).format(new Date(student.expiresAt)) : 'Sin vencimiento'}</dd></div>
        </dl>
      </section>
    </main>
  )
}
