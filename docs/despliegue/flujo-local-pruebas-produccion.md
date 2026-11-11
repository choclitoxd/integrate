# Flujo de trabajo: `local` → `pruebas` → `main` (producción)

Guía corta para no confundir en qué ambiente se está. No contiene secretos (SEC-DIR-004): las credenciales viven en `infra/env/.env.*`, ignorados por Git.

**Por qué existe:** SnapDeploy (pruebas) permite 5 despliegues por 12 h. Cada push a `pruebas` reconstruye el contenedor y gasta cupo. Con la rama `local` se prueba **todo en el PC** y a SnapDeploy solo llega código ya verificado.

## 1. Los tres ambientes

| | **LOCAL** | **PRUEBAS** | **PRODUCCIÓN** |
| :--- | :--- | :--- | :--- |
| Rama de Git | `local` | `pruebas` | `main` |
| Backend | `mvnw spring-boot:run` en `localhost:8080` | SnapDeploy (`integrate-0da6e`) | Hetzner `integrate-api` |
| Base de datos | PostgreSQL en Docker (`localhost:5432`) | Rama `pruebas` de Neon | Rama principal de Neon |
| PWA | `npm run dev` en `localhost:5173` | `https://pruebas.integrate-demo.pages.dev` | `https://integrate-demo.pages.dev` |
| Variables backend | `infra/env/.env.development` | `infra/env/.env.demo.pruebas` | `infra/env/.env.demo` (servidor: `~/integrate/.env`) |
| `SPRING_PROFILES_ACTIVE` | `dev` (carga datos semilla) | **NO definir** | **NO definir** |
| Correos | Mailpit (`localhost:8025`) | Apagados (`INTEGRATE_NOTIFICATION_DISPATCH_ENABLED=false`) | Gmail SMTP real (ADR-0065) |
| Pagos | `MOCK` + simulador activo | `MOCK` + simulador activo | `MOCK`/Wompi según decisión; simulador `false` con Wompi |
| Archivos | LocalStack S3 | Backblaze B2 | Backblaze B2 |
| `VITE_API_BASE_URL` | `/api` (proxy de Vite → `VITE_DEV_API_PROXY_TARGET=http://localhost:8080`) | `/api` (proxy de Pages → `API_ORIGIN` de pruebas) | `/api` (proxy de Pages → `API_ORIGIN` de producción) |

> [!IMPORTANT]
> Lo que cambia entre ambientes son **las variables de entorno**, nunca el código. Si hay que tocar código para cambiar de ambiente, algo está mal (hardcode prohibido por SEC-DIR-004).

## 2. Ciclo completo

```
rama local ── probar en el PC ──► merge a `pruebas` ──► SnapDeploy + Pages(pruebas) ──► merge a `main` ──► Hetzner + Pages(producción)
 (0 cupo)                         (1 push = 1 despliegue)                              (solo si pruebas OK)
```

### Paso A · LOCAL (todo el desarrollo y la verificación)

```powershell
git checkout local

# 1) Infraestructura local (desde infra/docker)
cd infra\docker
docker compose --env-file ../env/.env.development --profile full up -d
docker compose --env-file ../env/.env.development ps        # todo en healthy
cd ..\..

# 2) Backend (otra terminal, con las variables de .env.development cargadas)
cd backend-api
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"

# 3) PWA (otra terminal)
cd frontend-pwa
npm run dev                                                 # http://localhost:5173
```

Antes de pasar al siguiente ambiente, en verde:

- Backend: `.\mvnw.cmd verify`
- Frontend: los 5 comandos del Quality Gate (`docs/rules/frontend-rules.md`, Directiva 7) y `npm run build`
- Probar a mano las pantallas del cambio (en este caso **Incidentes** y **Liquidaciones**).
- Correos: revisar en Mailpit (`http://localhost:8025`).

Se hace commit atómico en `local` (Conventional Commits, `docs/rules/git-workflow.md`). **No se hace push a `pruebas` hasta terminar la verificación local completa.**

### Paso B · PRUEBAS (SnapDeploy + Pages vista previa)

Cambios que debe haber al pasar de local a pruebas:

| Qué | En local | En pruebas |
| :--- | :--- | :--- |
| Base de datos | Docker `localhost:5432` | Rama `pruebas` de Neon (`.env.demo.pruebas`) |
| Perfil `dev` | Activo | **Inactivo** (si se define, carga `db/seed-dev`) |
| Correo | Mailpit | Apagado |
| `FRONTEND_BASE_URL` | `http://localhost:5173` | `https://pruebas.integrate-demo.pages.dev` |
| Storage | LocalStack | Backblaze B2 |
| Proxy `/api` | Vite → `localhost:8080` | Pages → `API_ORIGIN` del entorno pruebas |

