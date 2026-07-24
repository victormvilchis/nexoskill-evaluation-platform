export function LoadingScreen() {
  return (
    <main className="centered-page" aria-live="polite">
      <div className="loader" aria-hidden="true" />
      <p>Validando sesión…</p>
    </main>
  )
}
