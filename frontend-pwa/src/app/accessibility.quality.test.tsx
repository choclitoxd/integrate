import { screen } from '@testing-library/react';
import axe from 'axe-core';
import { describe, expect, it } from 'vitest';

import { addDays, todayInBogota } from '@/shared/lib';
import { availability, catalogRoutes, detail, EXPERIENCE_ID, schedule } from '@/test/fixtures';
import { recordQuality } from '@/test/quality';
import { renderApp, type FakeRoutes } from '@/test/renderApp';

/**
 * RNF-03 (Capacidad de interacción · accesibilidad WCAG 2.1 AA / NTC 5854): auditoría automática con
 * axe-core de las vistas principales, renderizadas con la aplicación real (router, layouts, sesión).
 * jsdom no calcula estilos: el contraste lo certifica `styles/tokens.test.ts` (umbral AAA del
 * proyecto) y las áreas táctiles se revisan en la auditoría manual de T-2.15.
 */

const policy = {
  version: 'v0.1',
  title: 'Política de Tratamiento de Datos',
  documentUrl: 'https://integrate.co/legal/politica-v0.1.pdf',
  effectiveFrom: '2026-10-01T05:00:00Z',
  contentSha256: 'a'.repeat(64),
};

const consents = {
  currentPolicyVersion: 'v0.1',
  consents: [
    { purpose: 'PERSONAL_DATA', state: 'VALID', policyVersion: 'v0.1', occurredAt: null },
    { purpose: 'HEALTH_EMERGENCY', state: 'VALID', policyVersion: 'v0.1', occurredAt: null },
  ],
  requiresConsentUpdate: false,
  pendingPurposes: [],
};

const day = addDays(todayInBogota(), 3);

interface View {
  readonly name: string;
  readonly path: string;
  readonly routes: FakeRoutes;
  readonly roles?: readonly string[];
}

const VIEWS: readonly View[] = [
  { name: 'Catálogo', path: '/', routes: catalogRoutes },
  { name: 'Detalle de experiencia', path: `/experiencias/${EXPERIENCE_ID}`, routes: catalogRoutes },
  { name: 'Ingresar', path: '/ingresar', routes: catalogRoutes },
  {
    name: 'Registro',
    path: '/registro',
    routes: { ...catalogRoutes, '/v1/privacy-policies/current': policy },
  },
  { name: 'Activar cuenta', path: '/activar', routes: catalogRoutes },
  {
    name: 'Formulario de reserva',
    path: `/reservas/nueva?experiencia=${EXPERIENCE_ID}&franja=b&personas=2`,
    roles: ['ROLE_TOURIST'],
    routes: {
      [`/v1/experiences/${EXPERIENCE_ID}/availability`]: (url: URL) =>
        availability(url.searchParams.get('dateFrom') ?? '', url.searchParams.get('dateTo') ?? '', [
          schedule('b', day, '07', 5),
        ]),
      [`/v1/experiences/${EXPERIENCE_ID}`]: detail,
      '/v1/me/consents': consents,
    },
  },
  {
    name: 'Enrolamiento asistido (Gestor)',
    path: '/gestor/anfitriones/enrolamiento',
    roles: ['ROLE_LINK_MANAGER'],
    routes: { '/v1/privacy-policies/current': policy },
  },
  {
    name: 'Panel de jornada offline (Gestor)',
    path: '/gestor',
    roles: ['ROLE_LINK_MANAGER'],
    routes: {},
  },
  {
    name: 'Escáner QR en campo (Gestor)',
    path: '/gestor/validar',
    roles: ['ROLE_LINK_MANAGER'],
    routes: {},
  },
  {
    name: 'Contingencia manual (Gestor)',
    path: '/gestor/contingencia',
    roles: ['ROLE_LINK_MANAGER'],
    routes: {},
  },
  {
    name: 'Cola de sincronización (Gestor)',
    path: '/gestor/cola',
    roles: ['ROLE_LINK_MANAGER'],
    routes: {},
  },
];

async function audit(): Promise<axe.Result[]> {
  const results = await axe.run(document.body, {
    runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] },
    rules: { 'color-contrast': { enabled: false } },
    // El mapa del punto de encuentro es una página de terceros (Google Maps): no se audita su
    // interior, pero el <iframe> sí se revisa en el documento (regla frame-title). Además, axe no
    // puede comunicarse con marcos en jsdom.
    iframes: false,
  });
  return results.violations;
}

describe('[RNF-03] Accesibilidad web WCAG 2.1 AA y NTC 5854', () => {
  it(
    '[RNF-03] las vistas principales no tienen violaciones WCAG 2.1 A/AA detectables',
    { timeout: 60_000 },
    async ({ task }) => {
      const findings: string[] = [];
      let total = 0;
      for (const view of VIEWS) {
        const { unmount } = renderApp(
          view.path,
          view.routes,
          view.roles ? { roles: view.roles } : {},
        );
        await screen.findByRole('heading', { level: 1 });
        const violations = await audit();
        total += violations.reduce((sum, v) => sum + v.nodes.length, 0);
        for (const v of violations) {
          findings.push(`${view.name}: ${v.id} (${String(v.nodes.length)})`);
        }
        unmount();
      }
      recordQuality(task, {
        rnf: 'RNF-03',
        metric: 'Violaciones WCAG 2.1 A/AA detectadas por axe-core',
        value: total,
        unit: 'violaciones',
        threshold: '= 0',
        pass: total === 0,
        detail: `${String(VIEWS.length)} vistas: ${VIEWS.map((v) => v.name).join(', ')}${
          findings.length > 0 ? `; hallazgos: ${findings.join('; ')}` : ''
        }`,
      });
      expect(findings).toEqual([]);
    },
  );
});
