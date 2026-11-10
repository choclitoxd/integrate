import { useState } from 'react';
import { useSearchParams } from 'react-router';
import { useOnlineStatus } from '@/shared/offline';
import { BottomSheet, Button, Icon, ProblemNotice, Skeleton } from '@/shared/ui';
import { OperationConnection, OperationFilter, OperationTable } from '../components/OperationParts';
import { EmptyRow, Notice, PageHeader, Pager, TableSearchBar } from '../components/parts';
import { matchesSearchQuery, operationFieldClass, TD_CLASS } from '../components/tableStyles';
import { formatCop, formatInstant } from '../labels';
import {
  useOperationAction,
  useOperationDetail,
  useOperationList,
  type Incident,
} from '../operations';

const states = {
  OPEN: 'Abierto',
  IN_REVIEW: 'En revisión',
  RESOLVED: 'Resuelto',
  DISMISSED: 'Descartado',
};
const severities: Readonly<Record<string, string>> = { LOW: 'Baja', MEDIUM: 'Media', HIGH: 'Alta' };
const types: Readonly<Record<string, string>> = {
  OVER_REDEMPTION_COMMUNITY_PROTECTED: 'Canje de voucher revocado',
  DUPLICATE_OFFLINE_REDEMPTION: 'Canje duplicado',
  CLOCK_SKEW_FUTURE_SCAN: 'Escaneo con hora futura',
};

function IncidentDetail({ id, close }: { id: string; close: () => void }) {
  const detail = useOperationDetail<Incident>('incidents', id);
  const mutation = useOperationAction<Incident>('incidents', id);
  const online = useOnlineStatus();
  const [notes, setNotes] = useState('');
  const [decision, setDecision] = useState<'resolve' | 'dismiss' | null>(null);
  const incident = detail.data;
  return (
    <BottomSheet open onClose={close} title="Detalle del incidente">
      {detail.isPending && <Skeleton className="h-20" />}
      {detail.isError && (
        <ProblemNotice
          error={detail.error}
          onRetry={() => {
            void detail.refetch();
          }}
        />
      )}
      {incident && (
        <div className="grid gap-4">
          <dl className="grid gap-2 break-all">
            <div>
              <dt className="font-semibold">Estado</dt>
              <dd>{states[incident.status]}</dd>
            </div>
            <div>
              <dt className="font-semibold">Tipo</dt>
              <dd>{types[incident.type]}</dd>
            </div>
            <div>
              <dt className="font-semibold">Reserva</dt>
              <dd>{incident.bookingId}</dd>
            </div>
            <div>
              <dt className="font-semibold">Voucher</dt>
              <dd>{incident.voucherId}</dd>
            </div>
            <div>
              <dt className="font-semibold">Impacto estimado</dt>
              <dd>{formatCop(incident.financialImpactCop)}</dd>
            </div>
            {incident.resolvedAt && (
              <div>
                <dt className="font-semibold">Cierre</dt>
                <dd>
                  {formatInstant(incident.resolvedAt)} · {incident.resolvedBy}
                </dd>
              </div>
            )}
            {incident.resolutionNotes && (
              <div>
                <dt className="font-semibold">Nota de cierre</dt>
                <dd className="whitespace-pre-wrap">{incident.resolutionNotes}</dd>
              </div>
            )}
          </dl>
          <OperationConnection />
          {mutation.isError && (
            <ProblemNotice
              error={mutation.error}
              messages={{
                SYNC_INCIDENT_TRANSITION_CONFLICT:
                  'Otro proceso cambió el estado del caso. Revisa la ficha actualizada.',
              }}
            />
          )}
          {mutation.isSuccess && <Notice tone="success">Estado actualizado.</Notice>}
          {incident.status === 'OPEN' && (
            <Button
              disabled={!online || mutation.isPending}
              onClick={() => {
                mutation.mutate({ action: 'take' });
              }}
            >
              Tomar caso
            </Button>
          )}
          {incident.status === 'IN_REVIEW' && (
            <>
              <label className="grid gap-2 font-semibold">
                Nota de cierre
                <textarea
                  className={operationFieldClass}
                  maxLength={2000}
                  required
                  value={notes}
                  onChange={(event) => {
                    setNotes(event.target.value);
                    setDecision(null);
                  }}
                />
              </label>
              {decision ? (
                <>
                  <p role="status">
                    {decision === 'resolve' ? 'Resolver' : 'Descartar'} el caso {incident.id}
                  </p>
                  <Button
                    disabled={!online || mutation.isPending || !notes.trim()}
                    onClick={() => {
                      mutation.mutate(
                        { action: decision, notes: notes.trim() },
                        {
                          onSuccess: () => {
                            setDecision(null);
                          },
                        },
                      );
                    }}
                  >
                    Confirmar {decision === 'resolve' ? 'resolución' : 'descarte'}
                  </Button>
                  <Button
                    variant="secondary"
                    disabled={mutation.isPending}
                    onClick={() => {
                      setDecision(null);
                    }}
                  >
                    Cancelar
                  </Button>
                </>
              ) : (
                <>
                  <Button
                    disabled={!online || mutation.isPending || !notes.trim()}
                    onClick={() => {
                      setDecision('resolve');
                    }}
                  >
                    Resolver
                  </Button>
                  <Button
                    variant="secondary"
                    disabled={!online || mutation.isPending || !notes.trim()}
                    onClick={() => {
                      setDecision('dismiss');
                    }}
                  >
                    Descartar
                  </Button>
                </>
              )}
            </>
          )}
        </div>
      )}
    </BottomSheet>
  );
}

