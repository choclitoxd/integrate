import { useEffect, useRef, useState, type SubmitEvent } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router';
import { ApiError, useHttpClient } from '@/shared/api';
import { formatPhoneDisplay } from '@/shared/lib';
import { countPendingOutbox } from '@/shared/offline';
import { useSession, useSessionStore } from '@/shared/session';
import { Button, PasswordField, PhoneField, ProblemNotice, TextField } from '@/shared/ui';
import {
  changePassword,
  updateAccountProfile,
  type AccountProfile,
  type ReauthGrant,
} from '../api';
import { normalizePhone, phoneSchema, serverFieldErrors, type FieldErrors } from '../forms';
import { authKeys, useAccountProfile } from '../queries';
import { ReauthSheet } from './ReauthSheet';

type ProfileField = 'fullName' | 'phone';
type PasswordFieldName = 'currentPassword' | 'newPassword' | 'confirmPassword';
type Target = 'profile' | 'password';

/** Tope local de la confirmación: el servidor la vence a los 5 minutos aunque el reloj del equipo difiera. */
const MAX_WINDOW_MS = 5 * 60 * 1000;
const EXPIRED_MESSAGE = 'Tu confirmación venció. Confírmala de nuevo para seguir editando.';

interface Unlocked {
  readonly token: string;
  readonly deadline: number;
  readonly target: Target;
}

function secondsLeft(deadline: number, now: number) {
  return Math.max(0, Math.ceil((deadline - now) / 1000));
}

function clock(seconds: number) {
  return `${String(Math.floor(seconds / 60))}:${String(seconds % 60).padStart(2, '0')}`;
}

/** Cuenta regresiva de la confirmación; avisa cuando llega a cero. */
function useCountdown(deadline: number, onExpired: () => void) {
  const [now, setNow] = useState(() => Date.now());
  const expired = useRef(onExpired);
  useEffect(() => {
    expired.current = onExpired;
  });
  useEffect(() => {
    const timer = setInterval(() => {
      setNow(Date.now());
    }, 1000);
    return () => {
      clearInterval(timer);
    };
  }, [deadline]);
  const remaining = secondsLeft(deadline, now);
  useEffect(() => {
    if (remaining === 0) expired.current();
  }, [deadline, remaining]);
  return remaining;
}

/**
 * Cuenta propia (ADR-0058): los datos se ven en solo lectura y editarlos, o cambiar la contraseña,
 * exige confirmar la contraseña. La confirmación vive solo en memoria y dura unos minutos; al
 * recargar la página se vuelve a pedir.
 */
export function AccountSettings() {
  const profile = useAccountProfile();
  const [unlocked, setUnlocked] = useState<Unlocked | null>(null);
  const [asking, setAsking] = useState<Target | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  function lock(message: string | null) {
    setUnlocked(null);
    setNotice(message);
  }

  return (
    <>
      <section aria-labelledby="account-settings" className="mt-8">
        <h2 id="account-settings" className="text-xl font-bold text-ink">
          Datos de mi cuenta
        </h2>
        {notice && (
          <p role="status" className="mt-3 font-semibold text-warning-text">
            {notice}
          </p>
        )}
        {profile.isPending && <p className="mt-3">Cargando datos…</p>}
        {profile.isError && (
          <div className="mt-3">
            <ProblemNotice
              error={profile.error}
              onRetry={() => {
                void profile.refetch();
              }}
            />
          </div>
        )}
        {profile.data &&
          (unlocked ? (
            <ProfileForm
              initial={profile.data}
              token={unlocked.token}
              deadline={unlocked.deadline}
              focusOnMount={unlocked.target === 'profile'}
              onExpired={() => {
                lock(EXPIRED_MESSAGE);
              }}
              onLock={() => {
                lock(null);
              }}
            />
          ) : (
            <ProfileSummary
              profile={profile.data}
              onEdit={() => {
                setAsking('profile');
              }}
            />
          ))}
      </section>

      <section aria-labelledby="seguridad" className="mt-8">
        <h2 id="seguridad" className="text-xl font-bold text-ink">
          Seguridad
        </h2>
        {unlocked ? (
          <PasswordForm
            token={unlocked.token}
            deadline={unlocked.deadline}
            focusOnMount={unlocked.target === 'password'}
            onExpired={() => {
              lock(EXPIRED_MESSAGE);
            }}
          />
        ) : (
          <div className="mt-3 grid gap-3">
            <p className="text-ink-muted">
              Cambia tu contraseña cuando quieras. Te pediremos confirmarla antes.
            </p>
            <Button
              variant="secondary"
              onClick={() => {
                setAsking('password');
              }}
            >
              Cambiar contraseña
            </Button>
          </div>
        )}
      </section>

      <ReauthSheet
        open={asking !== null}
        onClose={() => {
          setAsking(null);
        }}
        onConfirmed={(grant: ReauthGrant) => {
          const target = asking ?? 'profile';
          setAsking(null);
          setNotice(null);
          setUnlocked({
            token: grant.reauthToken,
            deadline: Math.min(Date.parse(grant.expiresAt), Date.now() + MAX_WINDOW_MS),
            target,
          });
        }}
      />
    </>
  );
}

