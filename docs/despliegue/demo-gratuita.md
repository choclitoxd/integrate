# Despliegue de demostración gratuito · Intégrate

Cómo publicar Intégrate en Internet sin costo y sin tarjeta de crédito, según
[ADR-0024](../architecture/adr/ADR-0024-despliegue-demo-gratuito-neon-koyeb-cloudflare.md):

| Pieza | Servicio | Qué aloja |
| :--- | :--- | :--- |
| Base de datos | **Neon** | PostgreSQL 16+ (roles `integrate_owner`, `integrate_api`, `integrate_reporting`). |
| Backend | **Koyeb** (instancia Free) | `backend-api` construido con [`backend-api/Dockerfile`](../../backend-api/Dockerfile). |
| PWA | **Cloudflare Pages** | `frontend-pwa/dist` y el proxy [`functions/api/[[path]].ts`](../../frontend-pwa/functions/api/[[path]].ts). |
| Archivos | **Backblaze B2** | Buckets privados de vouchers y de documentos sensibles (API S3). |
| Correo | **Brevo SMTP** | Correos transaccionales (300/día). |

> [!WARNING]
> Es un entorno de **demostración**, no de producción. Koyeb Free se suspende sin tráfico y Neon
> suspende la base a los ~5 min: mientras duermen no corren la expiración de reservas (TTL 15 min),
> el envío de correos pendientes ni el barrido de vouchers, y la primera petición tarda varios
> segundos. No cargue datos reales de víctimas: la excepción de secretos de ADR-0024 §5 deja de valer.

Condiciones de los planes verificadas por última vez: **2026-10-18**. Cambian sin aviso; revíselas
al crear cada cuenta. Active **2FA** en Neon, Koyeb, Cloudflare, Backblaze, Brevo y GitHub.

---

## 1. Requisitos

- Repositorio en GitHub con la rama `main` actualizada (Koyeb y Pages despliegan desde ahí).
- `psql` 15 o superior (o Docker, ver paso 2.3) y `openssl` para generar llaves.
- Cuentas en Neon, Koyeb, Cloudflare, Backblaze y Brevo.

Orden recomendado: **Neon → B2 → Brevo → Koyeb → Cloudflare Pages → volver a Koyeb** (para
`FRONTEND_BASE_URL`, que solo se conoce al crear Pages).

## 2. Neon (base de datos)

1. Cree un proyecto con PostgreSQL 16 o superior. Región junto a la de Koyeb:
   **AWS us-east-1 ↔ Koyeb Washington** o **AWS eu-central-1 ↔ Koyeb Frankfurt**.
2. Copie la cadena de conexión del rol propietario (`neondb_owner`) **sin** `-pooler` en el host.
3. Genere tres contraseñas fuertes y ejecute el bootstrap una sola vez (es idempotente):

   ```bash
   export INTEGRATE_OWNER_PASSWORD='<contraseña-owner>'
   export INTEGRATE_API_PASSWORD='<contraseña-api>'
   export INTEGRATE_REPORTING_PASSWORD='<contraseña-reporting>'
   psql "postgresql://neondb_owner:<pwd>@ep-xxx.<region>.aws.neon.tech/neondb?sslmode=require" \
     -f infra/postgres/init/00_bootstrap.sql
   ```

   Sin `psql` local, con Docker:

   ```bash
   docker run --rm -e INTEGRATE_OWNER_PASSWORD -e INTEGRATE_API_PASSWORD -e INTEGRATE_REPORTING_PASSWORD \
     -v "$PWD/infra/postgres/init:/init:ro" postgres:16 \
     psql "postgresql://neondb_owner:<pwd>@ep-xxx.<region>.aws.neon.tech/neondb?sslmode=require" -f /init/00_bootstrap.sql
   ```

4. No ejecute la semilla `infra/postgres/seed/dev_test_data.sql`: contiene cuentas de prueba con
   contraseñas conocidas.
5. Las tablas las crea Flyway al primer arranque del backend (rol `integrate_owner`). Las
   extensiones `pg_trgm` y `btree_gist` son *trusted*: las crea el dueño de la base sin superusuario.