```powershell
git checkout pruebas
git merge local          # sin conflictos de variables: ningún .env viaja en Git
git push origin pruebas  # esto REBUILDEA SnapDeploy: gasta 1 de los 5 despliegues / 12 h
```

Después (Git Bash):

```bash
bash infra/scripts/snapdeploy.sh deploys     # esperar a que el build termine
bash infra/scripts/snapdeploy.sh wake
bash infra/scripts/snapdeploy.sh logs 400 | grep -oE "ep-[a-z]+-[a-z]+-[a-z0-9]+" | sort -u
bash infra/scripts/pages.sh deploy pruebas
```

> [!CAUTION]
> **Comprobar el aislamiento antes de probar.** El log debe mostrar **solo** el host de la rama `pruebas` de Neon (`ep-orange-frog-…`), nunca el de producción (`ep-calm-firefly-…`). Si aparece el de producción, detener el contenedor de inmediato. SnapDeploy aplica las variables solo al construir un despliegue nuevo (`docs/despliegue/demo-gratuita.md` §2).

Al terminar de probar, detener el contenedor para no gastar horas del plan Free.

> [!TIP]
> **Ahorrar cupo:** acumule varios cambios en `local` y haga un solo merge/push a `pruebas`. Si el cupo está agotado, `snapdeploy.sh status` muestra el estado y la ventana de espera.

### Paso C · PRODUCCIÓN (Hetzner + Pages)

Solo si pruebas quedó verificado.

| Qué | En pruebas | En producción |
| :--- | :--- | :--- |
| Base de datos | Rama `pruebas` de Neon | Rama principal de Neon |
| Variables | `.env.demo.pruebas` | `.env.demo` → `~/integrate/.env` en el servidor |
| Correo | Apagado | Gmail SMTP |
| Simulador de pagos | Activo | `false` si se usa Wompi |
| `FRONTEND_BASE_URL` | URL de pruebas | `https://integrate-demo.pages.dev` |
| `API_ORIGIN` (Pages) | SnapDeploy | `https://2-31-111-240.sslip.io` |

```powershell
git checkout main
git merge pruebas
git push origin main
```

```bash
bash infra/scripts/hetzner.sh deploy
bash infra/scripts/pages.sh deploy produccion
bash infra/scripts/hetzner.sh status
# Si algo sale mal:
bash infra/scripts/hetzner.sh rollback
```

> [!WARNING]
> Un push a `main` también reconstruye SnapDeploy si el contenedor sigue enlazado a `main` (`docs/despliegue/demo-gratuita.md` §2, «Cambiar la rama que sigue SnapDeploy»). Verifique a qué rama está ligado antes de empujar.

## 3. Reglas para no confundirse

1. **Nunca** copiar valores de un `.env.*` a otro por el chat ni subirlos a Git. Solo viajan los nombres (`.env.example`).
2. `localhost` en una variable → solo vale en **local**. Si aparece en pruebas o producción, es un error.
3. Una URL `*.pages.dev` o `*.sslip.io` en `.env.development` → error: local no debe apuntar a pruebas ni a producción.
4. Nunca apuntar `.env.development` ni `.env.demo.pruebas` a la base de producción de Neon (`ep-calm-firefly-…`).
5. Ramas: `local` → `pruebas` → `main`. Nunca saltar de `local` a `main`.
6. Cada nueva variable de entorno se agrega **con placeholder** a `infra/env/.env.example` y se crea en los tres archivos reales.
7. Antes de cada `git push`: checklist SEC-DIR-004 §7 (`git status`, `git diff --cached`, cero `.env` en staging).

## 4. Checklist rápido por ambiente

**Local:** Docker `healthy` · backend arriba con perfil `dev` · PWA en `:5173` · `mvnw verify` verde · Quality Gate verde.

**Pruebas:** cupo SnapDeploy disponible · merge `local` → `pruebas` · build terminado · log solo con host de la rama `pruebas` · `pages.sh deploy pruebas` · contenedor detenido al terminar.

**Producción:** pruebas verificado · merge a `main` · `hetzner.sh deploy` · `pages.sh deploy produccion` · `/api/v1/privacy-policies/current` responde JSON · rollback a mano si falla.

## 5. Situación actual (2026-11-10)

El backend de pruebas sigue con la versión anterior porque SnapDeploy rechazó la reconstrucción (límite de 5 despliegues por 12 h; cupo disponible en ≈ 9 h 22 min). Mientras tanto, **Incidentes** y **Liquidaciones** pueden fallar en pruebas. Verifíquelos en `local` (Paso A) y haga **un único** push a `pruebas` cuando el cupo se restablezca.

