import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ApiError, type RequestOptions } from '@/shared/api';
import { renderApp } from '@/test/renderApp';
import { pickOption } from '@/test/select';

import { putToStorage, type DocumentUploadTicket } from './api';
import { emptyDraft, formatFileSize, validateEnrollment, withVictimAnswer } from './forms';

const ENROLL_PATH = '/gestor/anfitriones/enrolamiento';

const policy = {
  version: 'v1.0',
  title: 'Política de Tratamiento de Datos',
  documentUrl: 'https://integrate.co/legal/politica-v1.0.pdf',
  effectiveFrom: '2026-10-01T05:00:00Z',
  contentSha256: 'a'.repeat(64),
};

const registration = {
  userId: 'u-9',
  hostProfileId: 'h-9',
  associationId: 'a-1',
  assignmentId: 1,
  linkManagerId: 'm-1',
  email: 'cc1094857483@comunidad.integrate.co',
  emailIsSynthetic: true,
  roles: ['ROLE_HOST'],
  credentialStatus: 'PENDING_RESET',
  consent: {
    channel: 'ASSISTED_PROXY',
    policyVersion: 'v1.0',
    purposes: ['PERSONAL_DATA', 'SENSITIVE_VICTIM'],
    capturedBy: 'm-1',
    capturedAt: '2026-10-10T14:00:00Z',
  },
};

function problem(status: number, extra: Record<string, unknown> = {}) {
  return new ApiError({ type: 'about:blank', title: 'x', status, ...extra }, 'trace-1');
}

function pdf(name = 'formato.pdf', size = 1_258_291) {
  const file = new File(['%PDF-1.7'], name, { type: 'application/pdf' });
  Object.defineProperty(file, 'size', { value: size });
  return file;
}

/** S3 simulado: registra cada PUT. */
function stubStorage(status = 200) {
  const put = vi.fn<typeof fetch>(() => Promise.resolve(new Response(null, { status })));
  vi.stubGlobal('fetch', put);
  return put;
}

function renderEnrollment(register: (body: unknown) => unknown = () => registration) {
  const bodies: unknown[] = [];
  let uploads = 0;
  const view = renderApp(
    ENROLL_PATH,
    {
      '/v1/privacy-policies/current': policy,
      '/v1/gestor/uploads': (_url: URL, options: RequestOptions) => {
        uploads += 1;
        const { kind } = options.body as { kind: string };
        const folder = kind === 'PHYSICAL_CONSENT_FORM' ? 'consents/physical' : 'host-support';
        return {
          s3Key: `${folder}/2026/10/k${String(uploads)}.pdf`,
          uploadUrl: `https://s3.local/${folder}/k${String(uploads)}.pdf?X-Amz-Signature=f`,
          contentType: 'application/pdf',
          maxSizeBytes: 5_242_880,
          expiresAt: '2026-10-10T14:05:00Z',
        };
      },
      '/v1/auth/register-assisted': (_url: URL, options: RequestOptions) => {
        bodies.push(options.body);
        return register(options.body);
      },
      '/v1/gestor/hosts/h-9/activation-code': {
        code: '7K3QD-M9X2P',
        expiresAt: '2026-10-13T19:05:00Z',
      },
    },
    { roles: ['ROLE_LINK_MANAGER'] },
  );
  return { ...view, bodies };
}

async function fillIdentity(user = userEvent.setup()) {
  await user.type(await screen.findByLabelText('Nombre completo'), 'Rosa Patiño');
  await user.type(screen.getByLabelText('Número de documento'), '1094857483');
  const phone = screen.getByLabelText('Celular');
  await user.clear(phone);
  await user.type(phone, '300 123 4567');
}