6. **Después del primer arranque exitoso del backend** (paso 6), publique la política de privacidad de
   demostración. Sin ella `GET /api/v1/privacy-policies/current` responde 404 y nadie puede registrarse
   (la política de ejemplo solo viene con el perfil `dev`). Es idempotente:

   ```bash
   psql "postgresql://integrate_owner:<contraseña-owner>@ep-xxx.<region>.aws.neon.tech/integrate?sslmode=require" \
     -f infra/postgres/seed/demo_privacy_policy.sql
   ```

**Por qué el endpoint directo:** el pooler de Neon trabaja en modo transacción y rompe el
`SELECT … FOR UPDATE` de las reservas (ADR-0008) y los `statement_timeout` / `lock_timeout` fijados
por rol en el bootstrap.

## 3. Backblaze B2 (archivos)

1. Cree **dos buckets privados**, p. ej. `integrate-demo-vouchers` y `integrate-demo-sensitive`.
2. En *Application Keys*, cree una llave con acceso **Read and Write** limitada a esos dos buckets.
   Guarde `keyID` y `applicationKey`: la segunda solo se muestra una vez.
3. Anote el endpoint S3 del bucket (p. ej. `https://s3.us-east-005.backblazeb2.com`) y su región
   (`us-east-005`). `StorageConfig` usa *path-style* automáticamente cuando hay endpoint propio.

## 4. Brevo (correo)

1. *SMTP & API → SMTP*: copie el **Login** SMTP y genere una **SMTP key**.
2. *Senders*: verifique el remitente que usará en `MAIL_FROM`.

## 5. Llaves de la demo

Genérelas **nuevas** (no reutilice las de `.env.development`) y no las guarde en el repositorio:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 | base64 -w0   # JWT_SIGNING_KEY
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 | base64 -w0   # VOUCHER_SIGNING_KEY
openssl rand -base64 32                                                    # SENSITIVE_DATA_KEY_V1
openssl rand -base64 32                                                    # PAYMENT_WEBHOOK_SECRET_MOCK
```

En macOS `base64` no admite `-w0`: use `base64 | tr -d '\n'`. Respalde `SENSITIVE_DATA_KEY_V1` en un
gestor de contraseñas: si se pierde, los datos cifrados de víctimas son irrecuperables.

## 6. Koyeb (backend)

1. *Secrets → Create secret*: cree un secreto por cada valor marcado 🔒 en la tabla de abajo.
2. *Create Web Service → GitHub*: repositorio de Intégrate, rama `main`.
   - **Builder:** Dockerfile · **Work directory:** `backend-api` · **Dockerfile location:** `Dockerfile`.
   - **Instance:** Free · **Region:** la misma zona que Neon.
   - **Port:** `8080`, protocolo HTTP, ruta `/`.
   - **Health check:** **TCP** en el puerto `8080` (el backend no expone Actuator).
3. *Environment variables*: cargue la tabla. Los 🔒 se referencian como `{{ secret.NOMBRE }}`.
4. Despliegue y espere en los logs `Started IntegrateApplication`. Anote la URL pública
   (`https://<servicio>-<org>.koyeb.app`).
5. Tras crear el primer ADMIN, **elimine** `BOOTSTRAP_ADMIN_EMAIL` y `BOOTSTRAP_ADMIN_PASSWORD` y
   vuelva a desplegar.

### Variables del servicio

