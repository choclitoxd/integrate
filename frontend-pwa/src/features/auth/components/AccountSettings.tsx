import { useRef, useState, type SubmitEvent } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router';
import { ApiError, useHttpClient } from '@/shared/api';
import { countPendingOutbox } from '@/shared/offline';
import { useSession, useSessionStore } from '@/shared/session';
import {
  BottomSheet,
  Button,
  PasswordField,
  PhoneField,
  ProblemNotice,
  TextField,
} from '@/shared/ui';
import { changePassword, reauthenticate, updateAccountProfile, type AccountProfile } from '../api';
import { normalizePhone, phoneSchema, serverFieldErrors, type FieldErrors } from '../forms';
import { authKeys } from '../queries';

type ProfileField = 'fullName' | 'phone';
type PasswordFieldName = 'currentPassword' | 'newPassword' | 'confirmPassword';

/**
 * Ventana emergente para editar nombre y celular (ADR-0062 enmendado): no pide contraseña.
 * El contenido solo se monta abierto, así que cada apertura parte de los datos vigentes.
 */
export function ProfileSheet({
  open,
  profile,
  onClose,
  onSaved,
}: {
  readonly open: boolean;
  readonly profile: AccountProfile;
  readonly onClose: () => void;
  readonly onSaved: () => void;
}) {
  return (
    <BottomSheet open={open} onClose={onClose} title="Editar mis datos">
      <ProfileForm initial={profile} onSaved={onSaved} onCancel={onClose} />
    </BottomSheet>
  );
}

/** Ventana emergente para cambiar la contraseña: lo único que pide la contraseña actual (ADR-0062). */
export function PasswordSheet({
  open,
  onClose,
}: {
  readonly open: boolean;
  readonly onClose: () => void;
}) {
  return (
    <BottomSheet open={open} onClose={onClose} title="Cambiar contraseña">
      <PasswordForm onCancel={onClose} />
    </BottomSheet>
  );
}

function ProfileForm({
  initial,
  onSaved,
  onCancel,
}: {
  readonly initial: AccountProfile;
  readonly onSaved: () => void;
  readonly onCancel: () => void;
}) {
  const http = useHttpClient();
  const store = useSessionStore();
  const queryClient = useQueryClient();
  const session = useSession();
  const ref = useRef<HTMLFormElement>(null);
  const [fullName, setFullName] = useState(initial.fullName);
  const [phone, setPhone] = useState(initial.phone);
  const [errors, setErrors] = useState<FieldErrors<ProfileField>>({});
  const [serverError, setServerError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  function showErrors(next: FieldErrors<ProfileField>) {
    setErrors(next);
    const first = (['fullName', 'phone'] as const).find((field) => next[field]);
    if (first) (ref.current?.elements.namedItem(first) as HTMLElement | null)?.focus();
  }
  async function submit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    setServerError(null);
    const next: FieldErrors<ProfileField> = {};
    if (fullName.trim().length < 3 || fullName.trim().length > 150)
      next.fullName = 'Escribe un nombre de 3 a 150 caracteres.';
    const parsed = phoneSchema.safeParse(phone);
    if (!parsed.success) next.phone = parsed.error.issues[0]?.message ?? 'Revisa el celular.';
    if (Object.keys(next).length) {
      showErrors(next);
      return;
    }
    setBusy(true);
    try {
      const updated = await updateAccountProfile(http, {
        fullName: fullName.trim(),
        phone: normalizePhone(phone),
      });
      if (session.status === 'authenticated')
        queryClient.setQueryData(authKeys.profile(session.user.id), updated);
      store.updateFullName(updated.fullName);
      onSaved();
    } catch (error) {
      if (error instanceof ApiError && error.status === 422) {
        const mapped = serverFieldErrors(error.invalidParams, ['fullName', 'phone'] as const);
        showErrors(mapped.fields);
      }
      setServerError(error);
    } finally {
      setBusy(false);
    }
  }
  return (
    <form
      ref={ref}
      onSubmit={(event) => {
        void submit(event);
      }}
      noValidate
      className="mt-3 grid gap-4"
    >
      <TextField
        name="fullName"
        label="Nombre completo"
        autoComplete="name"
        value={fullName}
        onChange={(event) => {
          setFullName(event.target.value);
        }}
        error={errors.fullName}
      />
      <PhoneField
        name="phone"
        label="Celular"
        value={phone}
        onChange={setPhone}
        error={errors.phone}
      />
      {serverError !== null && <ProblemNotice error={serverError} />}
      <Button type="submit" disabled={busy}>
        {busy ? 'Guardando…' : 'Guardar datos'}
      </Button>
      <Button type="button" variant="secondary" onClick={onCancel}>
        Cancelar
      </Button>
    </form>
  );
}

