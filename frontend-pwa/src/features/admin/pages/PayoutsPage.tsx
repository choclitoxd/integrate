import { useState } from 'react';
import { useSearchParams } from 'react-router';
import { useOnlineStatus } from '@/shared/offline';
import { BottomSheet, Button, ProblemNotice, Skeleton } from '@/shared/ui';
import { OperationConnection, OperationFilter, OperationTable } from '../components/OperationParts';
import { Notice, PageHeader, Pager } from '../components/parts';
import { TD_CLASS } from '../components/tableStyles';
import { formatCop, formatInstant } from '../labels';
import {
  useOperationAction,
  useOperationDetail,
  useOperationList,
  useReceipt,
  type Batch,
  type PayoutDetail,
  type Reconciliation,
} from '../operations';
import { useAssociations } from '../queries';

const states = { OPEN: 'Abierto', SUBMITTED: 'Enviado', PAID: 'Pagado', FAILED: 'Fallido' };
const date = (value: string | null) => (value ? formatInstant(value) : 'Sin fecha');

function PayoutSheet({ id, close }: { id: string; close: () => void }) {
  const detail = useOperationDetail<PayoutDetail>('payout-batches', id);
  const reconcile = useOperationAction<Reconciliation>('payout-batches', id);
  const receipt = useReceipt(id);
  const online = useOnlineStatus();
  const batch = detail.data?.batch;
  const disbursements = detail.data?.disbursements ?? [];
  const result = reconcile.data;
  const error = reconcile.error ?? receipt.error;
  const items = [
    ['Estado', batch ? states[batch.status] : ''],
    ['Bruto', formatCop(batch?.grossCop ?? null)],
    ['Comisión de pasarela', formatCop(batch?.gatewayFeeCop ?? null)],
    ['Comisión de plataforma', formatCop(batch?.platformFeeCop ?? null)],
    ['Neto comunitario', formatCop(batch?.netCommunityCop ?? null)],
    ['Envío', date(batch?.submittedAt ?? null)],
    ['Pago', date(batch?.paidAt ?? null)],
  ];
  return (
    <BottomSheet open onClose={close} title="Detalle de liquidación">
      {detail.isPending && <Skeleton className="h-20" />}
      {detail.isError && (
        <ProblemNotice
          error={detail.error}
          onRetry={() => {
            void detail.refetch();
          }}
        />
      )}
      {batch && (
        <div className="grid min-w-0 gap-4">
          <h3 className="font-bold wrap-anywhere">{batch.associationName}</h3>
          <dl className="grid gap-2">
            {items.map(([label, value]) => (
              <div key={label}>
                <dt className="font-semibold">{label}</dt>
                <dd>{value}</dd>
              </div>
            ))}
            {batch.failureReason && (
              <div>
                <dt className="font-semibold">Motivo de fallo</dt>
                <dd className="wrap-anywhere">{batch.failureReason}</dd>
              </div>
            )}
            {batch.receiptId && (
              <div>
                <dt className="font-semibold">Comprobante</dt>
                <dd className="wrap-anywhere">{batch.receiptId}</dd>
              </div>
            )}
          </dl>
          {disbursements.length > 0 && (
            <OperationTable
              caption="Dispersiones"
              headings={['Identificador', 'Importe', 'Estado']}
            >
              {disbursements.map((item) => (
                <tr key={item.id}>
                  <td className={TD_CLASS}>{item.id}</td>
                  <td className={TD_CLASS}>{formatCop(item.amountCop)}</td>
                  <td className={TD_CLASS}>{item.status === 'PAID' ? 'Pagada' : 'Liberada'}</td>
                </tr>
              ))}
            </OperationTable>
          )}
          <OperationConnection />
          {error && <ProblemNotice error={error} />}
          {result?.lookupStatus === 'UNAVAILABLE' && (
            <Notice tone="warning">No se pudo verificar la referencia en la pasarela.</Notice>
          )}
          {result && result.lookupStatus !== 'UNAVAILABLE' && (
            <Notice tone="info">
              {result.batch.status === 'SUBMITTED'
                ? 'El pago sigue pendiente.'
                : `Estado del lote: ${states[result.batch.status]}.`}
            </Notice>
          )}
          {batch.status === 'SUBMITTED' && (
            <Button
              disabled={!online || reconcile.isPending}
              onClick={() => {
                reconcile.mutate({ action: 'reconcile' });
              }}
            >
              Conciliar lote
            </Button>
          )}
          {batch.status === 'FAILED' && (
            <Notice tone="warning">Reintento pendiente de definir.</Notice>
          )}
          {batch.receiptId && (
            <Button
              disabled={!online || receipt.isPending}
              onClick={() => {
                receipt.mutate(undefined, {
                  onSuccess: (data) => {
                    window.location.assign(data.url);
                  },
                });
              }}
            >
              Descargar comprobante
            </Button>
          )}
        </div>
      )}
    </BottomSheet>
  );
}

