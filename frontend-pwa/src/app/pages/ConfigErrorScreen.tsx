interface ConfigErrorScreenProps {
  readonly message: string;
}

/** Fail-fast visible: la app no arranca con configuración inválida (SEC-DIR-004 §4). */
export function ConfigErrorScreen({ message }: ConfigErrorScreenProps) {
  return (
    <main className="mx-auto max-w-2xl px-4 py-12">
      <h1 className="text-3xl font-bold text-brand">Configuración incompleta</h1>
      <p role="alert" className="mt-4 text-lg text-ink">
        {message}
      </p>
    </main>
  );
}
