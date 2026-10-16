import { DOC_TYPES, NUMERIC_DOC_TYPES, type DocType } from '@/features/auth';
import { Select, TextField } from '@/shared/ui';

import type { TravelerDraft } from '../forms';

interface TravelerCardProps {
  readonly index: number;
  readonly total: number;
  readonly value: TravelerDraft;
  readonly onChange: (value: TravelerDraft) => void;
  readonly errors: Readonly<Record<string, string>>;
  readonly epsListId: string;
}

/** Un viajero por tarjeta (ADR-0015 §4). El primero es el titular de la reserva. */
export function TravelerCard({
  index,
  total,
  value,
  onChange,
  errors,
  epsListId,
}: TravelerCardProps) {
  const holder = index === 0;
  const prefix = `participants.${String(index)}`;
  const set = (patch: Partial<TravelerDraft>) => {
    onChange({ ...value, ...patch });
  };

  return (
    <fieldset className="grid gap-4 rounded-xl border-2 border-border-subtle px-4 pt-2 pb-4">
      <legend className="px-1 text-lg font-bold text-ink">
        Viajero {index + 1} de {total}
        {holder && <span className="font-semibold text-accent"> · titular</span>}
      </legend>
      <TextField
        label="Nombre completo"
        name={`${prefix}.fullName`}
        autoComplete={holder ? 'name' : 'off'}
        autoCapitalize="words"
        value={value.fullName}
        onChange={(event) => {
          set({ fullName: event.target.value });
        }}
        error={errors[`${prefix}.fullName`]}
      />
      <Select
        label="Tipo de documento"
        name={`${prefix}.docType`}
        size="lg"
        value={value.docType}
        options={DOC_TYPES}
        onChange={(next) => {
          set({ docType: next as DocType });
        }}
      />
      <TextField
        label="Número de documento"
        name={`${prefix}.docNumber`}
        inputMode={NUMERIC_DOC_TYPES.includes(value.docType) ? 'numeric' : 'text'}
        autoComplete="off"
        autoCapitalize="characters"
        spellCheck={false}
        value={value.docNumber}
        onChange={(event) => {
          set({ docNumber: event.target.value });
        }}
        error={errors[`${prefix}.docNumber`]}
      />
      <TextField
        label="EPS"
        hint="Para la póliza de turismo. Escribe la EPS o el régimen de salud."
        name={`${prefix}.medicalEps`}
        list={epsListId}
        autoComplete="off"
        value={value.medicalEps}
        onChange={(event) => {
          set({ medicalEps: event.target.value });
        }}
        error={errors[`${prefix}.medicalEps`]}
      />
    </fieldset>
  );
}
