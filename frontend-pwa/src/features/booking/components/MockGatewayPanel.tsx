import { Button, Icon } from '@/shared/ui';

interface MockGatewayPanelProps {
  readonly busy: boolean;
  readonly onSimulate: (status: 'APPROVED' | 'DECLINED') => void;
}

/**
 * Solo aparece si el servidor devolvió `checkout.type = MOCK` (dev/test, ADR-0016 §3). Está
 * marcada como pasarela de pruebas para que nadie la confunda con un cobro real.
 */
export function MockGatewayPanel({ busy, onSimulate }: MockGatewayPanelProps) {
  return (
    <section
      aria-labelledby="pasarela-pruebas"
      className="mt-6 rounded-xl border-2 border-dashed border-info-border bg-info-bg px-4 py-4"
    >
      <h2 id="pasarela-pruebas" className="flex items-center gap-2 font-bold text-info-text">
        <Icon name="alert" />
        Pasarela de pruebas (entorno de desarrollo)
      </h2>
      <p className="mt-1 text-sm text-ink">
        No se cobra dinero. Simula la respuesta de la pasarela para continuar.
      </p>
      <div className="mt-3 grid gap-2">
        <Button
          disabled={busy}
          onClick={() => {
            onSimulate('APPROVED');
          }}
        >
          Simular pago aprobado
        </Button>
        <Button
          variant="secondary"
          disabled={busy}
          onClick={() => {
            onSimulate('DECLINED');
          }}
        >
          Simular pago rechazado
        </Button>
      </div>
    </section>
  );
}
