import { fileURLToPath, URL } from 'node:url';

import tailwindcss from '@tailwindcss/vite';
import basicSsl from '@vitejs/plugin-basic-ssl';
import react from '@vitejs/plugin-react';
import { loadEnv } from 'vite';
import { defineConfig } from 'vitest/config';

// ADR-0004: en desarrollo la PWA llama a /api en su propio origen y Vite reenvía al backend,
// de modo que la cookie de refresh (SameSite=Strict; Path=/api/v1/auth) viaja sin CORS.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_');
  const proxyTarget = env.VITE_DEV_API_PROXY_TARGET ?? 'http://localhost:8080';
  // `npm run dev:https` (solo desarrollo): sirve la PWA por HTTPS con un certificado autofirmado para
  // probarla desde un celular por la IP de la red local. La cookie de refresh es `Secure`
  // (SEC-DIR-004, FE-DIR-001 D4) y un navegador no la guarda por HTTP fuera de `localhost`, así que
  // sin HTTPS la sesión no sobrevive a una recarga. El backend y la cookie no cambian.
  const devHttps = process.env.PWA_DEV_HTTPS === '1';

  const serveManualPlugin = {
    name: 'serve-manual',
    configureServer(server: {
      middlewares: {
        use: (fn: (req: { url?: string }, res: unknown, next: () => void) => void) => void;
      };
    }) {
      server.middlewares.use((req, _res, next) => {
        const path = req.url?.split('?')[0];
        if (path === '/manual' || path === '/manual/') {
          req.url = '/manual/index.html';
        }
        next();
      });
    },
  };

  return {
    plugins: [react(), tailwindcss(), serveManualPlugin, ...(devHttps ? [basicSsl()] : [])],
    resolve: {
      alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
    },
    server: {
      port: 5173,
      strictPort: true,
      // Con HTTPS de desarrollo también se publica en la red local (el celular lo abre por la IP del equipo).
      ...(devHttps ? { host: true } : {}),
      proxy: {
        '/api': { target: proxyTarget, changeOrigin: true },
      },
    },
    preview: { port: 4173, strictPort: true },
    build: { sourcemap: true },
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      css: false,
      restoreMocks: true,
      // Con ~30 archivos de jsdom en paralelo, una prueba de formulario que tarda 2 s sola pasa de los
      // 5 s por defecto bajo carga y falla sin que haya un error real.
      testTimeout: 15_000,
      // ADR-0019 §4 (RNF-27): cobertura de la lógica de features y de la capa compartida.
      coverage: {
        provider: 'v8',
        include: ['src/features/**/*.{ts,tsx}', 'src/shared/**/*.{ts,tsx}'],
        exclude: ['**/*.test.{ts,tsx}', '**/index.ts'],
        reporter: ['text-summary', 'json-summary', 'html'],
        thresholds: { lines: 80 },
      },
    },
  };
});