async function grantConsents(user = userEvent.setup()) {
  await user.click(screen.getByRole('checkbox', { name: /sus datos personales/ }));
  await user.click(screen.getByRole('checkbox', { name: /su condición de víctima/ }));
  await user.upload(screen.getByLabelText(/Formato de consentimiento firmado/), pdf());
  expect(
    await screen.findByText('Archivo cargado: formato.pdf (1,2 MB)', { exact: false }),
  ).toBeVisible();
}

const submit = () => userEvent.click(screen.getByRole('button', { name: 'Registrar anfitrión' }));

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('reglas del formulario (ADR-0018 §2)', () => {
  it('con "No" vacía los datos de víctima y no los envía', () => {
    const draft = withVictimAnswer(
      {
        ...emptyDraft(),
        fullName: 'Rosa Patiño',
        docNumber: '1094857483',
        phone: '3001234567',
        victim: 'yes',
        ruvRegistrationNumber: 'RUV-1',
        victimizationType: 'AMENAZA',
        supportDoc: { status: 'ready', s3Key: 'host-support/x.pdf', name: 'x.pdf', size: 10 },
        personalData: true,
        sensitiveVictim: true,
        consentForm: { status: 'ready', s3Key: 'consents/physical/y.pdf', name: 'y.pdf', size: 10 },
      },
      'no',
    );
    expect(draft.ruvRegistrationNumber).toBe('');
    expect(draft.supportDoc).toEqual({ status: 'empty' });
    const result = validateEnrollment(draft, 'v1.0');
    expect(result.ok && result.body.victimData).toEqual({ victimCondition: false });
    expect(result.ok && 'email' in result.body).toBe(false);
  });

  it('exige el formato firmado y espera a que termine de subir', () => {
    const base = {
      ...emptyDraft(),
      personalData: true,
      sensitiveVictim: true,
      victim: 'no' as const,
    };
    const missing = validateEnrollment(base, 'v1.0');
    expect(!missing.ok && missing.errors.consentForm).toBe(
      'Sube el formato de consentimiento firmado, en PDF.',
    );
    const uploading = validateEnrollment(
      { ...base, consentForm: { status: 'uploading', name: 'f.pdf', size: 1 } },
      'v1.0',
    );
    expect(!uploading.ok && uploading.errors.consentForm).toBe(
      'Espera a que el archivo termine de subir.',
    );
  });

  it('formatea el tamaño del archivo', () => {
    expect(formatFileSize(1_258_291)).toBe('1,2 MB');
    expect(formatFileSize(350_000)).toBe('342 kB');
  });
});

describe('subida directa a S3 (ADR-0018 §3)', () => {
  it('hace PUT con el Content-Type firmado y sin cookies', async () => {
    const fetchImpl = vi.fn<typeof fetch>(() =>
      Promise.resolve(new Response(null, { status: 200 })),
    );
    const ticket: DocumentUploadTicket = {
      s3Key: 'consents/physical/k.pdf',
      uploadUrl: 'https://s3.local/k.pdf?firma=1',
      contentType: 'application/pdf',
      maxSizeBytes: 5_242_880,
      expiresAt: '2026-10-10T14:05:00Z',
    };
    const file = pdf();
    await putToStorage(ticket, file, undefined, fetchImpl);
    const [url, init] = fetchImpl.mock.calls[0] ?? [];
    expect(url).toBe('https://s3.local/k.pdf?firma=1');
    expect(init).toMatchObject({
      method: 'PUT',
      credentials: 'omit',
      headers: { 'Content-Type': 'application/pdf' },
      body: file,
    });
    expect(new Headers(init?.headers).has('Authorization')).toBe(false);
  });

  it('rechaza en el teléfono lo que no es PDF o pesa más de 5 MB', async () => {
    const put = stubStorage();
    renderEnrollment();
    const user = userEvent.setup({ applyAccept: false });
    const input = await screen.findByLabelText(/Formato de consentimiento firmado/);
    await user.upload(input, new File(['x'], 'foto.jpg', { type: 'image/jpeg' }));
    expect(await screen.findByText(/Ese archivo no es un PDF/)).toBeVisible();
    await user.upload(input, pdf('grande.pdf', 6 * 1024 * 1024));
    expect(await screen.findByText(/pesa más de 5 MB/)).toBeVisible();
    expect(put).not.toHaveBeenCalled();
  });

  it('si S3 rechaza la subida, pide elegir el archivo otra vez', async () => {
    stubStorage(403);
    renderEnrollment();
    await userEvent.upload(
      await screen.findByLabelText(/Formato de consentimiento firmado/),
      pdf(),
    );
    expect(await screen.findByText(/La subida no se completó a tiempo/)).toBeVisible();
    expect(screen.getAllByText('Ningún archivo cargado.')).toHaveLength(1);
  });
});

