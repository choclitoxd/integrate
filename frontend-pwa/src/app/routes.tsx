import type { RouteObject } from 'react-router';

import {
  AccountPage,
  ActivatePage,
  ForgotPasswordPage,
  LoginPage,
  RegisterPage,
  ResetPasswordPage,
} from '@/features/auth';
import { CatalogPage, ExperienceDetailPage, SlotPickerPage } from '@/features/catalog';
import { ManagerHomePage } from '@/features/home';
import { AdaptiveView } from '@/shared/ui';

import { CatalogDesktop, ExperienceDetailDesktop, SlotPickerDesktop } from './desktopViews';
import { RequireSession } from './guards/RequireSession';
import type { ShellHandle } from './layouts/AppShell';
import { ManagerLayout } from './layouts/ManagerLayout';
import { TouristLayout } from './layouts/TouristLayout';
import { NotFoundPage } from './pages/NotFoundPage';
import { RouteErrorPage } from './pages/RouteErrorPage';
import { RouteLoading } from './pages/RouteLoading';

const toCatalog: ShellHandle = { flow: true, back: () => '/' };
const toLogin: ShellHandle = { flow: true, back: () => '/ingresar' };
/** Una reserva propia: sin historial (enlace directo), "Atrás" lleva a la lista de sus experiencias. */
const toMyExperiences: ShellHandle = { flow: true, back: () => '/mis-experiencias' };
const catalogHandle: ShellHandle = { wide: true };
const detailHandle: ShellHandle = { ...toCatalog, wide: true };
const slotsHandle: ShellHandle = {
  flow: true,
  wide: true,
  back: ({ experienceId = '' }) => `/experiencias/${experienceId}`,
};

const toHosts: ShellHandle = { flow: true, back: () => '/gestor/anfitriones' };
const toManagerHome: ShellHandle = { flow: true, back: () => '/gestor' };

/*
 * Carga diferida (ADR-0018 §1): la reserva (QR incluido) y el portal del Gestor no viajan en el
 * bundle inicial del catálogo. La guarda se aplica al resolver el módulo.
 */
const qrScannerPage = async () => {
  const { QrScannerPage } = await import('@/features/offline');
  return { Component: QrScannerPage };
};
const contingencyRedeemPage = async () => {
  const { ContingencyRedeemPage } = await import('@/features/offline');
  return { Component: ContingencyRedeemPage };
};
const syncQueuePage = async () => {
  const { SyncQueuePage } = await import('@/features/offline');
  return { Component: SyncQueuePage };
};
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
const myExperiences = async () => {
  const { MyExperiencesPage } = await import('@/features/booking');
  return {
    element: (
      <RequireSession roles={['ROLE_TOURIST']}>
        <MyExperiencesPage />
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

/*
 * Consola de administración (ADR-0042 §2): el shell y la feature `admin` viajan en un chunk propio
 * que ni el turista ni el gestor descargan.
 */
const adminLayout = async () => {
  const { AdminLayout } = await import('./layouts/AdminLayout');
  return {
    element: (
      <RequireSession roles={['ROLE_ADMIN']}>
        <AdminLayout />
      </RequireSession>
    ),
  };
};
const adminHomePage = async () => {
  const { AdminHomePage } = await import('@/features/admin');
  return { Component: AdminHomePage };
};
const adminExperiencesPage = async () => {
  const { ExperiencesPage } = await import('@/features/admin');
  return { Component: ExperiencesPage };
};
const adminAssociationsPage = async () => {
  const { AssociationsPage } = await import('@/features/admin');
  return { Component: AssociationsPage };
};
const adminPeoplePage = async () => {
  const { PeoplePage } = await import('@/features/admin');
  return { Component: PeoplePage };
};
const adminAuditPage = async () => {
  const { AuditPage } = await import('@/features/admin');
  return { Component: AuditPage };
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
          {
            index: true,
            element: (
              <AdaptiveView desktop={CatalogDesktop}>
                <CatalogPage />
              </AdaptiveView>
            ),
            handle: catalogHandle,
          },
          {
            path: 'experiencias/:experienceId',
            element: (
              <AdaptiveView desktop={ExperienceDetailDesktop}>
                <ExperienceDetailPage />
              </AdaptiveView>
            ),
            handle: detailHandle,
          },
          {
            path: 'experiencias/:experienceId/franjas',
            element: (
              <AdaptiveView desktop={SlotPickerDesktop}>
                <SlotPickerPage />
              </AdaptiveView>
            ),
            handle: slotsHandle,
          },
          { path: 'ingresar', element: <LoginPage />, handle: { centerOnDesktop: true } },
          { path: 'registro', element: <RegisterPage />, handle: toCatalog },
          { path: 'activar', element: <ActivatePage />, handle: toCatalog },
          { path: 'recuperar-contrasena', element: <ForgotPasswordPage />, handle: toLogin },
          { path: 'restablecer-contrasena', element: <ResetPasswordPage />, handle: toLogin },
          { path: 'reservas/nueva', lazy: bookingForm, handle: toCatalog },
          { path: 'mis-experiencias', lazy: myExperiences },
          { path: 'reservas/:bookingId', lazy: bookingStatus, handle: toMyExperiences },
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
          { path: 'validar', lazy: qrScannerPage, handle: toManagerHome },
          { path: 'contingencia', lazy: contingencyRedeemPage, handle: toManagerHome },
          { path: 'cola', lazy: syncQueuePage, handle: toManagerHome },
          { path: 'anfitriones', lazy: hostsPage },
          { path: 'anfitriones/enrolamiento', lazy: enrollmentPage, handle: toHosts },
        ],
      },
      {
        path: '/admin',
        lazy: adminLayout,
        children: [
          { index: true, lazy: adminHomePage },
          { path: 'experiencias', lazy: adminExperiencesPage },
          { path: 'asociaciones', lazy: adminAssociationsPage },
          { path: 'personas', lazy: adminPeoplePage },
          { path: 'auditoria', lazy: adminAuditPage },
        ],
      },
      {
        element: <TouristLayout />,
        children: [{ path: '*', element: <NotFoundPage /> }],
      },
    ],
  },
];
