import { Link } from 'react-router-dom'
import { useAuth } from '../features/authentication/context/AuthContext'
import { Icon } from '../shared/components/Icon'
import { authorizedHome } from '../shared/utils/authorizedHome'

export function AccessDeniedPage() {
  const { user } = useAuth()
  const home = authorizedHome(user)
  return (
    <main className="content-page centered-page">
      <Icon name="lock" size={32} />
      <h1>Acceso no disponible</h1>
      <p>No tienes permisos para consultar esta sección.</p>
      {home !== '/access-denied' && <Link className="primary-button" to={home}>Ir a una sección disponible</Link>}
    </main>
  )
}
