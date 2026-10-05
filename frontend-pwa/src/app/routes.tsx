import type { RouteObject } from 'react-router';

import { ManagerHomePage, TouristHomePage } from '@/features/home';

import { ManagerLayout } from './layouts/ManagerLayout';
import { TouristLayout } from './layouts/TouristLayout';
import { NotFoundPage } from './pages/NotFoundPage';
import { RouteErrorPage } from './pages/RouteErrorPage';

/** Árbol de rutas por rol. Separado del router para montarlo en memoria en las pruebas. */
export const routes: RouteObject[] = [
  {
    errorElement: <RouteErrorPage />,
    children: [
      {
        path: '/',
        element: <TouristLayout />,
        children: [{ index: true, element: <TouristHomePage /> }],
      },
      {
        path: '/gestor',
        element: <ManagerLayout />,
        children: [{ index: true, element: <ManagerHomePage /> }],
      },
      {
        element: <TouristLayout />,
        children: [{ path: '*', element: <NotFoundPage /> }],
      },
    ],
  },
];
