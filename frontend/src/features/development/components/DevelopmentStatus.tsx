export function DevelopmentStatus({ value, label }: { value: string; label?: string }) {
  const tone = value === 'PENDING_DEACTIVATION' || value === 'ATTENTION_PRIORITY' || value === 'EXPIRED'
    ? 'critical' : value === 'EXPIRING_SOON' || value === 'REQUIRES_ATTENTION'
      ? 'attention' : value === 'CONTINUE' ? 'progress' : 'ok'
  return <span className={`development-status ${tone}`}><span aria-hidden="true" />{label ?? value}</span>
}
