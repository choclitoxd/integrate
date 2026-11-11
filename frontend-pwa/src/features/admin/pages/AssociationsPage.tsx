import { useRef, useState, type SubmitEvent } from 'react';
import { Link, useSearchParams } from 'react-router';

import type { FieldErrors } from '@/features/auth';
import { ApiError } from '@/shared/api';
import { formatPhoneDisplay } from '@/shared/lib';
import {
  BottomSheet,
  Button,
  Icon,
  PhoneField,
  ProblemNotice,
  Select,
  Skeleton,
  TextField,
} from '@/shared/ui';

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
import {
  ASSOCIATION_FIELDS,
  associationServerErrors,
  emptyAssociation,
  fromAssociation,
  validateAssociation,
  type AssociationDraft,
  type AssociationField,
} from '../forms';
import { ASSOCIATION_FILTERS, ASSOCIATION_TYPES, associationTypeLabel } from '../labels';
import type { Association } from '../api';
import {
  useAssociations,
  useChangeAssociationStatus,
  useCreateAssociation,
  useUpdateAssociation,
} from '../queries';

const FORM_ID = 'nueva-asociacion';
const EDIT_FORM_ID = 'editar-asociacion';
const ALL_STATUS = 'TODAS';
const VALID_STATUS = new Set<string>(['ACTIVE', 'SUSPENDED']);

function associationStatusFrom(raw: string | null): 'ACTIVE' | 'SUSPENDED' | null {
  return raw !== null && VALID_STATUS.has(raw) ? (raw as 'ACTIVE' | 'SUSPENDED') : null;
}

interface FormProps {
  readonly formId?: string;
  readonly initial?: Association;
  readonly onSaved: (legalName: string) => void;
}

/** Alta o edición de asociación (ADR-0006 §3, ADR-0047 §2): valida en el cliente y traduce el 409 y el 422 del servidor. */
function AssociationForm({ formId = FORM_ID, initial, onSaved }: FormProps) {
  const create = useCreateAssociation();
  const update = useUpdateAssociation();
  const isBusy = create.isPending || update.isPending;
  const activeError = initial ? update.error : create.error;
  const isError = initial ? update.isError : create.isError;
  const formRef = useRef<HTMLFormElement>(null);
  const [draft, setDraft] = useState<AssociationDraft>(() =>
    initial ? fromAssociation(initial) : emptyAssociation(),
  );
  const [errors, setErrors] = useState<FieldErrors<AssociationField>>({});

  const set = (field: AssociationField) => (value: string) => {
    setDraft((current) => ({ ...current, [field]: value }));
  };
  const input = (field: AssociationField) => ({
    name: field,
    value: draft[field],
    error: errors[field],
    onChange: (event: React.ChangeEvent<HTMLInputElement>) => {
      set(field)(event.target.value);
    },
  });

  function focusFirst(next: FieldErrors<AssociationField>) {
    const first = ASSOCIATION_FIELDS.find((field) => next[field]);
    if (first) (formRef.current?.elements.namedItem(first) as HTMLElement | null)?.focus();
  }

  function handleError(error: unknown) {
    if (error instanceof ApiError && error.status === 409) {
      const next = { nit: 'Ya existe una asociación registrada con este NIT.' };
      setErrors(next);
      focusFirst(next);
    } else if (error instanceof ApiError && error.invalidParams.length > 0) {
      const next = associationServerErrors(error.invalidParams);
      setErrors(next);
      focusFirst(next);
    }
  }

  function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const result = validateAssociation(draft);
    if (!result.ok) {
      setErrors(result.errors);
      focusFirst(result.errors);
      return;
    }
    setErrors({});
    if (initial) {
      update.mutate(
        { id: initial.id, body: result.body },
        {
          onSuccess: (association) => {
            onSaved(association.legalName);
          },
          onError: handleError,
        },
      );
      return;
    }
    create.mutate(result.body, {
      onSuccess: (association) => {
        onSaved(association.legalName);
      },
      onError: handleError,
    });
  }

  const handled =
    activeError instanceof ApiError &&
    (activeError.status === 409 || Object.keys(errors).length > 0);

  return (
    <form
      id={formId}
      ref={formRef}
      noValidate
      onSubmit={handleSubmit}
      className="grid gap-5"
      aria-busy={isBusy}
    >
      <TextField label="Razón social" autoComplete="organization" {...input('legalName')} />
      <div className="grid grid-cols-2 gap-4">
        <TextField
          label="NIT"
          hint="Con dígito de verificación: 900123456-7"
          inputMode="numeric"
          autoComplete="off"
          {...input('nit')}
        />
        <TextField
          label="Registro Nacional de Turismo"
          hint="Solo números"
          inputMode="numeric"
          autoComplete="off"
          {...input('rntNumber')}
        />
      </div>
      <Select
        label="Tipo de asociación"
        name="associationType"
        size="lg"
        value={draft.associationType}
        options={ASSOCIATION_TYPES}
        invalid={Boolean(errors.associationType)}
        onChange={set('associationType')}
      />
      <div className="grid grid-cols-2 gap-4">
        <TextField
          label="Municipio (código DANE)"
          hint="5 dígitos, por ejemplo 05001"
          inputMode="numeric"
          autoComplete="off"
          maxLength={5}
          {...input('municipalityCode')}
        />
        <TextField
          label="Dirección (opcional)"
          hint="Vereda, corregimiento o dirección"
          autoComplete="street-address"
          {...input('address')}
        />
      </div>
      <TextField
        label="Correo de contacto"
        type="email"
        inputMode="email"
        autoComplete="email"
        autoCapitalize="none"
        spellCheck={false}
        {...input('contactEmail')}
      />
      <PhoneField
        label="Celular de contacto"
        name="contactPhone"
        value={draft.contactPhone}
        error={errors.contactPhone}
        onChange={set('contactPhone')}
      />
      {isError && !handled && <ProblemNotice error={activeError} />}
    </form>
  );
}

