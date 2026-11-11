import { useState, type SubmitEvent } from 'react';
import { useSearchParams } from 'react-router';

import { useHttpClient } from '@/shared/api';
import { todayInBogota } from '@/shared/lib';
import { Button, Icon, ProblemNotice, Select, Skeleton, TextField } from '@/shared/ui';

import { exportAudit, type AuditFilters, type AuditRecord, type AuditSeverity } from '../api';
import { ChainVerifier } from '../components/ChainVerifier';
import {
  EmptyRow,
  Notice,
  PageHeader,
  Pager,
  StatusBadge,
  TableFrame,
  TableSearchBar,
} from '../components/parts';
import {
  CAPTION_CLASS,
  matchesSearchQuery,
  TABLE_CLASS,
  TD_CLASS,
  TH_CLASS,
} from '../components/tableStyles';
import { formatInstant, SEVERITY, shortId } from '../labels';
import { useAuditSearch } from '../queries';

const SEVERITIES: readonly AuditSeverity[] = ['INFO', 'WARNING', 'HIGH', 'CRITICAL'];
const SEVERITY_OPTIONS = [
  { value: '', label: 'Todas' },
  ...SEVERITIES.map((value) => ({ value, label: SEVERITY[value].text })),
];
const MODULE = /^[a-z]{1,20}$/;
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

/** Filtros desde la URL; lo que no cumple el contrato se descarta en vez de provocar un 400. */
function filtersFrom(params: URLSearchParams): AuditFilters {
  const module = params.get('modulo') ?? '';
  const action = (params.get('accion') ?? '').slice(0, 60);
  const severity = params.get('severidad');
  const from = params.get('desde') ?? '';
  const to = params.get('hasta') ?? '';
  return {
    ...(MODULE.test(module) && { module }),
    ...(action !== '' && { action }),
    ...(SEVERITIES.some((value) => value === severity) && {
      severity: severity as AuditSeverity,
    }),
    ...(ISO_DATE.test(from) && { from }),
    ...(ISO_DATE.test(to) && { to }),
  };
}

function detailText(value: unknown): string {
  if (value === null || value === undefined) return '—';
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  return JSON.stringify(value);
}

function RecordDetails({ record }: { record: AuditRecord }) {
  const entries = Object.entries(record.details ?? {});
  return (
    <details>
      <summary className="inline-flex min-h-11 cursor-pointer items-center font-semibold text-brand underline underline-offset-4">
        Ver detalle<span className="sr-only"> del registro n.º {record.id}</span>
      </summary>
      <dl className="mt-1 grid gap-1 text-sm">
        <div>
          <dt className="inline font-semibold">Dirección IP: </dt>
          <dd className="inline break-all">{record.actorIp ?? '—'}</dd>
        </div>
        <div>
          <dt className="inline font-semibold">Navegador: </dt>
          <dd className="inline break-all">{record.userAgent ?? '—'}</dd>
        </div>
        {entries.map(([key, value]) => (
          <div key={key}>
            <dt className="inline font-semibold">{key}: </dt>
            <dd className="inline break-all">{detailText(value)}</dd>
          </div>
        ))}
      </dl>
    </details>
  );
}

/** Entrega el CSV al navegador como descarga, sin abrir otra pestaña. */
function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