function PasswordForm({ onCancel }: { readonly onCancel: () => void }) {
  const http = useHttpClient();
  const store = useSessionStore();
  const navigate = useNavigate();
  const ref = useRef<HTMLFormElement>(null);
  const [errors, setErrors] = useState<FieldErrors<PasswordFieldName>>({});
  const [serverError, setServerError] = useState<unknown>(null);
  const [pending, setPending] = useState(false);
  const [busy, setBusy] = useState(false);
  function showErrors(next: FieldErrors<PasswordFieldName>) {
    setErrors(next);
    const first = (['currentPassword', 'newPassword', 'confirmPassword'] as const).find(
      (field) => next[field],
    );
    if (first) (ref.current?.elements.namedItem(first) as HTMLElement | null)?.focus();
  }
  async function submit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const currentValue = data.get('currentPassword');
    const newValue = data.get('newPassword');
    const confirmValue = data.get('confirmPassword');
    const currentPassword = typeof currentValue === 'string' ? currentValue : '';
    const newPassword = typeof newValue === 'string' ? newValue : '';
    const confirmation = typeof confirmValue === 'string' ? confirmValue : '';
    const next: FieldErrors<PasswordFieldName> = {};
    if (!currentPassword) next.currentPassword = 'Escribe tu contraseña actual.';
    if (
      Array.from(newPassword).length < 12 ||
      Array.from(newPassword).length > 128 ||
      newPassword.includes('\0')
    )
      next.newPassword = 'Usa entre 12 y 128 caracteres.';
    if (confirmation !== newPassword || !confirmation)
      next.confirmPassword = 'Las contraseñas no coinciden.';
    setServerError(null);
    setPending(false);
    if (Object.keys(next).length) {
      showErrors(next);
      return;
    }
    setBusy(true);
    try {
      if ((await countPendingOutbox()) > 0) {
        setPending(true);
        return;
      }
      // La contraseña actual se confirma aquí mismo: no hay una ventana previa (ADR-0062).
      const grant = await reauthenticate(http, currentPassword);
      await changePassword(http, { currentPassword, newPassword }, grant.reauthToken);
      await store.finishPasswordChange();
      void navigate('/ingresar', {
        replace: true,
        state: { notice: 'Tu contraseña cambió. Ingresa de nuevo.' },
      });
    } catch (error) {
      if (error instanceof ApiError && error.status === 422) {
        // `/reauth` señala la contraseña actual como `password`.
        const mapped = serverFieldErrors(
          error.invalidParams,
          ['currentPassword', 'newPassword'] as const,
          { password: 'currentPassword' },
        );
        showErrors(
          Object.keys(mapped.fields).length
            ? mapped.fields
            : { currentPassword: 'Revisa tu contraseña actual.' },
        );
      }
      setServerError(error);
    } finally {
      setBusy(false);
    }
  }
  return (
    <form
      ref={ref}
      onSubmit={(event) => {
        void submit(event);
      }}
      noValidate
      className="mt-3 grid gap-4"
    >
      <p className="text-ink-muted">
        Al cambiarla, tendrás que volver a ingresar en tus dispositivos.
      </p>
      <PasswordField
        name="currentPassword"
        label="Contraseña actual"
        autoComplete="current-password"
        error={errors.currentPassword}
      />
      <PasswordField
        name="newPassword"
        label="Contraseña nueva"
        autoComplete="new-password"
        hint="Usa entre 12 y 128 caracteres."
        error={errors.newPassword}
      />
      <PasswordField
        name="confirmPassword"
        label="Confirmar contraseña nueva"
        autoComplete="new-password"
        error={errors.confirmPassword}
      />
      {pending && (
        <div
          role="alert"
          className="rounded-xl border-2 border-danger-border bg-danger-bg p-4 text-danger-text"
        >
          <p>
            Hay asistencias pendientes de sincronizar. Sincronízalas antes de cambiar tu contraseña.
          </p>
          <Link className="mt-2 inline-flex min-h-11 items-center underline" to="/gestor/cola">
            Ver sincronización
          </Link>
        </div>
      )}
      {serverError !== null && <ProblemNotice error={serverError} />}
      <Button type="submit" disabled={busy}>
        {busy ? 'Cambiando…' : 'Cambiar contraseña'}
      </Button>
      <Button type="button" variant="secondary" onClick={onCancel}>
        Cancelar
      </Button>
    </form>
  );
}
