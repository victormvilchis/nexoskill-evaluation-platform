import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { getStudyOptions, startPractice } from '../features/development/api/developmentApi'
import type { StudyOptions } from '../features/development/types/development'
import { useToast } from '../shared/components/ToastProvider'

const modes = [
  ['QUICK','Práctica rápida','Responde preguntas y recibe feedback después de cada respuesta.'],
  ['SIMULATOR','Simulador','Una experiencia continua con resultado al final. No consume intentos.'],
  ['REVIEW_ERRORS','Repasar errores','Trabaja sobre preguntas que todavía necesitas reforzar.'],
  ['TOPIC','Estudiar por tema','Elige tecnología o categoría para enfocar tu práctica.']
] as const
export function StudentStudyPage() {
  const navigate = useNavigate(); const toast = useToast(); const [search] = useSearchParams()
  const [options,setOptions] = useState<StudyOptions|null>(null); const [mode,setMode] = useState(search.get('mode') ?? 'QUICK'); const [count,setCount]=useState(5); const [technology,setTechnology]=useState(''); const [category,setCategory]=useState(''); const [starting,setStarting]=useState(false)
  useEffect(()=>{ void getStudyOptions().then(setOptions).catch((e:unknown)=>toast.error('No fue posible cargar el Centro de estudio', e instanceof Error ? e.message : undefined)) },[toast])
  const selectedMode = useMemo(()=>modes.find(([value])=>value===mode) ?? modes[0],[mode])
  async function begin(){ setStarting(true); try { const session=await startPractice({mode,questionCount:count,technologyPublicId:technology||undefined,categoryPublicId:category||undefined}); navigate(`/student/study/practice/${session.publicId}`) } catch(e){ toast.error('No fue posible iniciar la práctica', e instanceof Error ? e.message : undefined) } finally { setStarting(false) } }
  return <div className="development-page"><header className="development-page-heading"><p className="development-eyebrow">Centro de estudio</p><h1>Estudiar</h1><p>Elige una forma sencilla de practicar. Ninguna actividad de este espacio consume intentos oficiales ni modifica tus certificaciones.</p></header>
    <div className="study-mode-grid">{modes.map(([value,title,description])=><button className={`study-mode-card ${mode===value?'selected':''}`} key={value} onClick={()=>setMode(value)}><span aria-hidden="true">{value==='QUICK'?'↗':value==='SIMULATOR'?'◎':value==='REVIEW_ERRORS'?'↻':'⌁'}</span><strong>{title}</strong><small>{description}</small>{value==='REVIEW_ERRORS' && options ? <em>{options.reviewErrorQuestions} disponibles</em>:null}</button>)}</div>
    <section className="development-card study-config"><div><p className="development-eyebrow">{selectedMode[1]}</p><h2>Configura tu sesión</h2><p>{selectedMode[2]}</p></div>
      <div className="study-config-grid"><label>Cantidad<select value={count} onChange={(e)=>setCount(Number(e.target.value))}><option value={5}>5 preguntas</option><option value={10}>10 preguntas</option><option value={20}>20 preguntas</option></select></label>
        {mode==='TOPIC' && <><label>Tecnología<select value={technology} onChange={(e)=>setTechnology(e.target.value)}><option value="">Todas</option>{options?.technologies.map((item)=><option key={item.value} value={item.value}>{item.label} ({item.count})</option>)}</select></label><label>Categoría<select value={category} onChange={(e)=>setCategory(e.target.value)}><option value="">Todas</option>{options?.categories.map((item)=><option key={item.value} value={item.value}>{item.label} ({item.count})</option>)}</select></label></>}
      </div>
      {mode==='SIMULATOR' && <div className="development-info-note"><strong>Modo práctica</strong><span>Este simulador no consume intentos ni modifica tus certificaciones.</span></div>}
      {mode==='REVIEW_ERRORS' && options?.reviewErrorQuestions===0 && <div className="development-info-note"><strong>No tienes errores pendientes por repasar.</strong><span>Realiza una práctica y esta opción se alimentará de tu propia actividad.</span></div>}
      <button className="development-primary" disabled={starting || (mode==='REVIEW_ERRORS' && options?.reviewErrorQuestions===0)} onClick={()=>void begin()}>{starting?'Preparando…':'Comenzar'}</button>
    </section>
  </div>
}
