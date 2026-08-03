import { Link } from 'react-router-dom'
import { BackButton } from '../shared/components/BackButton'
import { Icon } from '../shared/components/Icon'

export function TalentTypeSelectionPage() {
  return <main className="content-page editor-page talent-type-selection-page">
    <BackButton fallback="/admin/talent-bank" />
    <section className="editor-card talent-type-selection">
      <div className="section-heading"><div><p className="eyebrow">Talent Bank</p><h1>¿Qué tipo de talento deseas registrar?</h1><p className="muted">Selecciona el flujo correspondiente. La organización y los permisos se validarán desde tu sesión.</p></div></div>
      <div className="talent-type-cards">
        <Link className="talent-type-card" to="/admin/talent-bank/new/academy"><Icon name="clipboard" size={28} /><strong>Academia</strong><span>Formulario simplificado para talento en formación.</span></Link>
        <Link className="talent-type-card" to="/admin/talent-bank/new/prospect"><Icon name="users" size={28} /><strong>Prospecto de colaborador</strong><span>Utiliza el formulario completo de Colaboradores.</span></Link>
      </div>
    </section>
  </main>
}
