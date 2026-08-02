interface BrandLogoProps {
  variant?: 'horizontal' | 'isotype'
  className?: string
  decorative?: boolean
}

const assets = {
  horizontal: 'brand/valtieris-talent-platform.png',
  isotype: 'brand/valtieris-isotype.png'
} as const

export function BrandLogo({
  variant = 'horizontal',
  className = '',
  decorative = false
}: BrandLogoProps) {
  const classes = ['valtieris-brand-logo', `valtieris-brand-logo--${variant}`, className]
    .filter(Boolean)
    .join(' ')

  return (
    <img
      alt={decorative ? '' : 'Valtieris Talent Platform'}
      aria-hidden={decorative || undefined}
      className={classes}
      src={`${import.meta.env.BASE_URL}${assets[variant]}`}
    />
  )
}