export function PayoutsPage() {
  const [params, setParams] = useSearchParams();
  const [page, setPage] = useState(0);
  const [associationPage, setAssociationPage] = useState(0);
  const [selectedAssociation, setSelectedAssociation] = useState<{ id: string; label: string }>();
  const [id, setId] = useState('');
  const status = params.get('status') ?? '';
  const associationId = params.get('associationId') ?? '';
  const options = useAssociations(associationPage);
  const associations = Object.fromEntries(
    (options.data?.items ?? []).map((item) => [item.id, item.legalName]),
  );
  if (associationId && !associations[associationId]) {
    associations[associationId] =
      selectedAssociation?.id === associationId ? selectedAssociation.label : associationId;
  }
  const list = useOperationList<Batch>('payout-batches', { status, associationId }, page);
  function filter(key: string, value: string) {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    setParams(next);
    setPage(0);
  }
  return (
    <div className="grid min-w-0 gap-4">
      <title>Liquidaciones · Administración · Intégrate</title>
      <PageHeader
        title="Liquidaciones"
        description="Consulta las dispersiones a las asociaciones y concilia lotes enviados."
      />
      <div className="grid gap-3 sm:grid-cols-2">
        <OperationFilter
          label="Estado"
          options={states}
          value={status}
          onChange={(value) => {
            filter('status', value);
          }}
        />
        <div className="grid min-w-0 gap-2">
          <OperationFilter
            label="Asociación"
            value={associationId}
            options={associations}
            onChange={(value) => {
              setSelectedAssociation({ id: value, label: associations[value] ?? value });
              filter('associationId', value);
            }}
          />
          {options.data && options.data.totalPages > 1 && (
            <Pager
              label="opciones de asociación"
              page={associationPage}
              totalPages={options.data.totalPages}
              totalElements={options.data.totalElements}
              onChange={setAssociationPage}
            />
          )}
        </div>
      </div>
      {options.isError && (
        <ProblemNotice
          error={options.error}
          onRetry={() => {
            void options.refetch();
          }}
        />
      )}
      {list.isPending && <Skeleton className="h-24" />}
      {list.isError && (
        <ProblemNotice
          error={list.error}
          onRetry={() => {
            void list.refetch();
          }}
        />
      )}
      {list.data && (
        <>
          {list.data.items.length === 0 ? (
            <p>No hay liquidaciones con estos filtros.</p>
          ) : (
            <OperationTable
              caption="Lotes de liquidación"
              headings={['Asociación', 'Estado', 'Neto', 'Envío', 'Lote']}
            >
              {list.data.items.map((item) => (
                <tr key={item.id}>
                  <td className={TD_CLASS}>{item.associationName}</td>
                  <td className={TD_CLASS}>{states[item.status]}</td>
                  <td className={TD_CLASS}>{formatCop(item.netCommunityCop)}</td>
                  <td className={TD_CLASS}>{date(item.submittedAt)}</td>
                  <td className={TD_CLASS}>
                    <Button
                      variant="secondary"
                      aria-label={`Ver lote ${item.id}`}
                      onClick={() => {
                        setId(item.id);
                      }}
                    >
                      Ver
                    </Button>
                  </td>
                </tr>
              ))}
            </OperationTable>
          )}
          <Pager
            label="liquidaciones"
            page={page}
            totalPages={list.data.totalPages}
            totalElements={list.data.totalElements}
            onChange={setPage}
          />
        </>
      )}
      {id && (
        <PayoutSheet
          id={id}
          close={() => {
            setId('');
          }}
        />
      )}
    </div>
  );
}