/** Asociaciones comunitarias: lista paginada, alta y edición en ventana emergente (ADR-0042 §2, ADR-0047 §2). */
export function AssociationsPage() {
  const [params, setParams] = useSearchParams();
  const statusFilter = associationStatusFrom(params.get('estado'));
  const statusParam = statusFilter ?? ALL_STATUS;
  const page = Math.max(Number.parseInt(params.get('pagina') ?? '1', 10) || 1, 1) - 1;
  const associations = useAssociations(page);
  const changeStatus = useChangeAssociationStatus();
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<Association | null>(null);
  const [created, setCreated] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const allItems = associations.data?.items ?? [];
  const items = allItems.filter(
    (item) =>
      (statusFilter === null || item.status === statusFilter) &&
      matchesSearchQuery(query, [
        item.legalName,
        item.nit,
        item.rntNumber,
        associationTypeLabel(item.associationType),
        item.municipalityCode,
        item.contactEmail,
        item.contactPhone,
        item.status === 'ACTIVE' ? 'Activa' : 'Suspendida',
      ]),
  );

  const hrefFor = (next: { estado?: string; pagina?: number }) => {
    const queryParams = new URLSearchParams();
    const targetStatus = next.estado ?? statusParam;
    if (targetStatus !== ALL_STATUS) queryParams.set('estado', targetStatus);
    const nextPage = next.pagina ?? (next.estado === undefined ? page + 1 : 1);
    if (nextPage > 1) queryParams.set('pagina', String(nextPage));
    const search = queryParams.toString();
    return search ? `?${search}` : '';
  };

  return (
    <>
      <title>Asociaciones · Administración · Intégrate</title>
      <PageHeader
        title="Asociaciones"
        description="Organizaciones comunitarias a las que pertenecen los anfitriones y los gestores de enlace."
        actions={
          <Button
            onClick={() => {
              setCreated(null);
              setCreating(true);
            }}
          >
            <Icon name="plus" />
            <span className="hidden sm:inline">Nueva asociación</span>
          </Button>
        }
      />

      {created && (
        <div className="mt-4">
          <Notice tone="success">{created}</Notice>
        </div>
      )}

      <div className="mt-6 flex flex-wrap items-center justify-between gap-3">
        <nav aria-label="Filtrar asociaciones por estado">
          <ul className="flex flex-wrap gap-2">
            {ASSOCIATION_FILTERS.map((filter) => {
              const value = filter.value ?? ALL_STATUS;
              const current = value === statusParam;
              return (
                <li key={value}>
                  <Link
                    to={hrefFor({ estado: value })}
                    aria-current={current ? 'true' : undefined}
                    className="inline-flex min-h-11 items-center rounded-full border-2 border-border bg-canvas px-4 font-semibold text-ink hover:bg-surface-subtle aria-[current=true]:border-brand aria-[current=true]:bg-brand aria-[current=true]:text-on-brand"
                  >
                    {filter.text}
                  </Link>
                </li>
              );
            })}
          </ul>
        </nav>
        <div className="w-full sm:max-w-xs">
          <TableSearchBar label="Buscar en asociaciones" value={query} onChange={setQuery} />
        </div>
      </div>

      <div className="mt-4">
        {associations.isError ? (
          <ProblemNotice
            error={associations.error}
            onRetry={() => {
              void associations.refetch();
            }}
          />
        ) : (
          <TableFrame title="Asociaciones registradas" count={associations.data?.totalElements}>
            <table className={TABLE_CLASS}>
              <caption className={CAPTION_CLASS}>
                Asociaciones registradas
                {associations.data && (
                  <span className="font-normal text-ink-muted">
                    {' '}
                    · {associations.data.totalElements}
                  </span>
                )}
              </caption>
              <thead>
                <tr>
                  <th scope="col" className={TH_CLASS}>
                    Razón social
                  </th>
                  <th scope="col" className={TH_CLASS}>
                    NIT
                  </th>
                  <th scope="col" className={TH_CLASS}>
                    RNT
                  </th>
                  <th scope="col" className={TH_CLASS}>
                    Tipo
                  </th>
                  <th scope="col" className={TH_CLASS}>
                    Municipio
                  </th>
                  <th scope="col" className={TH_CLASS}>
                    Contacto
                  </th>
                  <th scope="col" className={TH_CLASS}>
                    Estado
                  </th>
                  <th scope="col" className={TH_CLASS}>
                    Acciones
                  </th>
                </tr>
              </thead>
              <tbody>
                {associations.isPending && (
                  <EmptyRow columns={8}>
                    <span role="status">Cargando asociaciones…</span>
                    <Skeleton className="mx-auto mt-3 h-6 w-2/3" />
                  </EmptyRow>
                )}
                {associations.isSuccess && items.length === 0 && (
                  <EmptyRow columns={8}>
                    {allItems.length === 0
                      ? 'Aún no hay asociaciones. Registra la primera con «Nueva asociación».'
                      : 'No hay asociaciones que coincidan con este filtro.'}
                  </EmptyRow>
                )}
                {items.map((item) => (
                  <tr key={item.id}>
                    <th scope="row" className={`${TD_CLASS} font-semibold text-ink`}>
                      {item.legalName}
                    </th>
                    <td className={`${TD_CLASS} whitespace-nowrap tabular-nums`}>{item.nit}</td>
                    <td className={`${TD_CLASS} tabular-nums`}>{item.rntNumber}</td>
                    <td className={TD_CLASS}>{associationTypeLabel(item.associationType)}</td>
                    <td className={`${TD_CLASS} tabular-nums`}>{item.municipalityCode}</td>
                    <td className={TD_CLASS}>
                      <span className="break-all">{item.contactEmail}</span>
                      <br />
                      <span className="whitespace-nowrap text-ink-muted">
                        {formatPhoneDisplay(item.contactPhone)}
                      </span>
                    </td>
                    <td className={TD_CLASS}>
                      <StatusBadge tone={item.status === 'ACTIVE' ? 'success' : 'danger'}>
                        {item.status === 'ACTIVE' ? 'Activa' : 'Suspendida'}
                      </StatusBadge>
                    </td>
                    <td className={TD_CLASS}>
                      <Button
                        variant="secondary"
                        aria-label={`Gestionar ${item.legalName}`}
                        onClick={() => {
                          setCreated(null);
                          setEditing(item);
                        }}
                      >
                        <Icon name="edit" />
                        <span className="hidden sm:inline">Gestionar</span>
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </TableFrame>
        )}
        {associations.data && (
          <Pager
            label="asociaciones"
            page={associations.data.page}
            totalPages={associations.data.totalPages}
            totalElements={associations.data.totalElements}
            onChange={(next) => {
              setParams(hrefFor({ pagina: next + 1 }).slice(1));
            }}
          />
        )}
      </div>

      <BottomSheet
        open={creating}
        onClose={() => {
          setCreating(false);
        }}
        title="Nueva asociación"
        footer={
          <>
            <Button
              variant="secondary"
              className="flex-1"
              onClick={() => {
                setCreating(false);
              }}
            >
              <Icon name="close" />
              Cancelar
            </Button>
            <Button type="submit" form={FORM_ID} className="flex-1">
              <Icon name="check" />
              Registrar asociación
            </Button>
          </>
        }
      >
        <AssociationForm
          onSaved={(legalName) => {
            setCreating(false);
            setCreated(`Se registró «${legalName}».`);
          }}
        />
      </BottomSheet>

      {editing && (
        <BottomSheet
          open
          onClose={() => {
            setEditing(null);
          }}
          title={editing.legalName}
          footer={
            <>
              <Button
                variant="secondary"
                className="flex-1"
                onClick={() => {
                  setEditing(null);
                }}
              >
                <Icon name="close" />
                Cancelar
              </Button>
              <Button type="submit" form={EDIT_FORM_ID} className="flex-1">
                <Icon name="check" />
                Guardar cambios
              </Button>
            </>
          }
        >
          <div className="grid gap-5">
            <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg bg-surface p-3">
              <div className="flex items-center gap-2">
                <span className="text-sm font-semibold text-ink-muted">Estado actual:</span>
                <StatusBadge tone={editing.status === 'ACTIVE' ? 'success' : 'danger'}>
                  {editing.status === 'ACTIVE' ? 'Activa' : 'Suspendida'}
                </StatusBadge>
              </div>
              <Button
                variant="secondary"
                disabled={changeStatus.isPending}
                onClick={() => {
                  const action = editing.status === 'ACTIVE' ? 'suspend' : 'activate';
                  changeStatus.mutate(
                    { id: editing.id, action },
                    {
                      onSuccess: (updated) => {
                        setEditing(null);
                        setCreated(
                          updated.status === 'ACTIVE'
                            ? `Se reactivó «${updated.legalName}».`
                            : `Se suspendió «${updated.legalName}».`,
                        );
                      },
                    },
                  );
                }}
              >
                <Icon name={editing.status === 'ACTIVE' ? 'pause' : 'refresh'} />
                {editing.status === 'ACTIVE' ? 'Suspender asociación' : 'Activar asociación'}
              </Button>
            </div>
            <AssociationForm
              key={editing.id}
              formId={EDIT_FORM_ID}
              initial={editing}
              onSaved={(legalName) => {
                setEditing(null);
                setCreated(`Se actualizaron los datos de «${legalName}».`);
              }}
            />
          </div>
        </BottomSheet>
      )}
    </>
  );
}
