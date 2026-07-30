import { useToast } from '../../../shared/components/ToastProvider'
import type { StudentTemporaryCredentials } from '../../../shared/types/students'

interface Props {
  title: string
  credentials: StudentTemporaryCredentials
  onClose: () => void
}

export function StudentTemporaryCredentialsDialog({ title, credentials, onClose }: Props) {
  const toast = useToast()

  async function copy(value: string, success: string) {
    try {
      await navigator.clipboard.writeText(value)
      toast.success(success)
    } catch {
      toast.warning('No fue posible copiar automáticamente', 'Selecciona el texto y cópialo manualmente.')
    }
  }

  const userAndPassword = `Correo: ${credentials.email}\nContraseña temporal: ${credentials.temporaryPassword}`
  const complete = `Organización: ${credentials.organizationLogin}\n${userAndPassword}`

  return (
    <div className="dialog-backdrop" role="presentation">
      <section className="confirm-dialog student-credentials-dialog" role="dialog" aria-modal="true"
        aria-labelledby="student-credentials-title">
        <div>
          <p className="eyebrow">Visualización única</p>
          <h2 id="student-credentials-title">{title}</h2>
          <p>La contraseña temporal se mostrará una sola vez. Compártela de forma segura. El colaborador deberá cambiarla durante su primer inicio de sesión.</p>
        </div>
        <div className="temporary-credentials">
          <div><span>Organización</span><code>{credentials.organizationLogin}</code>
            <button className="secondary-button" type="button"
              onClick={() => void copy(credentials.organizationLogin, 'Organización copiada.')}>Copiar organización</button>
          </div>
          <div><span>Correo</span><code>{credentials.email}</code>
            <button className="secondary-button" type="button"
              onClick={() => void copy(credentials.email, 'Correo copiado.')}>Copiar correo</button>
          </div>
          <div><span>Contraseña temporal</span><code>{credentials.temporaryPassword}</code>
            <button className="secondary-button" type="button"
              onClick={() => void copy(credentials.temporaryPassword, 'Contraseña copiada.')}>Copiar contraseña</button>
          </div>
        </div>
        <div className="dialog-actions student-credentials-actions">
          <button className="secondary-button" type="button"
            onClick={() => void copy(userAndPassword, 'Usuario y contraseña copiados.')}>Copiar usuario y contraseña</button>
          <button className="secondary-button" type="button"
            onClick={() => void copy(complete, 'Credenciales copiadas.')}>Copiar todos los datos</button>
          <button className="primary-button" type="button" onClick={onClose}>Cerrar</button>
        </div>
      </section>
    </div>
  )
}