describe('enrolamiento asistido (T-1.12)', () => {
  it('envía el cuerpo exacto de register-assisted con condición de víctima y soporte', async () => {
    const put = stubStorage();
    const { bodies } = renderEnrollment();
    const user = userEvent.setup();
    await fillIdentity(user);
    await user.click(screen.getByRole('radio', { name: 'Sí' }));
    await user.type(screen.getByLabelText('Número de registro RUV (opcional)'), 'RUV-2019-000123');
    await pickOption(
      user,
      screen.getByRole('combobox', { name: 'Hecho victimizante (opcional)' }),
      'Desplazamiento forzado',
    );
    await user.upload(screen.getByLabelText(/Documento soporte/), pdf('ruv.pdf', 200_000));
    expect(await screen.findByText(/Archivo cargado: ruv\.pdf/)).toBeVisible();
    await grantConsents(user);
    await submit();

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Anfitrión registrado' }),
    ).toHaveFocus();
    expect(bodies).toEqual([
      {
        fullName: 'Rosa Patiño',
        docType: 'CC',
        docNumber: '1094857483',
        phone: '+573001234567',
        victimData: {
          victimCondition: true,
          ruvRegistrationNumber: 'RUV-2019-000123',
          victimizationType: 'DESPLAZAMIENTO_FORZADO',
          supportDocS3Key: 'host-support/2026/10/k1.pdf',
        },
        consent: {
          policyVersion: 'v1.0',
          purposes: ['PERSONAL_DATA', 'SENSITIVE_VICTIM'],
          physicalConsentFormS3Key: 'consents/physical/2026/10/k2.pdf',
        },
      },
    ]);
    expect(put).toHaveBeenCalledTimes(2);
    // Los datos de víctima ya no se muestran tras el envío.
    expect(screen.queryByText(/RUV-2019-000123/)).toBeNull();
    expect(screen.getByText(/Correo interno asignado por Intégrate/)).toBeVisible();
  });

  it('cambiar a "No" oculta y vacía los datos de víctima antes de enviar', async () => {
    stubStorage();
    const { bodies } = renderEnrollment();
    const user = userEvent.setup();
    await fillIdentity(user);
    await user.click(screen.getByRole('radio', { name: 'Sí' }));
    await user.type(screen.getByLabelText('Número de registro RUV (opcional)'), 'RUV-1');
    await user.click(screen.getByRole('radio', { name: 'No' }));
    expect(screen.queryByLabelText('Número de registro RUV (opcional)')).toBeNull();
    await user.click(screen.getByRole('radio', { name: 'Sí' }));
    expect(screen.getByLabelText('Número de registro RUV (opcional)')).toHaveValue('');
    await user.click(screen.getByRole('radio', { name: 'No' }));
    await user.type(screen.getByLabelText('Correo (opcional)'), 'rosa@ejemplo.co');
    await grantConsents(user);
    await submit();
    await screen.findByRole('heading', { level: 1, name: 'Anfitrión registrado' });
    expect(bodies[0]).toMatchObject({
      email: 'rosa@ejemplo.co',
      victimData: { victimCondition: false },
    });
  });

  it('valida por campo y enfoca el primer error', async () => {
    renderEnrollment();
    await screen.findByLabelText('Nombre completo');
    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Registrar anfitrión' })).toBeEnabled();
    });
    await submit();
    expect(screen.getByLabelText('Nombre completo')).toHaveFocus();
    expect(screen.getByText('Indica si la persona es víctima del conflicto.')).toBeVisible();
    expect(screen.getByText('Sube el formato de consentimiento firmado, en PDF.')).toBeVisible();
  });

  it('sin conexión muestra la insignia e inhabilita el registro', async () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false);
    renderEnrollment();
    expect(await screen.findByText('Sin conexión')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Registrar anfitrión' })).toBeDisabled();
    expect(
      screen.getByText('El enrolamiento necesita conexión. Tus datos siguen aquí.'),
    ).toBeVisible();
  });

  it.each([
    [
      problem(403, { code: 'ENROLLMENT_MANAGER_NOT_ACTIVE' }),
      /Tu acreditación como gestor no está activa/,
    ],
    [
      problem(409, { code: 'AUTH_USER_ALREADY_EXISTS' }),
      /Ya existe una cuenta con ese documento o correo/,
    ],
  ])('explica el error %#', async (error, message) => {
    stubStorage();
    renderEnrollment(() => error);
    const user = userEvent.setup();
    await fillIdentity(user);
    await user.click(screen.getByRole('radio', { name: 'No' }));
    await grantConsents(user);
    await submit();
    expect(await screen.findByRole('alert')).toHaveTextContent(message);
    expect(screen.getByLabelText('Nombre completo')).toHaveValue('Rosa Patiño');
  });

  it('un 422 del formato físico lo vacía para subirlo de nuevo', async () => {
    stubStorage();
    renderEnrollment(() =>
      problem(422, {
        code: 'ENROLLMENT_DOCUMENT_INVALID',
        invalidParams: [{ name: 'consent.physicalConsentFormS3Key', reason: 'already in use' }],
      }),
    );
    const user = userEvent.setup();
    await fillIdentity(user);
    await user.click(screen.getByRole('radio', { name: 'No' }));
    await grantConsents(user);
    await submit();
    expect(
      await screen.findByText('El servidor no aceptó este archivo. Súbelo de nuevo.'),
    ).toBeVisible();
    expect(screen.getByLabelText(/Formato de consentimiento firmado/)).toHaveFocus();
    expect(screen.getByText('Ningún archivo cargado.')).toBeVisible();
  });

  it('muestra el código de activación una sola vez y nada queda en el almacenamiento', async () => {
    stubStorage();
    const { http } = renderEnrollment();
    const user = userEvent.setup();
    await fillIdentity(user);
    await user.click(screen.getByRole('radio', { name: 'Sí' }));
    await grantConsents(user);
    await submit();
    await user.click(await screen.findByRole('button', { name: 'Emitir código de activación' }));
    const card = await screen.findByRole('region', { name: 'Código de activación' });
    expect(within(card).getByText('7K3QD-M9X2P')).toBeVisible();
    expect(within(card).getByText(/Vence el martes, 13 de octubre/)).toBeVisible();
    expect(within(card).getByText(/No se volverá a mostrar/)).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Emitir código de activación' })).toBeNull();
    expect(http.calls.filter(([path]) => path.endsWith('/activation-code'))).toHaveLength(1);

    const stored = [localStorage, sessionStorage]
      .flatMap((storage) =>
        Object.keys(storage).map((key) => `${key}=${storage.getItem(key) ?? ''}`),
      )
      .join(' ');
    expect(stored).not.toMatch(/Rosa|1094857483|7K3QD|consents\/physical/);

    await user.click(screen.getByRole('button', { name: 'Enrolar a otra persona' }));
    expect(screen.getByLabelText('Nombre completo')).toHaveValue('');
  });

  it('avisa antes de salir con datos sin enviar', async () => {
    const { router } = renderEnrollment();
    await userEvent.type(await screen.findByLabelText('Nombre completo'), 'Rosa');
    void router.navigate('/gestor');
    const sheet = await screen.findByRole('dialog', { name: '¿Salir sin registrar?' });
    await userEvent.click(within(sheet).getByRole('button', { name: 'Seguir llenando' }));
    expect(router.state.location.pathname).toBe(ENROLL_PATH);
  });
});

