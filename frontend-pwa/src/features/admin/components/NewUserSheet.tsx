import { useRef, useState, type SubmitEvent } from 'react';

import { DOC_TYPES, NUMERIC_DOC_TYPES, type DocType, type FieldErrors } from '@/features/auth';
import { ApiError } from '@/shared/api';
import {
  BottomSheet,
  Button,
  PasswordField,
  PhoneField,
  ProblemNotice,
  Select,
  TextField,
} from '@/shared/ui';

import {
  emptyUserDraft,
  USER_FIELDS,
  userServerErrors,
  validateUserDraft,
  type UserDraft,
  type UserField,
} from '../forms';
import { useCreateUser } from '../queries';

interface NewUserSheetProps {
  readonly onClose: () => void;
  readonly onCreated: (fullName: string) => void;
}

/** Alta administrativa de un usuario/cuenta desde la consola (ADR-0048 §2). */
export function NewUserSheet({ onClose, onCreated }: NewUserSheetProps) {
  const create = useCreateUser();
  const formRef = useRef<HTMLFormElement>(null);
  const [draft, setDraft] = useState<UserDraft>(emptyUserDraft);
  const [errors, setErrors] = useState<FieldErrors<UserField>>({});

  function setField<K extends UserField>(key: K, value: UserDraft[K]) {
    setDraft((prev) => ({ ...prev, [key]: value }));
    if (errors[key]) {
      setErrors((prev) => {
        const { [key]: _removed, ...rest } = prev;
        return rest;
      });
    }
  }

  function focusFirst(fields: FieldErrors<UserField>) {
    const first = USER_FIELDS.find((name) => Boolean(fields[name]));
    if (first) {
      (formRef.current?.elements.namedItem(first) as HTMLElement | null)?.focus();
    }
  }

  function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const result = validateUserDraft(draft);
    if (!result.ok) {
      setErrors(result.errors);
      focusFirst(result.errors);
      return;
    }

    create.mutate(result.body, {
      onSuccess: () => {
        onCreated(draft.fullName.trim());
      },
      onError: (error) => {
        if (error instanceof ApiError && error.invalidParams.length > 0) {
          const mapped = userServerErrors(error.invalidParams);
          setErrors(mapped);
          focusFirst(mapped);
        }
      },
    });
  }

  const docNumeric = NUMERIC_DOC_TYPES.includes(draft.docType);

  return (
    <BottomSheet open onClose={onClose} title="Nuevo usuario">
      <form ref={formRef} noValidate onSubmit={handleSubmit} className="grid gap-4">
        {create.isError &&
          !(create.error instanceof ApiError && create.error.invalidParams.length > 0) && (
            <ProblemNotice error={create.error} />
          )}

        <TextField
          label="Nombre completo"
          name="fullName"
          value={draft.fullName}
          error={errors.fullName}
          required
          maxLength={150}
          placeholder="Ej. María Elena Pérez"
          onChange={(event) => {
            setField('fullName', event.target.value);
          }}
        />

        <div className="grid gap-3 sm:grid-cols-[minmax(0,12rem)_minmax(0,1fr)]">
          <Select
            label="Tipo de documento"
            name="docType"
            value={draft.docType}
            options={DOC_TYPES}
            onChange={(value) => {
              setField('docType', value as DocType);
            }}
          />
          <TextField
            label="Número de documento"
            name="docNumber"
            value={draft.docNumber}
            error={errors.docNumber}
            required
            inputMode={docNumeric ? 'numeric' : 'text'}
            maxLength={20}
            placeholder={docNumeric ? '1020304050' : 'A1234567'}
            onChange={(event) => {
              setField('docNumber', event.target.value);
            }}
          />
        </div>

        <PhoneField
          label="Celular"
          name="phone"
          value={draft.phone}
          error={errors.phone}
          onChange={(phone) => {
            setField('phone', phone);
          }}
        />

        <TextField
          label="Correo electrónico"
          name="email"
          type="email"
          value={draft.email}
          error={errors.email}
          required
          maxLength={254}
          placeholder="usuario@ejemplo.co"
          onChange={(event) => {
            setField('email', event.target.value);
          }}
        />

        <PasswordField
          label="Contraseña"
          name="password"
          value={draft.password}
          error={errors.password}
          required
          hint="Debe tener al menos 12 caracteres."
          onChange={(event) => {
            setField('password', event.target.value);
          }}
        />

        <div className="mt-2 flex flex-wrap justify-end gap-3">
          <Button type="button" variant="secondary" onClick={onClose} disabled={create.isPending}>
            Cancelar
          </Button>
          <Button type="submit" variant="primary" disabled={create.isPending}>
            {create.isPending ? 'Guardando…' : 'Crear usuario'}
          </Button>
        </div>
      </form>
    </BottomSheet>
  );
}