| Variable | Valor | 🔒 |
| :--- | :--- | :---: |
| `DATABASE_URL` | `jdbc:postgresql://ep-xxx.<region>.aws.neon.tech/integrate?sslmode=require` | |
| `DATABASE_USERNAME` | `integrate_api` | |
| `DATABASE_PASSWORD` | contraseña de `integrate_api` | 🔒 |
| `FLYWAY_USER` | `integrate_owner` | |
| `FLYWAY_PASSWORD` | contraseña de `integrate_owner` | 🔒 |
| `DB_POOL_MAX_SIZE` | `5` | |
| `JWT_SIGNING_KEY` | RSA PKCS#8 en Base64 (paso 5) | 🔒 |
| `JWT_SIGNING_KEY_ID` | `session-demo-1` | |
| `VOUCHER_SIGNING_KEY` | RSA PKCS#8 en Base64 (paso 5) | 🔒 |
| `VOUCHER_SIGNING_KEY_ID` | `voucher-demo-2026-10` | |
| `SENSITIVE_DATA_KEY_V1` | 32 bytes en Base64 (paso 5) | 🔒 |
| `SENSITIVE_DATA_KEY_CURRENT_VERSION` | `1` | |
| `FRONTEND_BASE_URL` | `https://<proyecto>.pages.dev` (paso 7) | |
| `CORS_ALLOWED_ORIGINS` | *(vacío: Pages reenvía en el mismo origen)* | |
| `PAYMENT_PROVIDER` | `MOCK` (o `WOMPI` con llaves de prueba) | |
| `PAYMENT_WEBHOOK_SECRET_MOCK` | 32 bytes en Base64 (paso 5) | 🔒 |
| `AWS_ENDPOINT_URL` | endpoint S3 de B2 | |
| `AWS_REGION` | región de B2, p. ej. `us-east-005` | |
| `AWS_S3_BUCKET_NAME` | bucket de vouchers | |
| `AWS_S3_SENSITIVE_BUCKET_NAME` | bucket de documentos sensibles | |
| `AWS_ACCESS_KEY_ID` | `keyID` de B2 | 🔒 |
| `AWS_SECRET_ACCESS_KEY` | `applicationKey` de B2 | 🔒 |
| `SMTP_HOST` / `SMTP_PORT` | `smtp-relay.brevo.com` / `587` | |
| `SMTP_AUTH` / `SMTP_STARTTLS` | `true` / `true` | |
| `SMTP_USERNAME` | Login SMTP de Brevo | 🔒 |
| `SMTP_PASSWORD` | SMTP key de Brevo | 🔒 |
| `MAIL_FROM` | `Integrate <remitente.verificado@…>` | |
| `BOOTSTRAP_ADMIN_EMAIL` | correo del primer ADMIN (solo primer arranque) | |
| `BOOTSTRAP_ADMIN_PASSWORD` | 12–128 caracteres (solo primer arranque) | 🔒 |

`JAVA_TOOL_OPTIONS` y `SERVER_PORT` ya vienen en la imagen; solo agréguelos para sobrescribirlos.
Con Wompi: `WOMPI_PUBLIC_KEY` y 🔒 `WOMPI_INTEGRITY_SECRET`, `WOMPI_EVENTS_SECRET`; el webhook se
configura en Wompi apuntando a `https://<servicio>.koyeb.app/api/v1/payments/webhook/wompi`.

**No** defina `SPRING_PROFILES_ACTIVE=dev`: cargaría `db/seed-dev` (política de privacidad de desarrollo).

## 7. Cloudflare Pages (PWA)

1. *Workers & Pages → Create → Pages → Connect to Git*: repositorio de Intégrate, rama `main`.
2. Configuración de build:
   - **Framework preset:** None · **Root directory:** `frontend-pwa`
   - **Build command:** `npm ci && npm run build` · **Build output directory:** `dist`
3. Variables (*Settings → Variables and Secrets*, entorno **Production**):

   | Variable | Valor | Uso |
   | :--- | :--- | :--- |
   | `NODE_VERSION` | `22.12.0` | Versión de Node del build (`engines` de `package.json`). |
   | `VITE_API_BASE_URL` | `/api` | Se incrusta en el bundle: la PWA llama a su propio origen. |
   | `API_ORIGIN` | `https://<servicio>.koyeb.app` | Destino del proxy `functions/api/[[path]].ts`. Sin barra final. |

4. Despliegue. Pages publica `dist/` y, como `functions/` está en el directorio raíz, monta el proxy
   en `/api/*` sin configuración adicional.
5. Copie la URL (`https://<proyecto>.pages.dev`) en `FRONTEND_BASE_URL` de Koyeb y vuelva a desplegar
   el backend (los enlaces de restablecer contraseña la usan).

