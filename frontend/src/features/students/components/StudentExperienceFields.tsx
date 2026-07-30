import type { ExperienceLevel, StudentExperienceItem, StudentExperiencePayload } from '../types/studentImport'

interface Props {
  value: StudentExperiencePayload
  onChange: (next: StudentExperiencePayload) => void
  readOnly: boolean
  disabled?: boolean
}

type ListKey = 'currentTechnologies' | 'languages' | 'knownTechnologies'

const LEVELS: ExperienceLevel[] = ['', 'JR', 'STD', 'SR']

function blankItem(): StudentExperienceItem {
  return { name: '', level: '' }
}

export function StudentExperienceFields({ value, onChange, readOnly, disabled }: Props) {
  function setList(key: ListKey, items: StudentExperienceItem[]) {
    onChange({ ...value, [key]: items })
  }

  function updateItem(key: ListKey, index: number, field: 'name' | 'level', nextValue: string) {
    setList(key, value[key].map((item, itemIndex) => {
      if (itemIndex !== index) return item
      return field === 'level'
        ? { ...item, level: nextValue as ExperienceLevel }
        : { ...item, name: nextValue }
    }))
  }

  function section(title: string, help: string, key: ListKey) {
    const items = value[key]
    return (
      <div className="student-experience-group">
        <div className="student-experience-heading">
          <div><h3>{title}</h3><p className="muted">{help}</p></div>
          {!readOnly && <button type="button" className="secondary-button compact-button"
            disabled={disabled} onClick={() => setList(key, [...items, blankItem()])}>Agregar</button>}
        </div>
        {items.length === 0 && <p className="muted">Sin información registrada.</p>}
        {items.map((item, index) => readOnly ? (
          <div className="student-experience-readonly" key={`${key}-${index}`}>
            <strong>{item.name}</strong><span>{item.level || 'Nivel no especificado'}</span>
          </div>
        ) : (
          <div className="student-experience-row" key={`${key}-${index}`}>
            <input aria-label={`${title} ${index + 1}`} value={item.name} disabled={disabled}
              placeholder="Nombre" onChange={(event) => updateItem(key, index, 'name', event.target.value)} />
            <select aria-label={`Nivel de ${item.name || title}`} value={item.level ?? ''} disabled={disabled}
              onChange={(event) => updateItem(key, index, 'level', event.target.value)}>
              {LEVELS.map((level) => <option key={level || 'NONE'} value={level}>{level || 'Sin nivel'}</option>)}
            </select>
            <button type="button" className="danger-button compact-button" disabled={disabled}
              onClick={() => setList(key, items.filter((_, itemIndex) => itemIndex !== index))}>Quitar</button>
          </div>
        ))}
      </div>
    )
  }

  return (
    <section className="editor-card student-experience-card">
      <div className="section-heading"><div><p className="eyebrow">Experiencia</p><h2>Experiencia y conocimientos</h2></div></div>
      <p className="muted">Estos datos son independientes de las certificaciones y no cambian el perfil ni la tecnología principal.</p>
      {section('Tecnología actual y expertise', 'Ejemplos: JAVA — STD, APX — JR.', 'currentTechnologies')}
      {section('Lenguajes', 'Lenguajes de programación con nivel opcional.', 'languages')}
      {section('Tecnologías conocidas', 'Herramientas y tecnologías adicionales con nivel opcional.', 'knownTechnologies')}
    </section>
  )
}
