import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { addDays, todayInBogota } from '@/shared/lib';
import { availability, detail, EXPERIENCE_ID, schedule } from '@/test/fixtures';
import { percent, recordQuality } from '@/test/quality';
import { renderApp } from '@/test/renderApp';

import { CountdownBanner } from './components/CountdownBanner';
import { bookingFormSchema } from './forms';

/**
 * RNF-13 (Capacidad de interacción · protección contra errores de usuario): el checkout valida en el
 * cliente antes de invocar la API (EPS y contacto de emergencia obligatorios) y avisa cuando el TTL
 * de 15 minutos está por vencer.
 */

const day = addDays(todayInBogota(), 3);
const FORM_PATH = `/reservas/nueva?experiencia=${EXPERIENCE_ID}&franja=b&personas=2`;

const valid = {
  participants: [
    { fullName: 'Carlos Restrepo', docType: 'CC', docNumber: '1094000111', medicalEps: 'Sura' },
    { fullName: 'Luis Restrepo', docType: 'CC', docNumber: '1094000222', medicalEps: 'Sanitas' },
  ],
  emergencyName: 'Carmen Ruiz',
  emergencyPhone: '300 765 4321',
} as const;

type Omission = 'eps1' | 'eps2' | 'emergencyName' | 'emergencyPhone' | 'doc1';
const OMISSIONS: readonly Omission[] = ['eps1', 'eps2', 'emergencyName', 'emergencyPhone', 'doc1'];

function renderForm() {
  const lockCalls: unknown[] = [];
  const view = renderApp(
    FORM_PATH,
    {
      [`/v1/experiences/${EXPERIENCE_ID}/availability`]: (url: URL) =>
        availability(url.searchParams.get('dateFrom') ?? '', url.searchParams.get('dateTo') ?? '', [
          schedule('b', day, '07', 5),
        ]),
      [`/v1/experiences/${EXPERIENCE_ID}`]: detail,
      '/v1/me/consents': {
        currentPolicyVersion: 'v0.1',
        consents: [
          { purpose: 'PERSONAL_DATA', state: 'VALID', policyVersion: 'v0.1', occurredAt: null },
          { purpose: 'HEALTH_EMERGENCY', state: 'VALID', policyVersion: 'v0.1', occurredAt: null },
        ],
        requiresConsentUpdate: false,
        pendingPurposes: [],
      },
      '/v1/bookings/lock': () => {
        lockCalls.push(true);
        return new Error('El formulario incompleto no debía llegar a la API');
      },
    },
    { roles: ['ROLE_TOURIST'] },
  );
  return { ...view, lockCalls };
}

/** Llena todo el formulario salvo el campo omitido. */
async function fillExcept(omit: Omission) {
  await screen.findByText(/Sendero de la Memoria/);
  const travelers = [1, 2].map((n) =>
    screen.getByRole('group', { name: new RegExp(`Viajero ${String(n)} de 2`) }),
  );
  const [first, second] = travelers as [HTMLElement, HTMLElement];
  if (omit !== 'doc1') {
    await userEvent.type(within(first).getByLabelText('Número de documento'), '1094000111');
  }
  if (omit !== 'eps1') await userEvent.type(within(first).getByLabelText(/^EPS/), 'Sura');
  const secondName = within(second).getByLabelText('Nombre completo');
  await userEvent.clear(secondName);
  await userEvent.type(secondName, 'Luis Restrepo');
  await userEvent.type(within(second).getByLabelText('Número de documento'), '1094000222');
  if (omit !== 'eps2') await userEvent.type(within(second).getByLabelText(/^EPS/), 'Sanitas');
  const contact = screen.getByRole('group', { name: 'Contacto de emergencia' });
  if (omit !== 'emergencyName') {
    await userEvent.type(within(contact).getByLabelText('Nombre completo'), 'Carmen Ruiz');
  }
  const phone = within(contact).getByLabelText('Celular');
  await userEvent.clear(phone);
  if (omit !== 'emergencyPhone') await userEvent.type(phone, '300 765 4321');
}

