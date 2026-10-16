import { fileURLToPath, URL } from 'node:url';

import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { loadEnv } from 'vite';
import { defineConfig } from 'vitest/config';

// ADR-0004: en desarrollo la PWA llama a /api en su propio origen y Vite reenvía al backend,
// de modo que la cookie de refresh (SameSite=Strict; Path=/api/v1/auth) viaja sin CORS.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_');
  const proxyTarget = env.VITE_DEV_API_PROXY_TARGET ?? 'http://localhost:8080';

  return {
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
    },
    server: {
      port: 5173,
      strictPort: true,
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
