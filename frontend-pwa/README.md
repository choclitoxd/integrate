# frontend-pwa · Intégrate

PWA de Intégrate (Turista y Gestor de Enlace). Decisión de diseño: [ADR-0004](../docs/architecture/adr/ADR-0004-boilerplate-frontend-pwa-vite-react.md).

Stack: Vite 8 · React 19 · TypeScript 6 · Tailwind CSS 4 · React Router 8 · Vitest · ESLint 10 (+ `jsx-a11y-x`).
Requiere Node `>=22.12`.

## 1. Preparar (una vez)

```bash
npm install
cp .env.example .env.development.local
```

`.env.development.local` está en `.gitignore` (SEC-DIR-004). Las variables `VITE_*` se incrustan en el
bundle público: **nunca** contienen secretos. Sin `VITE_API_BASE_URL` la app no arranca y lo indica en pantalla.

## 2. Desarrollo

```bash
npm run dev        # http://localhost:5173
```

Las llamadas a `/api/**` se reenvían a `VITE_DEV_API_PROXY_TARGET` (por defecto `http://localhost:8080`),
así la cookie de refresh `SameSite=Strict` funciona sin CORS. Si el puerto 8080 está ocupado, arranque el
backend con `SERVER_PORT=8081` y ajuste `VITE_DEV_API_PROXY_TARGET` en su `.env.development.local`.

## 3. Calidad

| Script                            | Qué hace                                                                          |
| :-------------------------------- | :-------------------------------------------------------------------------------- |
| `npm run typecheck`               | TypeScript estricto (`tsc -b`)                                                    |
| `npm run lint`                    | ESLint: tipos, hooks, accesibilidad (`jsx-a11y-x` strict) y fronteras entre capas |
| `npm run format` / `format:check` | Prettier                                                                          |
| `npm test`                        | Vitest + Testing Library (jsdom)                                                  |
| `npm run build`                   | Typecheck + bundle de producción en `dist/`                                       |

## 4. Estructura

```text
src/
├── app/        router (routes.tsx), layouts por rol, páginas 404 / error / configuración
├── features/   un directorio por módulo de negocio, expuesto solo por su index.ts
├── shared/
│   ├── api/    createHttpClient (fetch) · ApiError (RFC 9457) · NetworkError
│   ├── config/ variables de entorno validadas (fail-fast)
│   └── ui/     componentes accesibles base (SkipLink, Button)
├── styles/     tokens de diseño Tailwind (@theme)
└── test/       setup de Vitest
```

Reglas verificadas por `npm run lint`:

- `shared/` no importa de `features/` ni de `app/`.
- Una feature no importa de `app/`.
- Nadie importa el interior de una feature (`@/features/x/Archivo`); solo `@/features/x`.

El access token vive solo en memoria (nunca `localStorage`/IndexedDB). Service Worker, manifiesto e
IndexedDB (`IntegrateOfflineDB`) llegan en T-2.1.