function ProfileSummary({
  profile,
  onEdit,
}: {
  readonly profile: AccountProfile;
  readonly onEdit: () => void;
}) {
  return (
    <div className="mt-3 grid gap-4">
      <dl className="grid gap-3 rounded-xl bg-surface px-4 py-4">
        <div>
          <dt className="text-sm text-ink-muted">Nombre completo</dt>
          <dd className="font-semibold text-ink">{profile.fullName}</dd>
        </div>
        <div>
          <dt className="text-sm text-ink-muted">Celular</dt>
          <dd className="font-semibold text-ink tabular-nums">
            {profile.phone ? formatPhoneDisplay(profile.phone) : 'Sin registrar'}
          </dd>
        </div>
      </dl>
      <Button variant="secondary" onClick={onEdit}>
        Editar mis datos
      </Button>
    </div>
  );
}

/** Se monta solo mientras la edición está habilitada: la cuenta regresiva parte de ese momento. */
function EditingNotice({
  deadline,
  onExpired,
}: {
  readonly deadline: number;
  readonly onExpired: () => void;
}) {
  const remaining = useCountdown(deadline, onExpired);
  return (
    <p className="text-sm font-semibold text-ink-muted">
      Puedes editar durante <span className="tabular-nums">{clock(remaining)}</span>
    </p>
  );
}

function ProfileForm({
  initial,
  token,
  deadline,
  focusOnMount,
  onExpired,
  onLock,
}: {
  readonly initial: AccountProfile;
  readonly token: string;
  readonly deadline: number;
  readonly focusOnMount: boolean;
  readonly onExpired: () => void;
  readonly onLock: () => void;
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
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (focusOnMount) (ref.current?.elements.namedItem('fullName') as HTMLElement | null)?.focus();
  }, [focusOnMount]);
  function showErrors(next: FieldErrors<ProfileField>) {
    setErrors(next);
    const first = (['fullName', 'phone'] as const).find((field) => next[field]);
    if (first) (ref.current?.elements.namedItem(first) as HTMLElement | null)?.focus();
  }
  async function submit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaved(false);
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
      const updated = await updateAccountProfile(
        http,
        { fullName: fullName.trim(), phone: normalizePhone(phone) },
        token,
      );
      setFullName(updated.fullName);
      setPhone(updated.phone);
      setErrors({});
      if (session.status === 'authenticated')
        queryClient.setQueryData(authKeys.profile(session.user.id), updated);
      store.updateFullName(updated.fullName);
      setSaved(true);
    } catch (error) {
      if (error instanceof ApiError && error.status === 403) {
        onExpired();
        return;
      }
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
      {saved && (
        <p role="status" className="text-success-text">
          Tus datos se guardaron.
        </p>
      )}
      <Button type="submit" disabled={busy}>
        {busy ? 'Guardando…' : 'Guardar datos'}
      </Button>
      <EditingNotice deadline={deadline} onExpired={onExpired} />
      <Button type="button" variant="secondary" onClick={onLock}>
        Terminar edición
      </Button>
    </form>
  );
}

function PasswordForm({
  token,
  deadline,
  focusOnMount,
  onExpired,
}: {
  readonly token: string;
  readonly deadline: number;
  readonly focusOnMount: boolean;
  readonly onExpired: () => void;
}) {
  const http = useHttpClient();
  const store = useSessionStore();
  const navigate = useNavigate();
  const ref = useRef<HTMLFormElement>(null);
  const [errors, setErrors] = useState<FieldErrors<PasswordFieldName>>({});
  const [serverError, setServerError] = useState<unknown>(null);
  const [pending, setPending] = useState(false);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (focusOnMount)
      (ref.current?.elements.namedItem('currentPassword') as HTMLElement | null)?.focus();
  }, [focusOnMount]);
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
      await changePassword(http, { currentPassword, newPassword }, token);
      await store.finishPasswordChange();
      void navigate('/ingresar', {
        replace: true,
        state: { notice: 'Tu contraseña cambió. Ingresa de nuevo.' },
      });
    } catch (error) {
      if (error instanceof ApiError && error.status === 403) {
        onExpired();
        return;
      }
      if (error instanceof ApiError && error.status === 422) {
        const mapped = serverFieldErrors(error.invalidParams, [
          'currentPassword',
          'newPassword',
        ] as const);
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
      <h3 className="text-lg font-bold text-ink">Cambiar contraseña</h3>
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
      <EditingNotice deadline={deadline} onExpired={onExpired} />
    </form>
  );
}
