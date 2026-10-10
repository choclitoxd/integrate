import type { RouteObject } from 'react-router';

import { AccountPage, ActivatePage, LoginPage, RegisterPage } from '@/features/auth';
import { CatalogPage, ExperienceDetailPage, SlotPickerPage } from '@/features/catalog';
import { ManagerHomePage } from '@/features/home';

import { RequireSession } from './guards/RequireSession';
import type { ShellHandle } from './layouts/AppShell';
import { ManagerLayout } from './layouts/ManagerLayout';
import { TouristLayout } from './layouts/TouristLayout';
import { NotFoundPage } from './pages/NotFoundPage';
import { RouteErrorPage } from './pages/RouteErrorPage';
import { RouteLoading } from './pages/RouteLoading';

const toCatalog: ShellHandle = { flow: true, back: () => '/' };
const slotsHandle: ShellHandle = {
  flow: true,
  back: ({ experienceId = '' }) => `/experiencias/${experienceId}`,
};

const toHosts: ShellHandle = { flow: true, back: () => '/gestor/anfitriones' };

/*
 * Carga diferida (ADR-0018 §1): la reserva (QR incluido) y el portal del Gestor no viajan en el
 * bundle inicial del catálogo. La guarda se aplica al resolver el módulo.
 */
const bookingForm = async () => {
  const { BookingFormPage } = await import('@/features/booking');
  return {
    element: (
      <RequireSession roles={['ROLE_TOURIST']}>
        <BookingFormPage />
      </RequireSession>
    ),
  };
};
const bookingStatus = async () => {
  const { BookingStatusPage } = await import('@/features/booking');
  return {
    element: (
      <RequireSession roles={['ROLE_TOURIST']}>
        <BookingStatusPage />
      </RequireSession>
    ),
  };
};
const hostsPage = async () => {
  const { HostsPage } = await import('@/features/enrollment');
  return { Component: HostsPage };
};
const enrollmentPage = async () => {
  const { EnrollmentPage } = await import('@/features/enrollment');
  return { Component: EnrollmentPage };
};

/**
 * Árbol de rutas por rol (ADR-0012 §4, ADR-0013 §6). Separado del router para montarlo en
 * memoria en las pruebas.
 */
export const routes: RouteObject[] = [
  {
    errorElement: <RouteErrorPage />,
    hydrateFallbackElement: <RouteLoading />,
    children: [
      {
        path: '/',
        element: <TouristLayout />,
        children: [
          { index: true, element: <CatalogPage /> },
          {
            path: 'experiencias/:experienceId',
            element: <ExperienceDetailPage />,
            handle: toCatalog,
          },
          {
            path: 'experiencias/:experienceId/franjas',
            element: <SlotPickerPage />,
            handle: slotsHandle,
          },
          { path: 'ingresar', element: <LoginPage />, handle: toCatalog },
          { path: 'registro', element: <RegisterPage />, handle: toCatalog },
          { path: 'activar', element: <ActivatePage />, handle: toCatalog },
          { path: 'reservas/nueva', lazy: bookingForm, handle: toCatalog },
          { path: 'reservas/:bookingId', lazy: bookingStatus, handle: toCatalog },
          {
            path: 'cuenta',
            element: (
              <RequireSession>
                <AccountPage />
              </RequireSession>
            ),
          },
        ],
      },
      {
        path: '/gestor',
        element: (
          <RequireSession roles={['ROLE_LINK_MANAGER']}>
            <ManagerLayout />
          </RequireSession>
        ),
        children: [
          { index: true, element: <ManagerHomePage /> },
          { path: 'anfitriones', lazy: hostsPage },
          { path: 'anfitriones/enrolamiento', lazy: enrollmentPage, handle: toHosts },
        ],
      },
      {
        element: <TouristLayout />,
        children: [{ path: '*', element: <NotFoundPage /> }],
      },
    ],
  },
];
