import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { changeQuestionStatus, deleteQuestion, duplicateQuestion, getQuestion, restoreQuestion } from '../features/questions/api/questionApi'
import { ApiRequestError } from '../shared/api/apiClient'
import { ConfirmDialog } from '../shared/components/ConfirmDialog'
import { Icon } from '../shared/components/Icon'
import { LoadingScreen } from '../shared/components/LoadingScreen'
import { useToast } from '../shared/components/ToastProvider'
import type { QuestionDetail } from '../shared/types/questions'

type Action='STATUS'|'DELETE'|'RESTORE'|null
export function AdminQuestionDetailPage(){
 const {publicId=''}=useParams();const navigate=useNavigate();const toast=useToast();const[question,setQuestion]=useState<QuestionDetail>();
 const[action,setAction]=useState<Action>(null);const[error,setError]=useState<string>();const[reloadKey,setReloadKey]=useState(0);const[busy,setBusy]=useState(false)
 const reload=useCallback(()=>setReloadKey(v=>v+1),[])
 useEffect(()=>{const controller=new AbortController();setError(undefined);getQuestion(publicId,controller.signal).then(setQuestion).catch((e:unknown)=>{if(!controller.signal.aborted)setError(e instanceof ApiRequestError?e.message:'No fue posible consultar la pregunta.')});return()=>controller.abort()},[publicId,reloadKey])
 if(error&&!question)return <main className="content-page"><section className="inline-error-panel"><div className="inline-error-icon"><Icon name="error"/></div><div><strong>No fue posible cargar la pregunta</strong><p>{error}</p></div><button className="secondary-button" onClick={reload}>Reintentar</button></section></main>
 if(!question)return <LoadingScreen/>
 const deleted=question.status==='DELETED'
 async function execute(){if(!action||!question)return;setBusy(true);try{let updated:QuestionDetail
   if(action==='DELETE')updated=await deleteQuestion(publicId,question.entityVersion,'Eliminación administrativa')
   else if(action==='RESTORE')updated=await restoreQuestion(publicId,question.entityVersion)
   else updated=await changeQuestionStatus(publicId,question.status==='ACTIVE'?'ARCHIVED':'ACTIVE',question.entityVersion)
   setQuestion(updated);setAction(null);toast.success(action==='DELETE'?'Pregunta eliminada':action==='RESTORE'?'Pregunta restaurada como archivada':updated.status==='ACTIVE'?'Pregunta reactivada':'Pregunta archivada')
 }catch(e){toast.error('No fue posible completar la operación',e instanceof ApiRequestError?e.message:undefined)}finally{setBusy(false)}}
 async function duplicate(){setBusy(true);try{const copy=await duplicateQuestion(publicId);toast.success('Pregunta duplicada');navigate(`/admin/questions/${copy.publicId}/edit`)}catch(e){toast.error('No fue posible duplicar la pregunta',e instanceof ApiRequestError?e.message:undefined)}finally{setBusy(false)}}
 const title=action==='DELETE'?'Eliminar pregunta':action==='RESTORE'?'Restaurar pregunta':question.status==='ACTIVE'?'Archivar pregunta':'Reactivar pregunta'
 const description=action==='DELETE'?'Desaparecerá del banco normal y de las colecciones, pero el registro permanecerá almacenado.':action==='RESTORE'?'La pregunta volverá como archivada. Después podrás reactivarla.':question.status==='ACTIVE'?'Dejará de estar disponible para nuevas colecciones y formularios.':'Volverá a estar disponible.'
 return <main className="content-page narrow-content resource-page"><div className="page-heading resource-heading"><div><p className="eyebrow">Pregunta</p><h1>Detalle</h1></div>
  <div className="heading-actions resource-heading-actions">{!deleted&&<><button className="secondary-button" disabled={busy} onClick={()=>void duplicate()}><Icon name="copy" size={15}/>Duplicar</button>
   {question.status==='ACTIVE'&&<Link className="primary-button button-link" to={`/admin/questions/${publicId}/edit`}><Icon name="edit" size={15}/>Editar</Link>}
   <button className="secondary-button" disabled={busy} onClick={()=>setAction('STATUS')}>{question.status==='ACTIVE'?'Archivar':'Reactivar'}</button>
   <button className="danger-button" disabled={busy} onClick={()=>setAction('DELETE')}>Eliminar</button></>}
   {deleted&&<button className="primary-button" disabled={busy} onClick={()=>setAction('RESTORE')}>Restaurar</button>}</div></div>
  <section className={`detail-card question-preview ${deleted?'deleted-detail':''}`}><div className="question-card-top"><span className={`status-badge status-${question.status.toLowerCase()}`}>{question.status==='ACTIVE'?'Activa':question.status==='ARCHIVED'?'Archivada':'Eliminada'}</span><span>{question.typeName} · {question.difficultyName}</span></div>
   <h2>{question.statement}</h2>{question.promptMedia&&<img className="question-prompt-image" src={question.promptMedia.url} alt={question.promptMedia.originalName}/>} {question.codeContent&&<pre className="code-preview"><code>{question.codeContent}</code></pre>}
   <div className="chip-row">{question.categories.map(c=><span className="category-chip" key={c.publicId}>{c.name}</span>)}</div>
   {question.options.length>0&&<div className="answer-preview-list">{question.options.map(o=><div className={o.correct?'correct':''} key={o.publicId}><span>{o.correct?'✓':'○'}</span><div>{o.text&&<p>{o.text}</p>}{o.media&&<img src={o.media.url} alt={o.media.originalName}/>}</div></div>)}</div>}
   {question.explanation&&<div className="explanation-box"><strong>Explicación</strong><p>{question.explanation}</p></div>}</section>
  <ConfirmDialog open={action!==null} title={title} description={description} confirmLabel={action==='DELETE'?'Eliminar':action==='RESTORE'?'Restaurar':question.status==='ACTIVE'?'Archivar':'Reactivar'} tone={action==='DELETE'||question.status==='ACTIVE'?'danger':'primary'} onCancel={()=>setAction(null)} onConfirm={()=>void execute()}/>
 </main>
}
