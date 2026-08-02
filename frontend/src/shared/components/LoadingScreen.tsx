import { BrandLogo } from './BrandLogo'

export function LoadingScreen() {
  return (
    <main className="centered-page valtieris-loading-screen" aria-live="polite">
      <BrandLogo variant="isotype" className="loading-brand-logo" decorative />
      <div className="loader" aria-hidden="true" />
      <p>Cargando Valtieris Talent Platform…</p>
    </main>
  )
}