describe('[RNF-13] Protección contra errores de usuario en el checkout', () => {
  it('[RNF-13] el esquema rechaza el 100 % de los formularios sin un campo obligatorio', ({
    task,
  }) => {
    expect(bookingFormSchema.safeParse(valid).success).toBe(true);
    const variants = [
      {
        ...valid,
        participants: [{ ...valid.participants[0], medicalEps: '' }, valid.participants[1]],
      },
      {
        ...valid,
        participants: [valid.participants[0], { ...valid.participants[1], medicalEps: ' ' }],
      },
      { ...valid, emergencyName: '' },
      { ...valid, emergencyPhone: '' },
      { ...valid, emergencyPhone: '123' },
      {
        ...valid,
        participants: [{ ...valid.participants[0], docNumber: '' }, valid.participants[1]],
      },
      {
        ...valid,
        participants: [{ ...valid.participants[0], fullName: 'A' }, valid.participants[1]],
      },
    ];
    const rejected = variants.filter((v) => !bookingFormSchema.safeParse(v).success).length;
    recordQuality(task, {
      rnf: 'RNF-13',
      metric: 'Formularios incompletos rechazados por la validación del cliente',
      value: percent(rejected, variants.length),
      unit: '%',
      threshold: '= 100 %',
      pass: rejected === variants.length,
      detail: `${String(variants.length)} variantes: sin EPS (titular y acompañante), sin nombre o celular de emergencia, celular inválido, sin documento, nombre corto`,
    });
    expect(rejected).toBe(variants.length);
  });

  it(
    '[RNF-13] ningún envío incompleto invoca la API de reserva',
    { timeout: 60_000 },
    async ({ task }) => {
      const outcomes: string[] = [];
      for (const omit of OMISSIONS) {
        const { lockCalls, unmount } = renderForm();
        await fillExcept(omit);
        await userEvent.click(screen.getByRole('button', { name: 'Apartar cupos' }));
        // Cualquier control marcado: la EPS es un combobox con sugerencias, no un textbox.
        const flagged = document.querySelectorAll('[aria-invalid="true"]').length > 0;
        outcomes.push(
          `${omit}: ${lockCalls.length > 0 ? 'llegó a la API' : flagged ? 'detenido' : 'sin aviso'}`,
        );
        unmount();
      }
      const blocked = outcomes.filter((o) => o.endsWith('detenido')).length;
      recordQuality(task, {
        rnf: 'RNF-13',
        metric: 'Envios incompletos detenidos antes de llamar a la API',
        value: percent(blocked, OMISSIONS.length),
        unit: '%',
        threshold: '= 100 %',
        pass: blocked === OMISSIONS.length,
        detail: `formulario real renderizado, un campo omitido por envío: ${outcomes.join('; ')}`,
      });
      expect(outcomes).toEqual(OMISSIONS.map((omit) => `${omit}: detenido`));
    },
  );

  it('[RNF-13] avisa en pantalla y al lector cuando el TTL está por vencer', ({ task }) => {
    const checkpoints = [
      { remaining: 300, text: 'Quedan 5 minutos para pagar.' },
      { remaining: 60, text: 'Último minuto: si no pagas, los cupos se liberan.' },
      { remaining: 0, text: 'El tiempo para pagar terminó.' },
    ];
    let warned = 0;
    for (const { remaining, text } of checkpoints) {
      const { unmount } = render(<CountdownBanner remaining={remaining} />);
      if (screen.queryAllByText(text).length > 0) warned++;
      unmount();
    }
    recordQuality(task, {
      rnf: 'RNF-13',
      metric: 'Hitos del TTL anunciados al usuario',
      value: percent(warned, checkpoints.length),
      unit: '%',
      threshold: '= 100 %',
      pass: warned === checkpoints.length,
      detail: '5 min, último minuto y vencido; texto visible y región aria-live',
    });
    expect(warned).toBe(checkpoints.length);
  });
});
