import type { PreparationPoint } from '../types/development'
export function PreparationBars({ title, items, empty }: { title: string; items: PreparationPoint[]; empty: string }) {
  return <section className="development-card development-performance"><h2>{title}</h2>{items.length === 0 ? <p className="development-empty-inline">{empty}</p> : <div className="preparation-list">{items.map((item) => <div key={item.key} className="preparation-item"><div><strong>{item.label}</strong><span>{item.answered} respuestas</span></div><div className="preparation-value"><div className="preparation-track"><span style={{ width: `${Math.max(0, Math.min(item.percentage ?? 0, 100))}%` }} /></div><strong>{item.percentage}%</strong></div></div>)}</div>}</section>
}
