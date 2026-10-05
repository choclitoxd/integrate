import { Link } from 'react-router';

export function NotFoundPage() {
  return (
    <>
      <title>Página no encontrada · Intégrate</title>
      <h1 className="text-3xl font-bold text-brand">Página no encontrada</h1>
      <p className="mt-4 text-lg text-ink-muted">La dirección que buscas no existe o fue movida.</p>
      <p className="mt-6">
        <Link to="/" className="font-semibold text-brand underline underline-offset-4">
          Volver al inicio
        </Link>
      </p>
    </>
  );
}