export function IncidentsPage() {
  const [params, setParams] = useSearchParams();
  const [page, setPage] = useState(0);
  const [id, setId] = useState('');
  const [query, setQuery] = useState('');
  const status = params.get('status') ?? '';
  const severity = params.get('severity') ?? '';
  const type = params.get('type') ?? '';
  const list = useOperationList<Incident>('incidents', { status, severity, type }, page);
  const filteredItems = (list.data?.items ?? []).filter((item) =>
    matchesSearchQuery(query, [
      item.id,
      types[item.type],
      states[item.status],
      severities[item.severity],
      item.bookingId,
      item.voucherId,
    ]),
  );
  function filter(key: string, value: string) {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    setParams(next);
    setPage(0);
  }
  return (
    <div className="grid min-w-0 gap-4">
      <title>Incidentes · Administración · Intégrate</title>
      <PageHeader
        title="Incidentes de sincronización"
        description="Revisa y cierra conflictos registrados durante la sincronización."
      />
      <div className="grid items-end gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <OperationFilter
          label="Estado"
          options={states}
          value={status}
          onChange={(value) => {
            filter('status', value);
          }}
        />
        <OperationFilter
          label="Severidad"
          options={severities}
          value={severity}
          onChange={(value) => {
            filter('severity', value);
          }}
        />
        <OperationFilter
          label="Tipo"
          options={types}
          value={type}
          onChange={(value) => {
            filter('type', value);
          }}
        />
        <div className="grid min-w-0 gap-2">
          <span className="font-semibold text-ink">Buscar</span>
          <TableSearchBar label="Buscar en incidentes" value={query} onChange={setQuery} />
        </div>
      </div>
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
            <p>No hay incidentes con estos filtros.</p>
          ) : (
            <OperationTable
              caption="Incidentes registrados"
              count={list.data.totalElements}
              headings={['Tipo', 'Estado', 'Severidad', 'Impacto', 'Fecha', 'Caso']}
            >
              {filteredItems.length === 0 && (
                <EmptyRow columns={6}>No hay incidentes con estos filtros.</EmptyRow>
              )}
              {filteredItems.map((item) => (
                <tr key={item.id}>
                  <td className={TD_CLASS}>{types[item.type]}</td>
                  <td className={TD_CLASS}>{states[item.status]}</td>
                  <td className={TD_CLASS}>{severities[item.severity]}</td>
                  <td className={TD_CLASS}>{formatCop(item.financialImpactCop)}</td>
                  <td className={TD_CLASS}>{formatInstant(item.createdAt)}</td>
                  <td className={TD_CLASS}>
                    <Button
                      variant="secondary"
                      aria-label={`Ver incidente ${item.id}`}
                      onClick={() => {
                        setId(item.id);
                      }}
                    >
                      <Icon name="eye" />
                      <span className="hidden sm:inline">Ver</span>
                    </Button>
                  </td>
                </tr>
              ))}
            </OperationTable>
          )}
          <Pager
            label="incidentes"
            page={page}
            totalPages={list.data.totalPages}
            totalElements={list.data.totalElements}
            onChange={setPage}
          />
        </>
      )}
      {id && (
        <IncidentDetail
          id={id}
          close={() => {
            setId('');
          }}
        />
      )}
    </div>
  );
}
