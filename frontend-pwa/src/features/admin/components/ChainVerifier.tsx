import { Button, Icon, ProblemNotice } from '@/shared/ui';

import { useVerifyAudit } from '../queries';
import { Notice } from './parts';

/**
 * Verificación de la cadena de hashes de la bitácora (ADR-0035 §1). Se ejecuta a pedido: la propia
 * verificación queda registrada, así que no se dispara sola al abrir la pantalla.
 */
export function ChainVerifier() {
  const verify = useVerifyAudit();
  const result = verify.data;

  return (
    <section
      aria-labelledby="cadena-titulo"
      className="rounded-xl border-2 border-border-subtle bg-canvas px-5 py-5"
    >
      <h2 id="cadena-titulo" className="text-xl font-bold text-ink">
        Integridad de la bitácora
      </h2>
      <p className="mt-1 text-ink-muted">
        Comprueba que ningún registro de auditoría fue alterado o eliminado.
      </p>
      <div className="mt-4 grid gap-4">
        {result?.intact === true && (
          <Notice tone="success">
            La cadena está íntegra: {result.checked} registros revisados, sin eslabones rotos.
          </Notice>
        )}
        {result?.intact === false && (
          <Notice tone="danger" role="alert">
            La cadena está rota
            {result.brokenId !== null && <> en el registro n.º {result.brokenId}</>}. Trátalo como
            un incidente de seguridad y no modifiques la base de datos.
          </Notice>
        )}
        {verify.isError && <ProblemNotice error={verify.error} />}
        <div>
          <Button
            variant="secondary"
            disabled={verify.isPending}
            onClick={() => {
              verify.mutate();
            }}
          >
            <Icon name="shield" />
            {verify.isPending ? 'Verificando…' : 'Verificar la cadena'}
          </Button>
        </div>
      </div>
    </section>
  );
}
