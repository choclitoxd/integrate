import { useId, useRef, useState, type SubmitEvent } from 'react';

import type { FieldErrors } from '@/features/auth';
import { LocationPicker } from '@/shared/maps/LocationPicker';
import { parseCoordinate } from '@/shared/maps/googleMaps';
import { Button, Icon, ProblemNotice, Select, TextField } from '@/shared/ui';

import type { AdminExperience, ExperienceUpdateRequest } from '../api';
import {
  EXPERIENCE_FIELDS,
  emptyExperienceDraft,
  fromAdminExperience,
  validateAdminExperience,
  type ExperienceDraft,
  type ExperienceField,
} from '../forms';
import { INTENSITY_OPTIONS } from '../labels';
import { operationFieldClass } from './tableStyles';

interface ExperienceFormProps {
  /** Experiencia que se edita; `null` para un alta (ADR-0048 §2). */
  readonly initial: AdminExperience | null;
  readonly heading?: string;
  readonly ariaLabel: string;
  readonly submitLabel: string;
  readonly busyLabel: string;
  readonly pending: boolean;
  /** Error de la última solicitud al servidor, si lo hubo. */
  readonly error: unknown;
  /**
   * Recibe el cuerpo ya validado. `fail` marca los errores por campo que devuelva el servidor (422).
   * Devuelve `false` desde `beforeSubmit` para impedir el envío (p. ej. falta elegir el anfitrión).
   */
  readonly onSubmit: (
    body: ExperienceUpdateRequest,
    fail: (errors: FieldErrors<ExperienceField>) => void,
  ) => void;
  readonly beforeSubmit?: () => boolean;
}

/** Formulario de datos de una experiencia, compartido por la edición y el alta del administrador. */
export function ExperienceForm({
  initial,
  heading,
  ariaLabel,
  submitLabel,
  busyLabel,
  pending,
  error,
  onSubmit,
  beforeSubmit,
}: ExperienceFormProps) {
  const formRef = useRef<HTMLFormElement>(null);
  const descId = useId();
  const safetyId = useId();
  const [draft, setDraft] = useState<ExperienceDraft>(() =>
    initial ? fromAdminExperience(initial) : emptyExperienceDraft(),
  );
  const [errors, setErrors] = useState<FieldErrors<ExperienceField>>({});

  const set = (field: ExperienceField) => (value: string) => {
    setDraft((current) => ({ ...current, [field]: value }));
  };
  const input = (field: ExperienceField) => ({
    name: field,
    value: draft[field],
    error: errors[field],
    onChange: (event: React.ChangeEvent<HTMLInputElement>) => {
      set(field)(event.target.value);
    },
  });

  function focusFirst(next: FieldErrors<ExperienceField>) {
    const first = EXPERIENCE_FIELDS.find((field) => next[field]);
    if (first) (formRef.current?.elements.namedItem(first) as HTMLElement | null)?.focus();
  }

  function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const allowed = beforeSubmit ? beforeSubmit() : true;
    const validation = validateAdminExperience(draft, initial);
    if (!validation.ok) {
      setErrors(validation.errors);
      focusFirst(validation.errors);
      return;
    }
    if (!allowed) return;
    setErrors({});
    onSubmit(validation.body, (next) => {
      setErrors(next);
      focusFirst(next);
    });
  }

  return (
    <form
      ref={formRef}
      noValidate
      onSubmit={handleSubmit}
      aria-label={ariaLabel}
      aria-busy={pending}
      className="grid gap-4"
    >
      {heading && <h3 className="font-bold text-ink">{heading}</h3>}
      <TextField label="Título" {...input('title')} />
      <div className="grid grid-cols-2 gap-4">
        <TextField
          label="Precio por persona (COP)"
          inputMode="numeric"
          {...input('unitPriceCop')}
        />
        <TextField label="Cupo por fecha" inputMode="numeric" {...input('maxCapacityPerSlot')} />
      </div>
      <div className="grid grid-cols-2 gap-4">
        <TextField label="Duración (minutos)" inputMode="numeric" {...input('durationMinutes')} />
        <Select
          label="Intensidad física"
          name="physicalIntensity"
          size="lg"
          value={draft.physicalIntensity}
          options={INTENSITY_OPTIONS}
          onChange={(value) => {
            setDraft((current) => ({
              ...current,
              physicalIntensity: value as 'LOW' | 'MEDIUM' | 'HIGH',
            }));
          }}
        />
      </div>
      <div className="grid grid-cols-2 gap-4">
        <TextField
          label="Municipio (código DANE)"
          inputMode="numeric"
          maxLength={5}
          {...input('municipalityCode')}
        />
        <TextField label="Vereda (opcional)" {...input('vereda')} />
      </div>
      <TextField label="Dirección o punto de encuentro" {...input('meetingPoint')} />
      <LocationPicker
        lat={parseCoordinate(draft.latitude)}
        lng={parseCoordinate(draft.longitude)}
        onChange={(point) => {
          setDraft((current) => ({
            ...current,
            latitude: String(point.lat),
            longitude: String(point.lng),
            ...(point.address ? { meetingPoint: point.address } : {}),
          }));
        }}
      />
      <details className="rounded-lg border-2 border-border-subtle p-3">
        <summary className="min-h-11 cursor-pointer font-semibold text-ink">Avanzado</summary>
        <div className="mt-3 grid grid-cols-2 gap-4">
          <TextField label="Latitud (opcional)" inputMode="decimal" {...input('latitude')} />
          <TextField label="Longitud (opcional)" inputMode="decimal" {...input('longitude')} />
        </div>
      </details>
      <div>
        <label htmlFor={descId} className="block font-semibold text-ink">
          Descripción
        </label>
        <textarea
          id={descId}
          name="description"
          rows={3}
          value={draft.description}
          aria-invalid={errors.description ? true : undefined}
          onChange={(event) => {
            set('description')(event.target.value);
          }}
          className={`mt-1.5 ${operationFieldClass}`}
        />
        {errors.description && (
          <p className="mt-1 text-sm font-semibold text-danger-text">{errors.description}</p>
        )}
      </div>
      <div>
        <label htmlFor={safetyId} className="block font-semibold text-ink">
          Recomendaciones de seguridad
        </label>
        <textarea
          id={safetyId}
          name="safetyGuidelines"
          rows={2}
          value={draft.safetyGuidelines}
          aria-invalid={errors.safetyGuidelines ? true : undefined}
          onChange={(event) => {
            set('safetyGuidelines')(event.target.value);
          }}
          className={`mt-1.5 ${operationFieldClass}`}
        />
        {errors.safetyGuidelines && (
          <p className="mt-1 text-sm font-semibold text-danger-text">{errors.safetyGuidelines}</p>
        )}
      </div>
      {error !== null && error !== undefined && Object.keys(errors).length === 0 && (
        <ProblemNotice error={error} />
      )}
      <div className="flex flex-wrap gap-3">
        <Button type="submit" disabled={pending}>
          <Icon name="check" />
          {pending ? busyLabel : submitLabel}
        </Button>
      </div>
    </form>
  );
}