describe('mis anfitriones (ADR-0018 §6)', () => {
  const host = (n: number, overrides: Record<string, unknown> = {}) => ({
    hostProfileId: `h-${String(n)}`,
    userId: `u-${String(n)}`,
    fullName: `Anfitrión ${String(n)}`,
    email: `cc100${String(n)}@comunidad.integrate.co`,
    emailIsSynthetic: true,
    phone: '+573001234567',
    docType: 'CC',
    docNumberLast4: '7483',
    credentialStatus: 'PENDING_RESET',
    assignedSince: '2026-10-01T14:00:00Z',
    ...overrides,
  });

  it('pagina con "Ver más", enmascara el documento y ofrece el código solo cuando aplica', async () => {
    renderApp(
      '/gestor/anfitriones',
      {
        '/v1/gestor/hosts': (url: URL) =>
          url.searchParams.get('page') === '0'
            ? {
                page: 0,
                size: 20,
                totalElements: 3,
                totalPages: 2,
                items: [
                  host(1),
                  host(2, {
                    credentialStatus: 'ACTIVE',
                    emailIsSynthetic: false,
                    email: 'ana@ejemplo.co',
                  }),
                ],
              }
            : { page: 1, size: 20, totalElements: 3, totalPages: 2, items: [host(3)] },
      },
      { roles: ['ROLE_LINK_MANAGER'] },
    );
    expect(await screen.findByText('3 anfitriones')).toBeVisible();
    const cards = within(screen.getByRole('list', { name: 'Anfitriones' })).getAllByRole(
      'listitem',
    );
    expect(cards).toHaveLength(2);
    const [pending, active] = cards;
    if (!pending || !active) throw new Error('Faltan tarjetas de anfitrión.');
    expect(pending).toHaveTextContent('CC ••••terminado en 7483');
    expect(pending).toHaveTextContent('+57 300 123 4567');
    expect(within(pending).getByText('Pendiente de activación')).toBeVisible();
    expect(
      within(pending).getByRole('button', { name: 'Emitir código de activación' }),
    ).toBeVisible();
    expect(within(active).getByText('Activo')).toBeVisible();
    expect(within(active).queryByRole('button')).toBeNull();

    await userEvent.click(screen.getByRole('button', { name: 'Ver más' }));
    expect(await screen.findByText('Anfitrión 3')).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Ver más' })).toBeNull();
  });

  it('una cuenta activa con correo real recupera por correo (409)', async () => {
    renderApp(
      '/gestor/anfitriones',
      {
        '/v1/gestor/hosts': {
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
          items: [host(1, { credentialStatus: 'ACTIVE' })],
        },
        '/v1/gestor/hosts/h-1/activation-code': problem(409, { code: 'INVALID_STATUS_TRANSITION' }),
      },
      { roles: ['ROLE_LINK_MANAGER'] },
    );
    await userEvent.click(
      await screen.findByRole('button', { name: 'Emitir código de activación' }),
    );
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Esta persona recupera su acceso por correo.',
    );
  });

  it('informa la acreditación inactiva', async () => {
    renderApp(
      '/gestor/anfitriones',
      { '/v1/gestor/hosts': problem(403, { code: 'ENROLLMENT_MANAGER_NOT_ACTIVE' }) },
      { roles: ['ROLE_LINK_MANAGER'] },
    );
    expect(await screen.findByRole('alert')).toHaveTextContent(
      /Tu acreditación como gestor no está activa/,
    );
  });
});
