# Despliegue · Intégrate

Guía única de despliegue. Cubre la producción 24/7, el entorno de pruebas, la demo gratuita, la PWA y los usuarios de prueba.

**Índice:** [1. Producción 24/7 (Hetzner)](#1-producción-247-en-hetzner-adr-0027) · [2. Entorno de pruebas y promoción](#2-entorno-de-pruebas-y-promoción-a-producción-adr-0031) · [3. Demo gratuita](#3-demo-gratuita-snapdeploy-neon-pages-b2-y-brevo) · [4. PWA en Cloudflare Pages](#4-desplegar-la-pwa-en-cloudflare-pages) · [5. Usuarios y datos de prueba](#5-usuarios-y-datos-de-prueba)

## 1. Producción 24/7 en Hetzner (ADR-0027)

Guía de cómo está desplegada la API para que no se duerma, y cómo operarla. La PWA sigue en Cloudflare Pages (ADR-0024) y la base de datos en Neon. No contiene secretos.

### Arquitectura

```
Usuario → Cloudflare Pages (PWA + Function /api/*) → https://<ip-con-guiones>.sslip.io → Caddy (VPS) → API Spring Boot → Neon
```

- **Servidor:** Hetzner Cloud `integrate-api`, plan CPX12 (1 vCPU, 2 GB RAM), Ubuntu 24.04, Falkenstein, con 2 GB de swap.
- **HTTPS:** Caddy obtiene y renueva solo el certificado de Let's Encrypt sobre `<ip-con-guiones>.sslip.io` (por ejemplo `2-31-111-240.sslip.io`). No hay dominio propio; si se compra uno, solo cambia `API_HOST`.
- **Seguridad:** acceso solo por llave SSH al usuario `integrate` (sin root ni contraseña), cortafuegos de Hetzner y `ufw` con solo los puertos 22, 80 y 443, `fail2ban` y actualizaciones automáticas. La API no publica puertos: solo Caddy.
- **Proxy de la PWA:** el secreto `API_ORIGIN` del proyecto `integrate-demo` en Cloudflare Pages apunta a esa URL HTTPS.

### Archivos

| Archivo | Para qué sirve |
| :--- | :--- |
| `infra/docker/compose.vps.yml` | API + Caddy, con reinicio automático y límite de memoria. |
| `infra/docker/Caddyfile` | Proxy inverso hacia la API. |
| `~/integrate/.env` (solo en el servidor, permisos 600) | Variables de la API y `API_HOST`. Se genera desde `infra/env/.env.demo`. Nunca va a Git. |

### Cómo se creó

1. Servidor y cortafuegos con la API de Hetzner (token en `infra/env/.env.hetzner`, ignorado por Git) y la llave SSH `integrate-vps`.
2. Configuración inicial con cloud-init: usuario `integrate`, Docker, swap, `ufw`, `fail2ban` y endurecimiento de SSH.
3. Imagen construida en el PC (`docker build -t integrate-api:vps backend-api`) y enviada con `docker save | gzip | ssh ... docker load`.
4. Se copian `compose.vps.yml`, `Caddyfile` y `.env` al servidor y se ejecuta `docker compose -f compose.vps.yml up -d`.
5. `wrangler pages secret put API_ORIGIN` y `wrangler pages deploy dist` para apuntar la PWA al servidor.

### Operación

```bash
ssh -i ~/.ssh/id_ed25519 integrate@<IP>
cd ~/integrate
docker compose -f compose.vps.yml ps
docker compose -f compose.vps.yml logs -f api
```

**Actualizar la API:** construir la imagen nueva en el PC, enviarla con `docker save | ssh ... docker load` y ejecutar `docker compose -f compose.vps.yml up -d api`.

**Memoria:** la JVM usa `-Xmx768m` y el contenedor tiene un tope de 1400 MB. Con una sola vCPU y 2 GB, vigile `docker stats` y el swap; si se queda corto, se sube a un plan con 4 GB desde el panel de Hetzner sin reinstalar.

### Riesgos asumidos

- Un solo servidor y sin alta disponibilidad: si cae, cae el backend. Conviene un monitor externo gratuito sobre `/api/v1/privacy-policies/current`.
- `sslip.io` es un servicio de terceros: si dejara de resolver, hay que cambiar `API_HOST` por un dominio propio.
- **Hetzner es la producción; SnapDeploy es el entorno de pruebas** (ADR-0027). Los dos **no deben compartir base de datos**: con la misma base de Neon los procesos de fondo se duplicarían y las pruebas ensuciarían los datos reales. El entorno de pruebas necesita su propia base (rama o base separada en Neon) y sus propias variables (`ENV_FILE` del script `snapdeploy.sh`); mientras no la tenga, el contenedor de SnapDeploy debe estar detenido, porque cada push a `main` lo reconstruye y lo despierta.

## 2. Entorno de pruebas y promoción a producción (ADR-0031)

Cómo se prueba un cambio antes de llevarlo a producción. No contiene secretos: las credenciales viven en archivos locales ignorados por Git (`infra/env/.env.*`).

### Piezas

| | Pruebas | Producción |
| :--- | :--- | :--- |
| Backend | SnapDeploy (contenedor `integrate-0da6e`), detenido mientras no se prueba | Hetzner Cloud `integrate-api`, 24/7 |
| Base de datos | Rama `pruebas` de Neon (copia de producción) | Rama principal de Neon |
| PWA | `https://pruebas.integrate-demo.pages.dev` | `https://integrate-demo.pages.dev` |
| Rama de Git | `pruebas` | `main` |
| Variables | `infra/env/.env.demo.pruebas` | `infra/env/.env.demo` (en el servidor: `~/integrate/.env`) |
| Correos | **Apagados** (`INTEGRATE_NOTIFICATION_DISPATCH_ENABLED=false`) | Brevo |

La rama de Neon copia usuarios reales: trátela como dato sensible. Si se envejece, se restablece desde producción en el panel de Neon («Reset from parent»), con nadie probando.

### Flujo

```
cambio → rama `pruebas` → SnapDeploy + PWA de vista previa → verificar → merge a `main` → Hetzner + PWA de producción → verificar
```

1. **Probar:** empuje el cambio a la rama `pruebas`; SnapDeploy la reconstruye. Despierte el contenedor (`bash infra/scripts/snapdeploy.sh wake`) y publique la vista previa (`bash infra/scripts/pages.sh deploy pruebas`).
2. **Comprobar el aislamiento antes de confiar en el entorno** (ver el apartado siguiente).
3. **Verificar** el cambio en `pruebas.integrate-demo.pages.dev`. Al terminar, deténgalo (`POST …/containers/<id>/stop` o desde el panel).
4. **Promover:** fusione a `main` y ejecute `bash infra/scripts/hetzner.sh deploy` y `bash infra/scripts/pages.sh deploy produccion`. Si algo sale mal: `bash infra/scripts/hetzner.sh rollback`.

### Comprobar el aislamiento (obligatorio tras cambiar variables)

SnapDeploy **aplica las variables solo al construir un despliegue nuevo**: `snapdeploy.sh env` las guarda, pero un contenedor detenido que se despierta arranca con las que tenía al construirse. El 2026-10-27 esto hizo que el entorno de pruebas arrancara contra la base de **producción** durante unos minutos (se detuvo y se limpió: un solo usuario de prueba y un correo a una dirección ficticia, sin reservas ni pagos tocados).

Por eso, antes de usar el entorno de pruebas:

1. Despierte el contenedor y lea el log: `bash infra/scripts/snapdeploy.sh logs 400 | grep -oE "ep-[a-z]+-[a-z]+-[a-z0-9]+" | sort -u` debe mostrar **solo el host de la rama** (`ep-orange-frog-…`), nunca el de producción (`ep-calm-firefly-…`). Si aparece el de producción, deténgalo de inmediato.
2. Registre un usuario en la PWA de pruebas y compruebe que **no** puede iniciar sesión en producción (debe dar 401).
3. Pida un restablecimiento de contraseña y compruebe en la bitácora de la rama que el correo queda `PENDING` y no pasa a `SENT`.

### Cambiar la rama que sigue SnapDeploy

`snapdeploy.sh link --branch pruebas` enlaza el repositorio a esa rama y lanza una construcción nueva con las variables guardadas. Si el contenedor ya tiene un repositorio enlazado, SnapDeploy responde 400 y **no permite desenlazarlo con llaves de API**: hay que hacerlo en su panel (web o móvil) y volver a ejecutar el comando. Mientras tanto, el contenedor sigue ligado a `main` (cada push a `main` lo reconstruye).

### Lista de cierre (la hace el desarrollador)

- [ ] Crear el rol `integrate_api` en la rama `pruebas` y reemplazar el provisional `integrate_owner` en `.env.demo.pruebas`.
- [ ] Monitor externo gratuito (por ejemplo UptimeRobot) sobre `https://2-31-111-240.sslip.io/api/v1/privacy-policies/current`, cada 5 minutos.
- [ ] Decidir si se borra el servidor duplicado `integrate-server` de Hetzner (apagado, pero se cobra).
- [ ] Al terminar el proyecto: revocar los tokens de Hetzner, SnapDeploy y Cloudflare; regenerar la llave de Google Maps; rotar la contraseña de la rama de Neon. Todos pasaron por el chat.
- [ ] Administrador pendiente: crear la cuenta cuando haya un correo permanente.

## 3. Demo gratuita: SnapDeploy, Neon, Pages, B2 y Brevo

> **Vigencia.** La **producción 24/7** corre en Hetzner Cloud ([ADR-0027](../architecture/adr/ADR-0027-backend-24-7-en-vps-hetzner.md), sección 1 de esta guía). La sección 3 describe SnapDeploy, que ahora es el **entorno de pruebas** (con su propia base de datos), y las piezas compartidas: Neon, Cloudflare Pages, Backblaze B2 y Brevo.

Cómo publicar Intégrate en Internet sin costo y sin tarjeta de crédito, según
[ADR-0026](../architecture/adr/ADR-0026-backend-demo-en-snapdeploy-plan-gratuito.md) (backend en SnapDeploy),
[ADR-0025](../architecture/adr/ADR-0025-backend-demo-en-pc-propia-con-cloudflare-tunnel.md) (plan B: backend en su PC) y
[ADR-0024](../architecture/adr/ADR-0024-despliegue-demo-gratuito-neon-koyeb-cloudflare.md) (Neon, Pages, B2; Koyeb ya no existe para cuentas nuevas):

| Pieza | Servicio | Qué aloja |
| :--- | :--- | :--- |
| Base de datos | **Neon** | PostgreSQL 16+ (roles `integrate_owner`, `integrate_api`, `integrate_reporting`). |
| Backend (camino A) | **SnapDeploy** (plan Free) | `backend-api` construido desde [`backend-api/Dockerfile`](../../backend-api/Dockerfile). |
| Backend (camino B, plan B) | **Tu PC** con Docker Compose + **Cloudflare Tunnel** | Definido en [`infra/docker/compose.demo.yml`](../../infra/docker/compose.demo.yml); el túnel publica la API por HTTPS sin abrir puertos del router. |
| PWA | **Cloudflare Pages** | `frontend-pwa/dist` y el proxy [`functions/api/[[path]].ts`](../../frontend-pwa/functions/api/[[path]].ts). |
| Archivos | **Backblaze B2** | Buckets privados de vouchers y de documentos sensibles (API S3). |
| Correo | **Brevo SMTP** | Correos transaccionales (300/día). |

> [!WARNING]
> Es un entorno de **demostración**, no de producción. En el camino A el contenedor de SnapDeploy
> **se duerme a los 15 minutos sin tráfico**, solo despierta si usted abre su URL en un navegador y toma más de un minuto; en el camino B la API
> **solo responde mientras su PC esté encendido, con internet y con Docker Desktop abierto**. No cargue
> datos reales de víctimas: el tráfico pasa por Cloudflare, las variables no están en un gestor de secretos
> y la política de privacidad es un borrador.

Condiciones de los planes verificadas por última vez: **2026-10-19**. Cambian sin aviso; revíselas al
crear cada cuenta. Active **2FA** en Neon, Cloudflare, Backblaze, Brevo y GitHub.

### Elija dónde corre el backend

| | **A · SnapDeploy** (Free) | **B · Su PC + Cloudflare Tunnel** |
| :--- | :--- | :--- |
| Depende de | La nube de SnapDeploy (proveedor pequeño) | Que su PC esté encendido |
| Recursos | 512 MB, **0,25 vCPU**, 100 h/mes despierto | Los de su PC |
| Arranque en frío | Duerme a los 15 min; **despierta solo si usted abre su URL en un navegador** (~1 min más el arranque de la JVM) | Sin dormir |
| Riesgo principal | Arranque lento con 0,25 vCPU y despertar manual (ver «Puerta de verificación») | Disponibilidad |
| Condiciones | No pide tarjeta según su web (verifíquelo al registrarse) | — |

Empiece por el **camino A**. Si falla la puerta de verificación del paso 6.A, use el camino B (ADR-0026 §2).

#### Modos del túnel (solo camino B)

| | **`quick`** (sin dominio) | **`named`** (con dominio) |
| :--- | :--- | :--- |
| Costo / cuentas | Gratis, sin cuenta de túnel | Requiere un dominio (≈ 10 USD/año) con su DNS en Cloudflare |
| URL de la API | `https://<palabras>.trycloudflare.com`, **cambia en cada reinicio del contenedor** | Fija, p. ej. `https://api.sudominio.com` |
| Al cambiar la URL | Hay que actualizar `API_ORIGIN` en Pages y redesplegar (paso 8) | No aplica |
| Disponibilidad | Cloudflare no la garantiza | Estable mientras el PC esté activo |
| Pagos | Solo `MOCK` | `MOCK` o Wompi (webhook con URL fija) |
| Úselo para | Pruebas y la sustentación | Dejarlo publicado varios días |

Puede empezar con `quick` y pasar a `named` más adelante sin tocar la PWA. El camino A no usa túnel.

### 1. Requisitos

- `git` y, para el camino B (o para el camino A si SnapDeploy no construye desde el subdirectorio), Docker Desktop. Repositorio en GitHub con `main` actualizada
  (Pages despliega desde ahí).
- `psql` 15 o superior (o Docker, ver paso 2.3) y `openssl` para generar llaves.
- Cuentas en Neon, Cloudflare, Backblaze y Brevo.
- **Decida ya el nombre del proyecto de Pages**: su URL (`https://<nombre>.pages.dev`) va en
  `FRONTEND_BASE_URL` antes de arrancar el backend.

Orden: **Neon → B2 → Brevo → llaves y `.env.demo` → backend (A o B) → política de privacidad →
Cloudflare Pages → verificación**.

### 2. Neon (base de datos)

1. Cree un proyecto con PostgreSQL 16 o superior, en la región más cercana a usted.
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

**Por qué el endpoint directo:** el pooler de Neon trabaja en modo transacción y rompe el
`SELECT … FOR UPDATE` de las reservas (ADR-0008) y los `statement_timeout` / `lock_timeout` fijados
por rol en el bootstrap.

### 3. Backblaze B2 (archivos)

1. Cree **dos buckets privados**, p. ej. `integrate-demo-vouchers` y `integrate-demo-sensitive`.
2. En *Application Keys*, cree una llave con acceso **Read and Write** limitada a esos dos buckets.
   Guarde `keyID` y `applicationKey`: la segunda solo se muestra una vez.
3. Anote el endpoint S3 del bucket (p. ej. `https://s3.us-east-005.backblazeb2.com`) y su región
   (`us-east-005`). `StorageConfig` usa *path-style* automáticamente cuando hay endpoint propio.

### 4. Brevo (correo)

1. *SMTP & API → SMTP*: copie el **Login** SMTP y genere una **SMTP key**.
2. *Senders*: verifique el remitente que usará en `MAIL_FROM`.

### 5. Llaves y `.env.demo`

Genere las llaves **nuevas** (no reutilice las de `.env.development`):

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 | base64 -w0   # JWT_SIGNING_KEY
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 | base64 -w0   # VOUCHER_SIGNING_KEY
openssl rand -base64 32                                                    # SENSITIVE_DATA_KEY_V1
openssl rand -base64 32                                                    # PAYMENT_WEBHOOK_SECRET_MOCK
```

En macOS `base64` no admite `-w0`: use `base64 | tr -d '\n'`. Respalde `SENSITIVE_DATA_KEY_V1` en un
gestor de contraseñas: si se pierde, los datos cifrados de víctimas son irrecuperables.

Cree el archivo de variables (local, **ignorado por Git**: no lo comitee, SEC-DIR-004):

```bash
cp infra/env/.env.example infra/env/.env.demo
```

Edítelo y deje solo las variables de la tabla (las demás líneas del `.env.example`, como Postgres local o
LocalStack, sobran: bórrelas). **No** defina `SPRING_PROFILES_ACTIVE=dev`: cargaría `db/seed-dev`.

| Variable | Valor |
| :--- | :--- |
| `DATABASE_URL` | `jdbc:postgresql://ep-xxx.<region>.aws.neon.tech/integrate?sslmode=require` |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | `integrate_api` / su contraseña del paso 2 |
| `FLYWAY_USER` / `FLYWAY_PASSWORD` | `integrate_owner` / su contraseña del paso 2 |
| `DB_POOL_MAX_SIZE`, `SERVER_TOMCAT_THREADS_MAX` | `4`, `16` (menos hilos y conexiones = menos memoria) |
| `JWT_SIGNING_KEY`, `JWT_SIGNING_KEY_ID` | RSA PKCS#8 en Base64 · `session-demo-1` |
| `VOUCHER_SIGNING_KEY`, `VOUCHER_SIGNING_KEY_ID` | RSA PKCS#8 en Base64 · `voucher-demo-2026-10` |
| `SENSITIVE_DATA_KEY_V1`, `SENSITIVE_DATA_KEY_CURRENT_VERSION` | 32 bytes en Base64 · `1` |
| `FRONTEND_BASE_URL` | `https://<nombre-del-proyecto-pages>.pages.dev` |
| `CORS_ALLOWED_ORIGINS` | *(vacío: Pages reenvía en el mismo origen)* |
| `PAYMENT_PROVIDER`, `PAYMENT_WEBHOOK_SECRET_MOCK` | `MOCK` · 32 bytes en Base64 |
| `AWS_ENDPOINT_URL`, `AWS_REGION` | endpoint y región de B2 |
| `AWS_S3_BUCKET_NAME`, `AWS_S3_SENSITIVE_BUCKET_NAME` | bucket de vouchers · bucket sensible |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | `keyID` y `applicationKey` de B2 |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_AUTH`, `SMTP_STARTTLS` | `smtp-relay.brevo.com`, `587`, `true`, `true` |
| `SMTP_USERNAME`, `SMTP_PASSWORD`, `MAIL_FROM` | Login SMTP, SMTP key y remitente verificado de Brevo |
| `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD` | Primer ADMIN (12–128 caracteres); bórrelas tras el primer arranque |
| `TUNNEL_TOKEN` | Solo camino B, modo `named` (paso 6.B.2); en `quick` y en el camino A no se usa |

`JAVA_TOOL_OPTIONS` ya viene ajustado en la imagen para 512 MB (ver «Memoria» más abajo); no
defina `SERVER_PORT`, el Compose lo fija en 8080.

> [!NOTE]
> El repositorio vive en OneDrive: el `.env.demo` se sincronizaría a su nube personal (privada). Para
> evitarlo, excluya `infra/env/` de la sincronización o trabaje con el repositorio fuera de OneDrive.

### 6. Backend

#### 6.A Camino A · SnapDeploy (plan Free)

Datos del plan según su [documentación oficial](https://snapdeploy.dev/docs/free-tier) (2026-10-19): contenedor
*Small* de 512 MB y 0,25 vCPU, 100 horas al mes **por cuenta** solo con el contenedor despierto, 10 despliegues
al día, URL `<nombre>.containers.snapdeploy.app` con HTTPS, conexiones salientes permitidas a los puertos 1 a 27017
(cubre Neon, 5432, y Brevo, 587). Se duerme a los 15 minutos sin tráfico y **solo despierta cuando una persona abre
su URL en un navegador**: las llamadas automáticas, incluido el proxy de Pages, reciben un 503 y no lo despiertan.
Su panel puede diferir de lo que sigue: oriéntese por los nombres, no por la posición.

1. Regístrese **sin** tarjeta (si la pide, pare y use el camino B) y active 2FA si lo ofrece.
2. **No cree el contenedor desde el botón «Deploy» del panel.** SnapDeploy da por fallido el despliegue si la
   aplicación cae en el primer arranque, y Intégrate aborta sin sus variables (*fail-fast*). Además, la pantalla de
   variables detectadas no lista `DATABASE_URL` y pegar a mano las llaves RSA (2 272 caracteres) las corrompe.
   Todo el flujo se hace con el script, que lee su `.env.demo` y llama a la API de SnapDeploy:

   ```bash
   # 1) En SnapDeploy: API Keys -> crear un token personal con alcance «deploy» y vencimiento corto (p. ej. 2 días).
   # 2) Pegue el token en infra/env/.env.snapdeploy (local, ignorado por Git; no en el chat), p. ej.:
   #      SNAPDEPLOY_TOKEN=sd_pat_...
   #      SNAPDEPLOY_NAME=integrate-api                     # nombre que tendrá el contenedor
   #    (o, sin archivo: export SNAPDEPLOY_TOKEN='sd_pat_...' y export SNAPDEPLOY_NAME='...' en esta terminal)
   bash infra/scripts/snapdeploy.sh env --dry-run          # lista los NOMBRES que enviará y avisa de lo que falte
   bash infra/scripts/snapdeploy.sh create                 # crea el contenedor con todas las variables (marcador, sin build)
   bash infra/scripts/snapdeploy.sh link                   # enlaza GitHub: rama main, root directory backend-api; lanza el build
   bash infra/scripts/snapdeploy.sh deploys                # sigue el build (4-5 min); después: logs 120
   ```

   `link` envía `rootDirectory` a la API (`POST /api/mobile/github/link`); aunque su referencia no lo documenta,
   SnapDeploy lo acepta (verificado: construyó el `Dockerfile` de `backend-api/` y el contenedor quedó `RUNNING`
   en el puerto 8080, detectado por el `EXPOSE`). El script omite las variables del túnel local (`TUNNEL_TOKEN`,
   etc.), las vacías y las que ya fija la imagen, y se niega a enviar si quedan placeholders o faltan
   obligatorias. Nunca imprime valores ni el token. SnapDeploy no ofrece gestor de secretos aparte: las variables
   son visibles para quien entre a su cuenta (excepción de ADR-0026 §4: solo demo, sin datos reales, llaves
   nuevas, 2FA).
3. **Un contenedor «marcador» (creado con `create` y sin repositorio) no se puede editar en el panel** (muestra
   «not linked to a GitHub repository»): se enlaza con `link`. Si SnapDeploy avisa de un *add-on* de base de datos,
   **no lo compre**: la base es Neon y ya está en `DATABASE_URL`.

   Para cambiar variables después (p. ej. al rotar un secreto): edite el `.env.demo` y ejecute
   `bash infra/scripts/snapdeploy.sh env` (reemplaza **todas** las del contenedor; reinicio rodante, sin reconstruir).
   No las edite en el panel. Revoque el token en *API Keys* y borre `infra/env/.env.snapdeploy` al terminar.
4. Siga el arranque con `bash infra/scripts/snapdeploy.sh logs 120` y espere `Started IntegrateApplication`. Anote la URL pública (`https://<nombre>.containers.snapdeploy.app`): es el `API_ORIGIN` del paso 8. El arranque con 0,25 vCPU tardó ≈ 90 s en la prueba real.

**Solo si el *root directory* fallara en la práctica:** publique la imagen usted mismo en GitHub
Container Registry (paquete público; la imagen no contiene secretos) y cree el contenedor desde esa imagen:

```bash
docker build -t ghcr.io/<usuario-github>/integrate-api:demo backend-api
docker login ghcr.io -u <usuario-github>          # contraseña: un token de GitHub con write:packages
docker push ghcr.io/<usuario-github>/integrate-api:demo
```

Después marque el paquete como **Public** en GitHub (*Packages → Package settings*).

##### Puerta de verificación (ADR-0026 §2)

La documentación de SnapDeploy ya confirma los puertos de salida y el *root directory*. Estas tres cosas solo se
comprueban con un despliegue real: hágalo en el primero. Si la 1 falla, abandone el camino A y use el B.

| # | Comprobación | Cómo |
| :---: | :--- | :--- |
| 1 | El arranque con 0,25 vCPU termina y la plataforma lo da por sano | Flyway aplica las 14 migraciones, aparece `Started IntegrateApplication` y el contenedor queda activo sin reinicios. Anote cuánto tardó. |
| 2 | La plataforma enruta al puerto 8080 | `https://<url>/api/v1/experiences` responde JSON (no HTML). |
| 3 | Brevo acepta el envío desde esa red | «Olvidé mi contraseña» con un correo real y llega (revise spam). |

##### Memoria: 512 MB es justo (verificado)

La tarea del plan Free tiene **512 MB y 0,25 vCPU** para toda la JVM. Con el `MaxRAMPercentage=70` original, la plataforma
mataba el proceso **sin dejar ningún mensaje** (el log simplemente se corta) y volvía a arrancarlo en bucle. Se reprodujo y
midió en local (`docker run --memory=512m --cpus=0.25`) y la configuración que quedó como valor por defecto de la imagen
(`-Xmx144m`, metaspace ≤ 144 MB, caché de código 40 MB, `TieredStopAtLevel=1`) usa 348-413 MiB durante el recorrido completo
y en vivo aguantó la suite de 108 pruebas sin reiniciarse. **No** baje el metaspace de 144 MB (con 96 MB la JVM termina con
`OutOfMemoryError: Metaspace`) ni suba el heap sin medir. Síntomas de falta de memoria: 502 de Cloudflare, el log termina de
golpe y aparece un nuevo `Started IntegrateApplication` minutos después. Si el negocio crece, el siguiente escalón es
*Always-On Medium* (2 GB).

##### Despertar la API y consumo de horas

El contenedor se duerme a los 15 minutos sin tráfico y la PWA (en Pages) **no puede despertarlo**. Antes de cada
demo o prueba, haga esto:

1. Abra en el navegador la URL de la API (`https://<nombre>.containers.snapdeploy.app`) y verá la página de espera
   de SnapDeploy. **O, sin navegador**, con el token del paso 6.A: `bash infra/scripts/snapdeploy.sh wake` (y
   `status` para ver el estado y las horas gastadas).
2. Espere ~1 minuto a que recargue sola y luego otro poco a que arranque la JVM. Compruebe que
   `https://<nombre>.containers.snapdeploy.app/api/v1/experiences` responde JSON.
3. Abra la PWA. Si la API volvió a dormirse, la PWA mostrará un 503 con la URL a abrir: repita el paso 1.

Las 100 horas/mes son **por cuenta** (≈ 4 días seguidos) y solo cuentan con el contenedor despierto. Un monitor o
*keep-alive* no puede despertarlo, pero una vez despierto mantenerlo activo consume horas: no los use. Cada
despertar arranca una copia nueva (la API no guarda estado local: todo está en Neon y B2).

#### 6.B Camino B · Su PC + Cloudflare Tunnel (plan B)

Todos los comandos, desde `infra/docker/`.

##### 6.B.1 Modo `quick` (sin dominio)

```bash
docker compose --env-file ../env/.env.demo -f compose.demo.yml --profile quick up -d --build
docker compose --env-file ../env/.env.demo -f compose.demo.yml logs -f api            # espere «Started IntegrateApplication»
docker compose --env-file ../env/.env.demo -f compose.demo.yml logs cloudflared-quick  # busque https://….trycloudflare.com
```

La primera construcción tarda unos minutos (compila el JAR en la imagen). Anote la URL
`https://<palabras>.trycloudflare.com`: es el `API_ORIGIN` del paso 8. Compruébela:

```bash
curl https://<palabras>.trycloudflare.com/api/v1/experiences
```

##### 6.B.2 Modo `named` (con dominio)

1. Añada su dominio a Cloudflare (*Add a site*) y cambie los *nameservers* en su registrador.
2. En el panel **Zero Trust → Networks → Tunnels → Create a tunnel → Cloudflared**, nómbrelo y copie
   el **token**. (Zero Trust puede pedir datos de facturación aun en el plan gratuito: verifíquelo al
   registrarse.)
3. En *Public Hostnames* agregue: hostname `api.sudominio.com`, servicio **HTTP** `api:8080`.
4. Ponga el token en `TUNNEL_TOKEN` del `.env.demo` y arranque:

   ```bash
   docker compose --env-file ../env/.env.demo -f compose.demo.yml --profile named up -d --build
   ```

5. `API_ORIGIN` será `https://api.sudominio.com` y no cambia al reiniciar.

##### Operación diaria (camino B)

| Tarea | Comando (desde `infra/docker/`, con `--env-file ../env/.env.demo -f compose.demo.yml`) |
| :--- | :--- |
| Ver estado | `ps` |
| Logs de la API | `logs -f api` |
| Detener | `--profile quick down` (o `named`) |
| Reiniciar tras editar `.env.demo` | `--profile quick up -d` (recrea los contenedores; en `quick` la URL **cambia**) |
| Actualizar la API tras un `git pull` | `--profile quick up -d --build` |

Para que Docker arranque con el PC, active *Start Docker Desktop when you sign in* (Settings →
General). Evite que el PC se suspenda mientras haya demo (Windows: `powercfg /change standby-timeout-ac 0`).

### 7. Política de privacidad de la demo

Con el backend ya iniciado (Flyway creó las tablas), publique la política de demostración. Sin ella
`GET /api/v1/privacy-policies/current` responde 404 y nadie puede registrarse (la política de ejemplo
solo viene con el perfil `dev`). Es idempotente:

```bash
psql "postgresql://integrate_owner:<contraseña-owner>@ep-xxx.<region>.aws.neon.tech/integrate?sslmode=require" \
  -f infra/postgres/seed/demo_privacy_policy.sql
```

### 8. Cloudflare Pages (PWA)

1. *Workers & Pages → Create → Pages → Connect to Git*: repositorio de Intégrate, rama `main`, con el
   **nombre de proyecto** que eligió en el paso 1.
2. Configuración de build:
   - **Framework preset:** None · **Root directory:** `frontend-pwa`
   - **Build command:** `npm ci && npm run build` · **Build output directory:** `dist`
3. Variables (*Settings → Variables and Secrets*, entorno **Production**):

   | Variable | Valor | Uso |
   | :--- | :--- | :--- |
   | `NODE_VERSION` | `22.12.0` | Versión de Node del build (`engines` de `package.json`). |
   | `VITE_API_BASE_URL` | `/api` | Se incrusta en el bundle: la PWA llama a su propio origen. |
   | `API_ORIGIN` | URL pública del backend (SnapDeploy, paso 6.A; o el túnel, paso 6.B), **sin barra final** | Destino del proxy `functions/api/[[path]].ts`. |

4. Despliegue. Como `functions/` está en el directorio raíz, Pages monta el proxy en `/api/*` sin
   configuración adicional.

**Cuando cambie la URL del túnel (modo `quick`):** edite `API_ORIGIN` en Pages y redespliegue
(*Deployments → … → Retry deployment*, o un push a `main`): las variables se aplican en el siguiente
despliegue. Mientras tanto la PWA mostrará el 502 del proxy.

**Por qué el proxy:** la cookie de refresh es `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`
(ADR-0005). Entre `*.pages.dev` y otro dominio el navegador no la enviaría y la sesión se perdería al
recargar. Con el proxy la PWA y la API comparten origen, igual que con el proxy de Vite en desarrollo.

### 9. Verificación

1. `https://<proyecto>.pages.dev` carga el catálogo.
2. `https://<proyecto>.pages.dev/api/v1/privacy-policies/current` responde JSON (si da 404, falta el paso 7).
3. Inicie sesión con el ADMIN, **recargue la página** y compruebe que la sesión sigue activa (cookie de refresh).
4. Haga una reserva con `MOCK`, confirme el pago y descargue el voucher (escritura y lectura en B2).
5. Pida restablecer contraseña y revise que el correo llegue con un enlace a `pages.dev`.
6. Tras crear el primer ADMIN, borre `BOOTSTRAP_ADMIN_*` del `.env.demo` y recree la API
   (`up -d`; en `quick` la URL cambiará: actualice `API_ORIGIN`).

### 10. Operación y seguridad

| Tarea | Cómo |
| :--- | :--- |
| Desplegar cambios del frontend | Push o merge a `main`: Pages reconstruye solo. |
| Desplegar cambios del backend | Camino A: redespliegue desde el panel de SnapDeploy (cuenta para las 10 por día). Camino B: `git pull` y `up -d --build`. |
| Nueva migración | Se aplica al arrancar el backend (Flyway, rol `integrate_owner`). |
| Rotar la llave de vouchers | Nueva `VOUCHER_SIGNING_KEY` y nuevo `VOUCHER_SIGNING_KEY_ID`; la anterior queda `RETIRED`. |
| Rotar un secreto filtrado | Revocar en el proveedor, actualizar `.env.demo`, recrear la API ([SOP-SEC-01](../security/security_secrets_policy.md)). El `TUNNEL_TOKEN` se revoca desde Zero Trust. |

El PC aloja el backend y las llaves: use cifrado de disco (BitLocker), bloquee la sesión y mantenga el
`.env.demo` fuera del repositorio. La API solo escucha en `127.0.0.1:8081` (cambie el puerto con `DEMO_API_PORT` si está ocupado); el único acceso desde fuera es el túnel.

### 11. Limitaciones conocidas

- **SnapDeploy (camino A):** 0,25 vCPU, así que el arranque de la JVM + Flyway es lento; tras 15 min sin tráfico se duerme, solo despierta si una persona abre su URL en un navegador y la primera petición después puede tardar más de dos minutos (la PWA verá un 503 que indica abrir la URL de la API a mano). Las tareas programadas (expiración de bloqueos, outbox de correo, barrido de vouchers) solo corren con el contenedor despierto, y las 100 h/mes se agotan en ≈ 4 días de uso continuo.
- **Disponibilidad (camino B):** igual a la del PC (apagado, suspensión, sin internet o Docker cerrado → 502). Para una
  sustentación, arranque todo unos minutos antes y verifique el paso 9.
- **URL efímera en `quick`:** cada reinicio del contenedor del túnel cambia la URL y exige el paso 8.
- **Tareas programadas:** la expiración de bloqueos (TTL 15 min), el outbox de correo y el barrido de
  vouchers corren mientras el backend esté activo; si el PC se apaga, se ponen al día al volver.
- **Límite de tasa:** SnapDeploy corta a 100 peticiones/min por IP; el proxy de Pages concentra a todos los usuarios en pocas IPs de Cloudflare. Apto para una demo con pocos usuarios, no para carga.
- **Neon** suspende la base a los ~5 minutos sin uso; la primera consulta tarda un par de segundos.
- **IP del cliente:** el backend registra la IP con `getRemoteAddr()` (ADR-0005 §4), que detrás de
  `cloudflared` es la del túnel, no la del usuario. El proxy envía `X-Forwarded-For`, pero el backend no
  lo lee: activar `server.forward-headers-strategy` exige una decisión sobre proxies de confianza que
  queda fuera de esta demo.
- **Mapas de fuente:** `vite build` genera *sourcemaps* (`build.sourcemap: true`) y Pages los publica.
- **Cuota de Pages Functions:** ~100 000 invocaciones/día en el plan gratuito.

### 12. Problemas frecuentes

| Síntoma | Causa probable y solución |
| :--- | :--- |
| SnapDeploy: la pantalla de variables detectadas no lista `DATABASE_URL` y el arranque falla con `Could not resolve placeholder 'DATABASE_URL'` | SnapDeploy trata `DATABASE_URL` como variable de add-on y no la ofrece. Con una base externa (Neon) hay que **añadirla a mano** (`+ Add Variable`, Key `DATABASE_URL`, Value la URL JDBC del `.env.demo`) o cargarla con `snapdeploy.sh env`. No compre el add-on. |
| La API en SnapDeploy responde 401 a todo y `/login` muestra «Please sign in» | Está desplegada una rama sin la seguridad de Intégrate (p. ej. `main`, que solo tiene la Fase 0). Cambie la rama de despliegue del contenedor y redespliegue. |
| `/api/*` responde 502 «API_ORIGIN no está configurada» | Falta `API_ORIGIN` en Pages o no empieza por `https://`. Redespliegue tras añadirla. |
| `/api/*` responde 502 de Cloudflare de forma intermitente y el log del contenedor se corta sin error | Falta de memoria: la plataforma mata la JVM. Compruebe que `JAVA_TOOL_OPTIONS` sea el de la imagen (sección «Memoria») y que no haya sobrescrito `MaxRAMPercentage`. Tras corregir, `snapdeploy.sh env`. |
| `/api/*` responde 429 | SnapDeploy limita a **100 peticiones por minuto por IP** al mismo contenedor y bloquea 10 min. Todo el tráfico que pasa por el proxy de Pages sale por IPs de Cloudflare compartidas: no haga pruebas de carga contra la demo. |
| `/api/*` responde 503 «El backend está dormido» | Camino A: ábralo en el navegador con la URL que indica el mensaje, espere ~1 minuto más el arranque de la JVM y reintente. No lo "mantenga caliente" con peticiones periódicas: no lo despiertan y consumen horas una vez despierto. |
| El contenedor de SnapDeploy no despierta o dice que se agotaron las horas | Se consumieron las 100 h del mes: espere al siguiente ciclo o use el camino B. |
| `/api/*` responde 502 «El backend no respondió» | PC apagado o Docker cerrado; o (modo `quick`) la URL cambió y `API_ORIGIN` quedó vieja. Revise `ps`, `logs cloudflared-quick` y el paso 8. |
| El túnel `quick` no muestra URL | El contenedor espera a que la API pase el healthcheck (hasta ~90 s); revise `logs -f api`. |
| `docker compose` dice «env file … not found» | Falta `infra/env/.env.demo` o se ejecutó fuera de `infra/docker/`. |
| La PWA muestra «VITE_API_BASE_URL no está configurada» | Falta la variable de build en Pages: se lee al compilar, no en tiempo de ejecución. |
| La API aborta con `… is required` o `Could not resolve placeholder` | Falta una variable de la tabla del paso 5. |
| `FATAL: password authentication failed` | La contraseña del `.env.demo` no coincide con la del bootstrap, o se usó el usuario de Neon. |
| Flyway falla con `permission denied` | El bootstrap no se ejecutó sobre la base `integrate` o se conectó con otro rol. |
| Errores `prepared statement … does not exist` o timeouts raros | `DATABASE_URL` apunta al host `-pooler`: use el endpoint directo. |
| La API se reinicia con `OOMKilled` | Suba `mem_limit` en `compose.demo.yml` o baje `DB_POOL_MAX_SIZE`. |
| La sesión se pierde al recargar | La PWA se construyó con una `VITE_API_BASE_URL` absoluta: debe ser `/api`. |
| El voucher no se descarga (`AccessDenied` / `NoSuchBucket`) | Llave de B2 sin acceso al bucket, o región/endpoint distintos a los del bucket. |
| `/privacy-policies/current` da 404 | Falta el paso 7. |

## 4. Desplegar la PWA en Cloudflare Pages

Notas para no repetir errores al publicar `integrate-demo.pages.dev`. No contiene secretos: los tokens viven en `infra/env/.env.cloudflare` (ignorado por Git).

### 1. Compilar para producción

Vite en modo producción **no lee** `.env.development.local`. La variable `VITE_API_BASE_URL` debe pasarse en el propio comando:

```bash
cd frontend-pwa
MSYS_NO_PATHCONV=1 VITE_API_BASE_URL=/api npm run build
```

- `MSYS_NO_PATHCONV=1` es obligatorio en **Git Bash (Windows)**. Sin él, `/api` se convierte en `C:/Program Files/Git/api` y la app muestra «VITE_API_BASE_URL debe ser una ruta…».
- Sin la variable, la app muestra «Configuración incompleta» en producción. Esto ya pasó: el sitio quedó roto tras un redespliegue.
- **Mapa con Google (opcional):** si hay una llave de *Maps Embed API*, se pasa en el mismo comando: `MSYS_NO_PATHCONV=1 VITE_API_BASE_URL=/api VITE_GOOGLE_MAPS_EMBED_KEY=<llave> npm run build`. La llave queda visible en el bundle, así que debe restringirse en Google Cloud por referencia HTTP (`https://integrate-demo.pages.dev/*`) y solo a esa API. Si la API no está activada en su proyecto, el mapa sale con error: antes de compilar con ella, pruebe `https://www.google.com/maps/embed/v1/place?key=<llave>&q=4.53,-75.68` (debe responder 200). Sin la variable, el mapa usa OpenStreetMap.
- Antes de subir, confirma que el bundle no tiene la ruta de Git:

```bash
grep -c "Program Files" dist/assets/index-*.js   # debe dar 0
```

### 2. Publicar

```bash
export CLOUDFLARE_API_TOKEN=... CLOUDFLARE_ACCOUNT_ID=...   # desde infra/env/.env.cloudflare
WRANGLER_SEND_METRICS=false npx --yes wrangler pages deploy dist --project-name integrate-demo --branch main --commit-dirty=true
```

`--branch main` publica en producción. Cualquier otra rama genera una vista previa.

### 3. Proxy `/api` hacia el backend

La Pages Function `frontend-pwa/functions/api/[[path]].ts` reenvía `/api/*` al servidor. Su destino está en el secreto `API_ORIGIN` del proyecto:

```bash
printf '%s' 'https://2-31-111-240.sslip.io' | npx --yes wrangler pages secret put API_ORIGIN --project-name integrate-demo
```

- Cambiar el secreto **no** requiere recompilar; sí requiere un nuevo despliegue para que la función lo tome.
- Si el backend cambia de servidor (IP o dominio), actualiza el secreto y prueba `https://integrate-demo.pages.dev/api/v1/privacy-policies/current`.

### 4. Verificar después de publicar

1. Abrir `https://integrate-demo.pages.dev/` en una ventana de incógnito.
2. La pantalla debe mostrar **Experiencias comunitarias** con la tarjeta del catálogo, sin «Configuración incompleta».
3. En las herramientas de red, `catalog/departments`, `catalog/categories` y `experiences` deben responder **200** desde `integrate-demo.pages.dev/api`. Un 502 suele ser un backend dormido o caído; revisa `docker compose ps` en el servidor.
4. Abrir `https://integrate-demo.pages.dev/manual/` y comprobar que carga.

### 5. Desarrollo local

- `npm run dev` lee `frontend-pwa/.env.development.local` (copia de `.env.example`). `VITE_API_BASE_URL=/api` con `VITE_DEV_API_PROXY_TARGET=http://localhost:8080`.
- Si el servidor de desarrollo arrancó **antes** de crear ese archivo, reinícialo.
- Si el puerto 5173 está ocupado, Vite no lo cambia y el navegador integrado puede fallar. Cierra el proceso que lo usa.

### Errores vistos y su causa

| Mensaje o síntoma | Causa | Solución |
| :--- | :--- | :--- |
| «Configuración incompleta» en producción | Se compiló sin `VITE_API_BASE_URL` | Paso 1 |
| «se recibió "C:/Program Files/Git/api"» | Git Bash convirtió `/api` | `MSYS_NO_PATHCONV=1` |
| 502 en `/api/*` | Backend dormido o caído | Revisar el servidor, paso 4 |
| 404 en `/api/*` con HTML | Falta la función o el build es viejo | Redesplegar con `dist` actual |

## 5. Usuarios y datos de prueba

Cómo se inicializa la base de la demo desplegada (Neon + SnapDeploy + Cloudflare Pages) y qué cuentas y datos quedan para probar **todos los roles** de la aplicación. Este documento no contiene contraseñas ni datos personales: las credenciales viven solo en archivos locales ignorados por Git (SEC-DIR-004).

### 1. Reiniciar la base desde cero

1. **Borrar los esquemas** de la aplicación en Neon (los roles y las extensiones no se tocan):
   `DROP SCHEMA IF EXISTS notification, audit, offline, voucher, payments, booking, catalog, auth, common CASCADE`.
2. **Reiniciar el backend** (`bash infra/scripts/snapdeploy.sh env` y luego `wake`). Al arrancar, Flyway aplica las 14 migraciones (V1–V14) y recrea las extensiones.
3. **Cargar la política de privacidad vigente** (sin ella nadie puede registrarse): `infra/postgres/seed/demo_privacy_policy.sql` (versión `v0.1`, idempotente). Se carga con `psql` leyendo el archivo en binario.
4. **Crear el ADMIN inicial** con las variables `BOOTSTRAP_ADMIN_*` de `infra/env/.env.demo`: se registra por la API y se le concede el rol por SQL. La app no tiene pantalla para otorgar ADMIN.

### 2. Cuentas de prueba (testers) · todos los roles

Se crean con `python infra/scripts/crear-testers.py`. Las contraseñas se generan al azar y se escriben solo en `infra/env/.env.testers.txt`, que no se sube a Git. Los correos `@integrate-demo.test` son ficticios: no reciben mensajes, así que no sirven para recuperar la contraseña. Si un tester la olvida, el equipo la restablece. La contraseña de `tester2` fue cambiada durante la prueba de recuperación: la vigente está en `.env.testers.txt`.

| Rol | Cuentas | Qué permite probar |
| :--- | :--- | :--- |
| **TOURIST** (turista) | `tester1` a `tester5@integrate-demo.test` | Catálogo, reserva de una franja, pago en modo MOCK con «Simular pago aprobado», «Mis experiencias», voucher en PDF y cancelación. |
| **HOST** (anfitrión) | `tester.anfitrion@integrate-demo.test` | Crear experiencias, enviarlas a revisión, gestionar franjas y cupos, ver sus reservas. |
| **LINK_MANAGER** (gestor de enlace) | `tester.gestor@integrate-demo.test` | Gestionar la asociación y a sus anfitriones; ver la dispersión del 85 %. |
| **ADMIN** (administrador) | `tester.admin@integrate-demo.test` | Aprobar experiencias, crear asociaciones, asignar roles, auditoría y reportes. Acceso total. |

El anfitrión y el gestor de prueba pertenecen a la asociación «Asociación de Prueba Integrate» (Armenia, 63001). Los roles nuevos aparecen tras **cerrar sesión y volver a entrar**.

### 3. Usuarios reales y datos de ejemplo

Las personas reales se capturan con el formulario `infra/forms/formulario-usuarios-reales.html` (no envía nada: descarga un `usuarios-reales.json`) y se cargan con `python infra/scripts/crear-usuarios.py --file <archivo> --apply --experiencia`:

- Crea las asociaciones, registra a cada persona con una **contraseña temporal aleatoria que nunca se guarda ni se imprime** y le asigna su rol (gestor de enlace, anfitrión).
- Envía a cada persona un correo de «crear contraseña» (Brevo). Cada una elige su propia clave.
- Con `--experiencia` crea, a nombre de cada anfitrión nuevo, la experiencia de ejemplo «Caminata ecológica y cata de café» (baja intensidad, 180 min, 8 cupos, 60 000 COP), la envía a revisión, la aprueba el ADMIN y abre 3 franjas en los próximos días.
- El informe `*.resultado.json` queda junto al archivo de entrada, sin contraseñas.

El rol **ADMIN** de una persona real solo se concede cuando su correo es permanente; no se otorga a correos desechables.

### 3b. Cuentas reales creadas (estado al 2026-10-27)

Ninguna contraseña de estas cuentas está guardada en el proyecto: la clave temporal se genera al azar y solo sirve para crear la cuenta. Para entrar, cada persona pide un enlace nuevo desde **«¿Olvidaste tu contraseña?»** en `/ingresar`. El enlace vale 15 minutos y de un solo uso (ADR-0014); los enlaces anteriores ya vencieron.

| Rol | Estado |
| :--- | :--- |
| Gestor de enlace | Cuenta creada. Debe pedir un enlace nuevo para entrar. |
| Anfitrión | Cuenta creada. Debe pedir un enlace nuevo para entrar. Tiene aprobada la experiencia «Caminata ecológica y cata de café» con 3 franjas. |
| Administrador (persona real) | Pendiente: el correo entregado era desechable; se creará cuando envíe uno permanente. |

Nombres y correos de estas personas no se publican en el repositorio (es público): están solo en los archivos locales indicados abajo.

Los datos completos de cada persona están en `infra/env/.env.usuarios-reales.json` (y `…parte1.json`), ignorados por Git.

### 4. Flujo completo para probar

1. **Turista (`tester1`):** elige «Caminata ecológica y cata de café», una franja y pulsa «Reservar». Llena los datos (documento, EPS y contacto de emergencia), acepta el consentimiento de datos de salud y pulsa «Apartar cupos».
2. **Pago:** en «Paga tu reserva» pulsa «Pagar» y luego «Simular pago aprobado» (panel «Pasarela de pruebas», ADR-0029). La reserva queda confirmada con su voucher, que se descarga en PDF/A y aparece en «Mis experiencias».
3. **Cupos:** los cupos apartados sin pagar se liberan a los 15 minutos (worker de expiración).
4. **Anfitrión y gestor:** el anfitrión ve la reserva; el gestor de enlace ve la dispersión del 85 % y su jornada.
5. **ADMIN:** revisa la auditoría y aprueba experiencias nuevas.
6. **Recuperación de contraseña (ADR-0014):** «¿Olvidaste tu contraseña?» envía un enlace de un solo uso al correo de la cuenta. Con los correos `@integrate-demo.test` no se puede completar la prueba: se necesita una cuenta con correo real.

### 5. Limpieza al terminar

Borrar de `infra/env/` los archivos locales con credenciales o datos personales (`.env.testers.txt`, `.env.usuarios-reales*.json`, `.env.snapdeploy`, `.env.cloudflare`) y revocar los tokens de SnapDeploy y Cloudflare.