/** Bitácora de auditoría: filtros, exportación y verificación de la cadena (ADR-0035, ADR-0042). */
export function AuditPage() {
  const http = useHttpClient();
  const [params, setParams] = useSearchParams();
  const filters = filtersFrom(params);
  const page = Math.max(Number.parseInt(params.get('pagina') ?? '1', 10) || 1, 1) - 1;
  const audit = useAuditSearch(filters, page);
  const [severity, setSeverity] = useState<string>(filters.severity ?? '');
  const [moduleError, setModuleError] = useState<string | undefined>();
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<unknown>(null);
  const [query, setQuery] = useState('');
  // Tras "Limpiar" el formulario se vuelve a montar con los campos vacíos.
  const [formKey, setFormKey] = useState(0);
  const allItems = audit.data?.items ?? [];
  const items = allItems.filter((record) =>
    matchesSearchQuery(query, [
      record.id,
      record.module,
      record.action,
      SEVERITY[record.severity].text,
      record.entityName,
      record.entityId,
      record.actorId ?? 'Sistema',
    ]),
  );

  function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const text = (name: string) => {
      const value = data.get(name);
      return typeof value === 'string' ? value.trim() : '';
    };
    const module = text('modulo').toLowerCase();
    if (module !== '' && !MODULE.test(module)) {
      setModuleError('El módulo va en minúsculas y sin espacios, por ejemplo: booking.');
      (event.currentTarget.elements.namedItem('modulo') as HTMLElement | null)?.focus();
      return;
    }
    setModuleError(undefined);
    const next = new URLSearchParams();
    if (module) next.set('modulo', module);
    if (text('accion')) next.set('accion', text('accion'));
    if (severity) next.set('severidad', severity);
    if (text('desde')) next.set('desde', text('desde'));
    if (text('hasta')) next.set('hasta', text('hasta'));
    setParams(next);
  }

  async function handleExport() {
    setExporting(true);
    setExportError(null);
    try {
      const blob = await exportAudit(http, filters);
      saveBlob(blob, `auditoria-${todayInBogota()}.csv`);
    } catch (error) {
      setExportError(error);
    } finally {
      setExporting(false);
    }
  }

  return (
    <>
      <title>Auditoría · Administración · Intégrate</title>
      <PageHeader
        title="Auditoría"
        description="Bitácora de lo que ocurre en la plataforma. Los datos sensibles se guardan ya redactados."
        actions={
          <Button
            variant="secondary"
            disabled={exporting}
            onClick={() => {
              void handleExport();
            }}
          >
            <Icon name="download" />
            {exporting ? 'Preparando…' : 'Exportar CSV'}
          </Button>
        }
      />

      <div className="mt-4">
        <Notice tone="info" role="note">
          Cada consulta, exportación y verificación que hagas aquí queda registrada en esta misma
          bitácora.
        </Notice>
      </div>
      {exportError !== null && (
        <div className="mt-4">
          <ProblemNotice error={exportError} />
        </div>
      )}

      <form
        key={formKey}
        noValidate
        onSubmit={handleSubmit}
        aria-label="Filtrar la bitácora"
        className="mt-6 grid grid-cols-1 items-end gap-4 rounded-xl bg-surface px-5 py-5 sm:grid-cols-2 xl:grid-cols-5"
      >
        <TextField
          label="Módulo"
          name="modulo"
          hint="Por ejemplo: booking"
          defaultValue={filters.module ?? ''}
          autoCapitalize="none"
          spellCheck={false}
          maxLength={20}
          error={moduleError}
        />
        <TextField
          label="Acción"
          name="accion"
          hint="Por ejemplo: AUDIT_QUERIED"
          defaultValue={filters.action ?? ''}
          autoCapitalize="characters"
          spellCheck={false}
          maxLength={60}
        />
        <div>
          <Select
            label="Severidad"
            size="lg"
            value={severity}
            options={SEVERITY_OPTIONS}
            onChange={setSeverity}
          />
        </div>
        <TextField label="Desde" name="desde" type="date" defaultValue={filters.from ?? ''} />
        <TextField label="Hasta" name="hasta" type="date" defaultValue={filters.to ?? ''} />
        <div className="col-span-full flex flex-wrap gap-3">
          <Button type="submit">
            <Icon name="filters" />
            Aplicar filtros
          </Button>
          <Button
            variant="secondary"
            onClick={() => {
              setSeverity('');
              setModuleError(undefined);
              setFormKey((key) => key + 1);
              setParams({});
            }}
          >
            Limpiar
          </Button>
        </div>
      </form>

      <div className="mt-6 flex justify-end">
        <div className="w-full sm:max-w-xs">
          <TableSearchBar label="Buscar en bitácora" value={query} onChange={setQuery} />
        </div>
      </div>

      <div className="mt-4">
        {audit.isError ? (
          <ProblemNotice
            error={audit.error}
            onRetry={() => {
              void audit.refetch();
            }}
          />
        ) : (
          <TableFrame
            title="Registros, del más reciente al más antiguo"
            count={audit.data?.totalElements}
          >
            <table className={TABLE_CLASS}>
              <caption className={CAPTION_CLASS}>
                Registros, del más reciente al más antiguo
                {audit.data && (
                  <span className="font-normal text-ink-muted"> · {audit.data.totalElements}</span>
                )}
              </caption>
              <thead>
                <tr>
                  <th scope="col" className={TH_CLASS}>
                    Fecha
                  </th>
                  <th scope="col" className={TH_CLASS}>
                    Módulo
                  </th>
                  <th scope="col" className={TH_CLASS}>
                    Acción
                  </th>
                  <th scope="col" className={TH_CLASS}>
                    Severidad
                  </th>
                  <th scope="col" className={TH_CLASS}>
                    Entidad
                  </th>
                  <th scope="col" className={TH_CLASS}>
                    Quién
                  </th>
                  <th scope="col" className={TH_CLASS}>
                    Detalle
                  </th>
                </tr>
              </thead>
              <tbody>
                {audit.isPending && (
                  <EmptyRow columns={7}>
                    <span role="status">Cargando la bitácora…</span>
                    <Skeleton className="mx-auto mt-3 h-6 w-2/3" />
                  </EmptyRow>
                )}
                {audit.isSuccess && items.length === 0 && (
                  <EmptyRow columns={7}>Ningún registro coincide con estos filtros.</EmptyRow>
                )}
                {items.map((record) => {
                  const severityLabel = SEVERITY[record.severity];
                  return (
                    <tr key={record.id}>
                      <td className={`${TD_CLASS} text-sm whitespace-nowrap tabular-nums`}>
                        {formatInstant(record.createdAt)}
                      </td>
                      <td className={TD_CLASS}>{record.module}</td>
                      <th scope="row" className={`${TD_CLASS} font-mono text-sm font-semibold`}>
                        {record.action}
                      </th>
                      <td className={TD_CLASS}>
                        <StatusBadge tone={severityLabel.tone}>{severityLabel.text}</StatusBadge>
                      </td>
                      <td className={`${TD_CLASS} text-sm`}>
                        {record.entityName ?? '—'}
                        {record.entityId && (
                          <span className="block font-mono text-ink-muted" title={record.entityId}>
                            {shortId(record.entityId)}
                          </span>
                        )}
                      </td>
                      <td className={`${TD_CLASS} font-mono text-sm`}>
                        {record.actorId ? (
                          <span title={record.actorId}>{shortId(record.actorId)}</span>
                        ) : (
                          <span className="font-sans">Sistema</span>
                        )}
                      </td>
                      <td className={TD_CLASS}>
                        <RecordDetails record={record} />
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </TableFrame>
        )}
        {audit.data && (
          <Pager
            label="registros"
            page={audit.data.page}
            totalPages={audit.data.totalPages}
            totalElements={audit.data.totalElements}
            onChange={(next) => {
              const query = new URLSearchParams(params);
              if (next > 0) query.set('pagina', String(next + 1));
              else query.delete('pagina');
              setParams(query);
            }}
          />
        )}
      </div>

      <div className="mt-8 max-w-2xl">
        <ChainVerifier />
      </div>
    </>
  );
}
