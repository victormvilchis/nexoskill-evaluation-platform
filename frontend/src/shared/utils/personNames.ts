export function formatPersonName(value?: string | null) {
  const normalized = value?.trim().replace(/\s+/g, ' ')
  if (!normalized) return ''

  return normalized
    .toLocaleLowerCase('es-MX')
    .replace(/(^|[\s'-])\p{L}/gu, (letter) => letter.toLocaleUpperCase('es-MX'))
}
