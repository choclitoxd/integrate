import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { createBrowserRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';

import { ConfigErrorScreen } from '@/app/pages/ConfigErrorScreen';
import { routes } from '@/app/routes';
import { ConfigError, getConfig } from '@/shared/config';

import './styles/index.css';

const container = document.getElementById('root');
if (!container) throw new Error('No existe el elemento #root en index.html.');

function bootstrap() {
  try {
    getConfig();
  } catch (error) {
    if (error instanceof ConfigError) return <ConfigErrorScreen message={error.message} />;
    throw error;
  }
  return <RouterProvider router={createBrowserRouter(routes)} />;
}

createRoot(container).render(<StrictMode>{bootstrap()}</StrictMode>);
