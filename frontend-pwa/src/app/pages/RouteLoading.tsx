/** Primera carga de una ruta diferida al abrir la app directamente en ella (ADR-0018 §1). */
export function RouteLoading() {
  return (
    <div className="flex min-h-dvh items-center justify-center bg-surface px-4">
      <p role="status" className="font-semibold text-ink-muted">
        Cargando…
      </p>
    </div>
  );
}