**Por qué el proxy:** la cookie de refresh es `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`
(ADR-0005). Entre `*.pages.dev` y `*.koyeb.app` el navegador no la enviaría y la sesión se perdería al
recargar. Con el proxy la PWA y la API comparten origen, igual que con el proxy de Vite en desarrollo.

## 8. Verificación

1. `https://<proyecto>.pages.dev` carga el catálogo (la primera vez puede tardar mientras Koyeb despierta).
2. `https://<proyecto>.pages.dev/api/v1/privacy-policies/current` responde JSON (si da 404, falte el paso 2.6).
3. Inicie sesión con el ADMIN, **recargue la página** y compruebe que la sesión sigue activa (cookie de refresh).
4. Haga una reserva con `MOCK`, confirme el pago y descargue el voucher (escritura y lectura en B2).
5. Pida restablecer contraseña y revise que el correo llegue con un enlace a `pages.dev`.

## 9. Operación

| Tarea | Cómo |
| :--- | :--- |
| Desplegar cambios | Push o merge a `main`: Koyeb y Pages reconstruyen solos. |
| Nueva migración | Se aplica al arrancar el backend (Flyway, rol `integrate_owner`). |
| Rotar la llave de vouchers | Nueva `VOUCHER_SIGNING_KEY` y nuevo `VOUCHER_SIGNING_KEY_ID`; la anterior queda `RETIRED`. |
| Rotar un secreto filtrado | Revocar en el proveedor, actualizar el secreto en Koyeb, redesplegar ([SOP-SEC-01](../security/security_secrets_policy.md)). |
| Probar la imagen en local | `docker build -t integrate-api backend-api` (usa la imagen `maven:3-eclipse-temurin-21`, no `./mvnw`: en Linux el wrapper valida un SHA-256 del `.zip` pero descarga el `.tar.gz` y siempre falla). |

## 10. Limitaciones conocidas

- **Suspensión:** ver la advertencia del inicio. Para una sustentación, abra la app unos minutos antes.
- **IP del cliente:** el backend registra la IP con `getRemoteAddr()` (ADR-0005 §4), que detrás de
  Koyeb y Cloudflare es la de la infraestructura, no la del usuario. El proxy envía `X-Forwarded-For`,
  pero el backend no la lee: activar `server.forward-headers-strategy` exige una decisión sobre
  proxies de confianza que queda fuera de esta demo.
- **Mapas de fuente:** `vite build` genera *sourcemaps* (`build.sourcemap: true`) y Pages los publica.
  No contienen secretos, pero exponen el código fuente de la PWA.
- **Cuota de Pages Functions:** ~100 000 invocaciones/día en el plan gratuito.

## 11. Problemas frecuentes

| Síntoma | Causa probable y solución |
| :--- | :--- |
| `/api/*` responde 502 «API_ORIGIN no está configurada» | Falta `API_ORIGIN` en Pages o no empieza por `https://`. Redespliegue tras añadirla. |
| `/api/*` responde 502 «El backend no respondió» | Koyeb está despertando o caído: espere unos segundos y revise sus logs. |
| La PWA muestra «VITE_API_BASE_URL no está configurada» | Falta la variable de build en Pages: se lee al compilar, no en tiempo de ejecución. |
| El backend aborta con `… is required` o `Could not resolve placeholder` | Falta una variable de la tabla del paso 6. |
| `FATAL: password authentication failed` | La contraseña del secreto no coincide con la del bootstrap, o se usó el usuario de Neon. |
| Flyway falla con `permission denied` | El bootstrap no se ejecutó sobre la base `integrate` o se conectó con otro rol. |
| Errores `prepared statement … does not exist` o timeouts raros | `DATABASE_URL` apunta al host `-pooler`: use el endpoint directo. |
| El servicio se reinicia con `OutOfMemoryError` / `OOMKilled` | Baje `DB_POOL_MAX_SIZE` o `MaxRAMPercentage` en `JAVA_TOOL_OPTIONS`. |
| La sesión se pierde al recargar | La PWA se construyó con una `VITE_API_BASE_URL` absoluta: debe ser `/api`. |
| El voucher no se descarga (`AccessDenied` / `NoSuchBucket`) | Llave de B2 sin acceso al bucket, o región/endpoint distintos a los del bucket. |
