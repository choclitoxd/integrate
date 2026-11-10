import { useRef, useState, type SubmitEvent } from 'react';
import { useSearchParams } from 'react-router';

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

import { EmptyRow, Notice, PageHeader, Pager, StatusBadge, TableFrame } from '../components/parts';
import { CAPTION_CLASS, TABLE_CLASS, TD_CLASS, TH_CLASS } from '../components/tableStyles';
import {
  ASSOCIATION_FIELDS,
  associationServerErrors,
  emptyAssociation,
  validateAssociation,
  type AssociationDraft,
  type AssociationField,
} from '../forms';
import { ASSOCIATION_TYPES, associationTypeLabel } from '../labels';
import { useAssociations, useCreateAssociation } from '../queries';

const FORM_ID = 'nueva-asociacion';

interface FormProps {
  readonly onCreated: (legalName: string) => void;
}

/** Alta de asociación (ADR-0006 §3): valida en el cliente y traduce el 409 y el 422 del servidor. */
function AssociationForm({ onCreated }: FormProps) {
  const create = useCreateAssociation();
  const formRef = useRef<HTMLFormElement>(null);
  const [draft, setDraft] = useState<AssociationDraft>(emptyAssociation);
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

  function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const result = validateAssociation(draft);
    if (!result.ok) {
      setErrors(result.errors);
      focusFirst(result.errors);
      return;
    }
    setErrors({});
    create.mutate(result.body, {
      onSuccess: (association) => {
        onCreated(association.legalName);
      },
      onError: (error) => {
        if (error instanceof ApiError && error.status === 409) {
          const next = { nit: 'Ya existe una asociación registrada con este NIT.' };
          setErrors(next);
          focusFirst(next);
        } else if (error instanceof ApiError && error.invalidParams.length > 0) {
          const next = associationServerErrors(error.invalidParams);
          setErrors(next);
          focusFirst(next);
        }
      },
    });
  }

  const handled =
    create.error instanceof ApiError &&
    (create.error.status === 409 || Object.keys(errors).length > 0);

  return (
    <form
      id={FORM_ID}
      ref={formRef}
      noValidate
      onSubmit={handleSubmit}
      className="grid gap-5"
      aria-busy={create.isPending}
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
      {create.isError && !handled && <ProblemNotice error={create.error} />}
    </form>
  );
}

/** Asociaciones comunitarias: lista paginada y alta (ADR-0042 §2). */
export function AssociationsPage() {
  const [params, setParams] = useSearchParams();
  const page = Math.max(Number.parseInt(params.get('pagina') ?? '1', 10) || 1, 1) - 1;
  const associations = useAssociations(page);
  const [creating, setCreating] = useState(false);
  const [created, setCreated] = useState<string | null>(null);
  const items = associations.data?.items ?? [];

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
            Nueva asociación
          </Button>
        }
      />

      {created && (
        <div className="mt-4">
          <Notice tone="success">Se registró «{created}».</Notice>
        </div>
      )}

      <div className="mt-6">
        {associations.isError ? (
          <ProblemNotice
            error={associations.error}
            onRetry={() => {
              void associations.refetch();
            }}
          />
        ) : (
          <TableFrame>
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
                </tr>
              </thead>
              <tbody>
                {associations.isPending && (
                  <EmptyRow columns={7}>
                    <span role="status">Cargando asociaciones…</span>
                    <Skeleton className="mx-auto mt-3 h-6 w-2/3" />
                  </EmptyRow>
                )}
                {associations.isSuccess && items.length === 0 && (
                  <EmptyRow columns={7}>
                    Aún no hay asociaciones. Registra la primera con «Nueva asociación».
                  </EmptyRow>
                )}
                {items.map((item) => (
                  <tr key={item.id}>
                    <th scope="row" className={`${TD_CLASS} font-semibold`}>
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
              setParams(next > 0 ? { pagina: String(next + 1) } : {});
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
              Cancelar
            </Button>
            <Button type="submit" form={FORM_ID} className="flex-1">
              Registrar asociación
            </Button>
          </>
        }
      >
        <AssociationForm
          onCreated={(legalName) => {
            setCreating(false);
            setCreated(legalName);
          }}
        />
      </BottomSheet>
    </>
  );
}
