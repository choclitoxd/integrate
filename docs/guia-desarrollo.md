# Guía de desarrollo · Intégrate

Cómo levantar Intégrate en una máquina local, cargar datos de prueba, trabajar con recarga en caliente y
ejecutar las pruebas. Para el uso de la aplicación como usuario final, vea el
[manual de usuario](manual/README.md).

---

## 1. Requisitos

| Herramienta | Versión | Para qué |
| :--- | :--- | :--- |
| Docker + Docker Compose | reciente | PostgreSQL 16, LocalStack S3 y Mailpit |
| Java (JDK) | 21 | `backend-api` (el *wrapper* `mvnw` descarga Maven) |
| Node.js | ≥ 22.12 | `frontend-pwa` |
| OpenSSL | cualquiera | Generar llaves RSA y AES locales |

Puertos usados (todos ligados a `127.0.0.1`): `5432` PostgreSQL · `4566` LocalStack · `1025`/`8025` Mailpit ·
`8080` backend · `5173` PWA.

## 2. Variables de entorno

Intégrate versiona una sola plantilla: [`infra/env/.env.example`](../infra/env/.env.example). Cópiela y
complete los valores:

```bash
cp infra/env/.env.example infra/env/.env.development
```

| Variable | Cómo obtener el valor local |
| :--- | :--- |
| `POSTGRES_SUPERUSER_PASSWORD`, `INTEGRATE_*_PASSWORD` | Contraseñas fuertes inventadas por usted. |
| `DATABASE_PASSWORD` | El mismo valor que `INTEGRATE_API_PASSWORD`. |
| `FLYWAY_PASSWORD` | El mismo valor que `INTEGRATE_OWNER_PASSWORD`. |
| `JWT_SIGNING_KEY` | `openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \| base64 -w0` |
| `VOUCHER_SIGNING_KEY` | Igual que la anterior, pero **otra** llave distinta. |
| `SENSITIVE_DATA_KEY_V1` | `openssl rand -base64 32` — respáldela: si se pierde, los datos cifrados son irrecuperables. |
| `PAYMENT_WEBHOOK_SECRET_MOCK` | `openssl rand -base64 32` |
| `PAYMENT_PROVIDER` | `MOCK` en local (no se cobra dinero real). |
| `SMTP_*` | Déjelos apuntando a Mailpit; la plantilla trae ejemplos comentados para Gmail y Brevo. |

El perfil `dev` del backend importa automáticamente `infra/env/.env.development`, así que no hace falta
exportar las variables en la terminal.

> `infra/env/.env.development` y `infra/env/.env.test-users` están ignorados por Git. **Nunca** los comitee ni
> copie sus valores en código, documentación o issues ([SEC-DIR-004](security/security_secrets_policy.md)).

## 3. Infraestructura local

Desde `infra/docker/`:

```bash
docker compose --env-file ../env/.env.development --profile full up -d
docker compose --env-file ../env/.env.development ps
```

| Perfil | Servicios |
| :--- | :--- |
| *(ninguno)* | Solo PostgreSQL |
| `storage` | + LocalStack S3 (buckets de vouchers y de documentos sensibles) |
| `mail` | + Mailpit (UI en <http://localhost:8025>) |
| `full` | Todos |

El script `infra/postgres/init/00_bootstrap.sql` crea los roles `integrate_owner` (DDL/Flyway),
`integrate_api` (DML del backend) e `integrate_readonly`, **solo la primera vez** que el volumen está vacío.
Si cambia contraseñas, reinicie desde cero (borra los datos locales):

```bash
docker compose --env-file ../env/.env.development --profile full down -v
```

Más detalle: [infra/README.md](../infra/README.md).

## 4. Backend (`backend-api`)

```bash
cd backend-api
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev      # Windows: mvnw.cmd ...
```

- Al arrancar, Flyway aplica las migraciones de `src/main/resources/db/migration` y, en `dev`, la semilla
  mínima de `db/seed-dev`.
- La API queda en <http://localhost:8080/api/v1>. Los errores siguen el formato RFC 9457 e incluyen un
  `traceId` para buscar en los logs.
- **Recarga en caliente** ([ADR-0021](architecture/adr/ADR-0021-recarga-en-caliente-entorno-desarrollo.md)):
  con DevTools, recompilar (`./mvnw compile` en otra terminal o desde el IDE) reinicia el contexto en
  segundos. DevTools no se empaqueta en el JAR de producción.
- **Primer administrador:** si no carga la semilla, defina `BOOTSTRAP_ADMIN_EMAIL` y
  `BOOTSTRAP_ADMIN_PASSWORD` para el primer arranque y retírelas después.

## 5. PWA (`frontend-pwa`)

```bash
cd frontend-pwa
npm install
npm run dev
```

Vite sirve la PWA en <http://localhost:5173> con HMR y redirige `/api` al backend en el puerto 8080, por lo
que no hace falta configurar CORS en local. Si el backend corre en otro puerto, defina
`VITE_DEV_API_PROXY_TARGET` (por ejemplo `http://localhost:8081`).

### Probar desde un celular (HTTPS de desarrollo)

La cookie de sesión (`refresh_token`) es `Secure`: un navegador solo la guarda por HTTPS o por `localhost`.
Si abre la PWA desde el celular por `http://<IP-del-equipo>:5173`, el inicio de sesión funciona pero **se pierde
al recargar o reabrir** (la sesión no se restaura). Para probar en el celular con la sesión estable:

```bash
cd frontend-pwa
npm run dev:https
```

El script imprime la dirección para el celular (`https://<IP-del-equipo>:5173`). Use un certificado autofirmado:
la primera vez el navegador del celular avisa que no es de confianza; acéptelo para continuar. El celular y el
equipo deben estar en la misma red Wi-Fi y el firewall de Windows debe permitir el puerto 5173. El backend, la
cookie y los demás comandos no cambian; `npm run dev` sigue sirviendo por HTTP en `localhost`. Es solo para
desarrollo: producción usa HTTPS real.

Reglas obligatorias para cualquier cambio en la PWA: [FE-DIR-001](rules/frontend-rules.md) (mobile-first
360–430 px, tokens de color AAA de `src/styles/tokens.ts`, áreas táctiles ≥ 44 px, fronteras de capas).

## 6. Datos de prueba

[`infra/postgres/seed/dev_test_data.sql`](../infra/postgres/seed/dev_test_data.sql) **vacía los datos
transaccionales** y crea un usuario por rol, dos asociaciones, anfitriones, experiencias y franjas
horarias. Conserva los catálogos gobernados por migraciones (roles, políticas de privacidad, categorías,
DIVIPOLA, comisiones).

El archivo no contiene contraseñas: recibe el hash Argon2id como variable de psql. Todas las cuentas
comparten la contraseña de prueba guardada en `infra/env/.env.test-users` (ignorado por Git).

1. Obtenga el hash Argon2id de esa contraseña; por ejemplo, regístrese en la PWA con ella y copie el valor
   de `auth.users.password_hash` de ese usuario.
2. Cargue la semilla (bash):

   ```bash
   docker exec -i integrate-postgres psql -U postgres -d integrate \
     -v ON_ERROR_STOP=1 -v pwhash='<hash argon2id>' < infra/postgres/seed/dev_test_data.sql
   ```

   En PowerShell, sustituya la redirección `<` por
   `Get-Content infra/postgres/seed/dev_test_data.sql -Raw | docker exec -i integrate-postgres psql ...`.

Cuentas creadas: `turista1…3@ejemplo.com`, `gestor1@ejemplo.com`, `gestor2@ejemplo.com`,
`anfitrion1…3@ejemplo.com` y `admin@ejemplo.com`. Qué puede hacer cada una: [manual de usuario](manual/README.md).

## 7. Pruebas y Quality Gate

### Backend

```bash
cd backend-api
./mvnw verify               # pruebas unitarias e integración (Testcontainers/PostgreSQL)
./mvnw verify -Pquality     # suite trazable al plan de calidad, incluye pruebas de no conformidad
```

Las pruebas marcadas `nonconformity` verifican el criterio literal del plan sobre comportamientos que hoy
difieren; solo corren con `-Pquality` y sus fallos son esperados y documentados.

### PWA — Quality Gate (Directiva 7 de FE-DIR-001)

Ningún cambio en la PWA se da por terminado sin estos cinco comandos en verde:

```bash
npm run typecheck
npm run lint
npm run format:check
npm test
npm run build
```

Extras: `npm run test:coverage` (cobertura) y `npm run quality:bundle` (presupuesto de tamaño del bundle).

### Carga y evidencias

- Pruebas k6 de RNF-02, RNF-08 y RNF-10: [qa/k6/README.md](../qa/k6/README.md). Se ejecutan **solo** contra un
  entorno desechable, nunca contra la base de desarrollo.
- Informe de evidencias: `node qa/scripts/build-evidence-report.mjs` regenera
  [docs/quality/evidencias-metricas.md](quality/evidencias-metricas.md). No lo edite a mano.

## 8. Flujo de trabajo

| Tema | Regla |
| :--- | :--- |
| Cambios de backend | Primero un ADR en `docs/architecture/adr/`, aprobado antes de escribir código ([flujo ADR](rules/adr-workflow.md)). |
| Git | Ramas, Conventional Commits, momentos de push y PR según [GIT-WF-001](rules/git-workflow.md). |
| Secretos | Lista de verificación pre-commit / pre-push de la [política de secretos](security/security_secrets_policy.md). |
| Grafo de conocimiento | Tras modificar código: `graphify update .` (ver `CLAUDE.md`). |
| Despliegue de demo | Neon + backend en SnapDeploy (o su PC con Cloudflare Tunnel) + Cloudflare Pages + B2, paso a paso en la [guía de despliegue](despliegue/demo-gratuita.md) (ADR-0024 a ADR-0026). |

## 9. Problemas frecuentes

| Síntoma | Causa probable y solución |
| :--- | :--- |
| `docker compose` aborta con «Defina … en infra/env/.env.development» | Falta esa variable en `.env.development`. |
| El backend no conecta a PostgreSQL (`password authentication failed`) | `DATABASE_PASSWORD` ≠ `INTEGRATE_API_PASSWORD`, o cambió contraseñas sin borrar el volumen (`down -v`). |
| El backend no arranca y menciona `PAYMENT_PROVIDER` | La variable es obligatoria: use `MOCK` en local. |
| Error al firmar sesiones o vouchers | `JWT_SIGNING_KEY` / `VOUCHER_SIGNING_KEY` no es un PKCS#8 PEM en Base64 de una sola línea. |
| No llegan correos | Levante el perfil `mail` y revise <http://localhost:8025>. |
| El voucher PDF no se guarda | Levante el perfil `storage` (LocalStack). |
| La PWA responde 502 / `ECONNREFUSED` en `/api` | El backend no está corriendo en el puerto 8080. |
| No puedo iniciar sesión con las cuentas de prueba | La semilla no se ha cargado o se cargó con el hash de otra contraseña (sección 6). |

## Anexo: búsqueda de experiencias en local

### 1. Contexto y Qué es "Buscar un Servicio"
En **Intégrate**, un "servicio" es una **experiencia turística comunitaria** (talleres artesanales, senderismo de memoria, recorridos agroecológicos, etc.) prestada por anfitriones y asociaciones campesinas de Colombia.

La funcionalidad de **búsqueda y catálogo territorial** permite al turista:
* Explorar experiencias públicas en tarjetas móviles con tarifa en COP y aforo ecológico.
* Buscar por texto libre (palabras clave en título y descripción).
* Filtrar por ubicación geográfica oficial (**Departamento y Municipio DIVIPOLA** del DANE).
* Filtrar por **Categoría temática** (Senderismo, Agroturismo, Memoria histórica, etc.) y por **Intensidad física** (Baja, Media, Alta).
* Consultar la ficha detallada de la experiencia y la disponibilidad de fechas/franjas horarias con aforo en tiempo real.

El sistema se compone de:
1. **Infraestructura / Persistencia:** Contenedor Docker con **PostgreSQL 16** (extensiones `pg_trgm`, `btree_gist`, 9 esquemas y datos DIVIPOLA precargados).
2. **Backend API:** Monolito modular en **Java 21 con Spring Boot 4.1.1** (arquitectura hexagonal, seguridad stateless y migraciones Flyway).
3. **Frontend PWA:** Aplicación Web Progresiva construida con **React 19, TypeScript, Tailwind CSS 4 y Vite** (diseño Mobile-First optimizado para 360–430 px).

---

### 2. Prerrequisitos de Software en la Máquina
Antes de comenzar, la máquina debe tener instalado:
* **Git**: Para clonar y gestionar el repositorio.
* **Docker y Docker Compose**: Para la base de datos (Docker Desktop en Windows/macOS o Docker Engine en Linux).
* **Java Development Kit (JDK) 21**: Verificable con `java -version`.
* **Node.js (versión 22.12 o superior)** y **npm**: Verificable con `node -v`.
* **OpenSSL** o terminal compatible (Bash / WSL / PowerShell) para generar los secretos criptográficos locales.

---

### 3. Paso a Paso de Configuración y Despliegue

#### Paso 1: Configurar las variables de entorno de infraestructura y Backend
El proyecto aplica una política estricta de seguridad sin contraseñas predeterminadas (*fail-fast*).

1. En la raíz del repositorio, copie la plantilla de desarrollo:
   ```bash
   cp infra/env/.env.example infra/env/.env.development
   ```
2. Abra `infra/env/.env.development` y complete los valores requeridos. Para un entorno local rápido, genere las llaves y secretos con los siguientes comandos:

   * **Generar llaves RSA de 2048 bits para sesión y vouchers:**
     ```bash
     # En Linux/macOS/Git Bash:
     openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 | base64 -w0

     # En Windows PowerShell:
     $key = openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048
     [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes(($key -join "`n")))
     ```
     *(Copie el resultado en `JWT_SIGNING_KEY` y genere otro para `VOUCHER_SIGNING_KEY`)*.

   * **Generar llaves AES de 32 bytes en Base64:**
     ```bash
     openssl rand -base64 32
     ```
     *(Use una para `SENSITIVE_DATA_KEY_V1` y otra para `PAYMENT_WEBHOOK_SECRET_MOCK`)*.

   * **Definir contraseñas de base de datos:**
     Asegúrese de definir contraseñas seguras para:
     * `POSTGRES_SUPERUSER_PASSWORD=MiSuperPassword123!`
     * `INTEGRATE_OWNER_PASSWORD=MiOwnerPassword123!`
     * `INTEGRATE_API_PASSWORD=MiApiPassword123!`
     * `INTEGRATE_REPORTING_PASSWORD=MiReportingPassword123!`
     * `DATABASE_PASSWORD` debe ser idéntico a `INTEGRATE_API_PASSWORD`.
     * `FLYWAY_PASSWORD` debe ser idéntico a `INTEGRATE_OWNER_PASSWORD`.

   * **Configuraciones adicionales:**
     * `PAYMENT_PROVIDER=MOCK`
     * `FRONTEND_BASE_URL=http://localhost:5173`
     * `SERVER_PORT=8080` (o `8081` si el 8080 está ocupado en su máquina).

---

#### Paso 2: Iniciar la Base de Datos con Docker
Desde la carpeta `infra/docker/`:

```bash
cd infra/docker
docker compose --env-file ../env/.env.development up -d
```

* **Qué ocurre:** Se levanta el contenedor `integrate-postgres` en el puerto `5432`. En su primer arranque, ejecuta `00_bootstrap.sql` creando la base `integrate` y los roles `integrate_owner` y `integrate_api`.
* **Verificación:** Ejecute `docker compose --env-file ../env/.env.development ps` y confirme que el estado sea `healthy`.

---

#### Paso 3: Iniciar el Backend (Spring Boot)
Regrese a la raíz y diríjase al directorio `backend-api/`:

```bash
cd ../../backend-api

# En Linux / macOS / WSL:
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# En Windows (CMD o PowerShell):
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

* **Qué ocurre:**
  * Spring Boot carga las variables de `infra/env/.env.development`.
  * **Flyway ejecuta automáticamente las migraciones (`V1` a `V13`)**: crea los 9 esquemas de negocio, inicializa tablas y precarga los **33 departamentos**, los **1.122 municipios DIVIPOLA** de Colombia y las **categorías del catálogo**.
  * El servidor queda escuchando en `http://localhost:8080` (o el puerto configurado).
* **Verificación rápida:** Abra en su navegador o ejecute un curl:
  ```bash
  curl http://localhost:8080/api/v1/catalog/categories
  ```
  Debe responder con el listado JSON de categorías (`SENDERISMO_MEMORIA`, `AGROTURISMO`, `ARTESANIAS`, etc.).

---

#### Paso 4: Cargar Datos de Prueba para la Búsqueda (Seed)
Por diseño de integridad y moderación, una base de datos recién creada **no tiene experiencias cargadas**, y el catálogo público solo muestra experiencias con estado **`APPROVED`**.

Para poder probar el buscador de inmediato con datos reales, ejecute el siguiente bloque SQL directamente en el contenedor de PostgreSQL para insertar una asociación comunitaria, un anfitrión de prueba y dos experiencias aprobadas con franjas de disponibilidad:

```bash
docker exec -i integrate-postgres psql -U integrate_owner -d integrate << 'EOF'
-- 1. Crear usuario anfitrión de prueba
INSERT INTO auth.users (id, email, password_hash, status, is_superadmin)
VALUES ('11111111-1111-1111-1111-111111111111', 'anfitrion.demo@comunidad.integrate.co', '$argon2id$v=19$m=19456,t=2,p=1$fakeHashForDemoOnly$fakeHashForDemoOnly', 'ACTIVE', false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO auth.user_roles (user_id, role_code)
VALUES ('11111111-1111-1111-1111-111111111111', 'HOST')
ON CONFLICT DO NOTHING;

-- 2. Crear asociación comunitaria
INSERT INTO catalog.community_associations (id, legal_name, short_name, tax_id, rnt_number, department_code, municipality_code, contact_phone, contact_email, status)
VALUES ('22222222-2222-2222-2222-222222222222', 'Asociación Agroturística del Oriente', 'ASOAGRO', '901234567-1', 'RNT-99881', '05', '05001', '+573001112233', 'contacto@asoagro.org', 'ACTIVE')
ON CONFLICT DO NOTHING;

-- 3. Crear perfil de anfitrión
INSERT INTO catalog.host_profiles (id, user_id, association_id, full_name, document_type, document_number, phone, sensitive_key_version)
VALUES ('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 'María Gómez', 'CC', '1234567890', '+573001112233', 1)
ON CONFLICT DO NOTHING;

-- 4. Experiencia 1: Sendero Ecológico (Medellín, Antioquia)
INSERT INTO catalog.experiences (
    id, host_id, title, description, safety_guidelines, physical_intensity,
    duration_minutes, municipality_code, vereda, meeting_point, latitude, longitude,
    max_capacity_per_slot, unit_price_cop, timezone, status, created_by
) VALUES (
    '44444444-4444-4444-4444-444444444444',
    '33333333-3333-3333-3333-333333333333',
    'Sendero de Niebla y Tradición Campesina',
    'Recorrido guiado por bosques nativos y fincas campesinas tradicionales con avistamiento de aves.',
    'Llevar calzado con buen agarre, hidratación y capa impermeable.',
    'MEDIUM', 180, '05001', 'Santa Elena', 'Parque Central de Santa Elena frente a la iglesia', 6.208500, -75.498100,
    10, 85000, 'America/Bogota', 'APPROVED', '11111111-1111-1111-1111-111111111111'
) ON CONFLICT DO NOTHING;

-- 5. Experiencia 2: Taller de Café (Medellín, Antioquia)
INSERT INTO catalog.experiences (
    id, host_id, title, description, safety_guidelines, physical_intensity,
    duration_minutes, municipality_code, vereda, meeting_point, latitude, longitude,
    max_capacity_per_slot, unit_price_cop, timezone, status, created_by
) VALUES (
    '55555555-5555-5555-5555-555555555555',
    '33333333-3333-3333-3333-333333333333',
    'Taller Vivencial de Café de Origen',
    'Aprende el proceso de recolección, beneficio y catación artesanal de café con familias productoras.',
    'Ropa cómoda para trabajo en campo, sombrero y bloqueador solar.',
    'LOW', 120, '05001', 'San Antonio de Prado', 'Finca La Esperanza, vereda Potrerito', 6.185200, -75.652100,
    8, 60000, 'America/Bogota', 'APPROVED', '11111111-1111-1111-1111-111111111111'
) ON CONFLICT DO NOTHING;

-- Asociar categorías
INSERT INTO catalog.experience_categories (experience_id, category_id)
SELECT '44444444-4444-4444-4444-444444444444', id FROM catalog.categories WHERE code = 'SENDERISMO_MEMORIA'
ON CONFLICT DO NOTHING;

INSERT INTO catalog.experience_categories (experience_id, category_id)
SELECT '55555555-5555-5555-5555-555555555555', id FROM catalog.categories WHERE code = 'AGROTURISMO'
ON CONFLICT DO NOTHING;

-- 6. Franjas horarias futuras (con cupos)
INSERT INTO catalog.schedules (experience_id, start_time, end_time, total_slots, available_slots, status)
VALUES 
('44444444-4444-4444-4444-444444444444', now() + interval '2 days', now() + interval '2 days' + interval '3 hours', 10, 10, 'OPEN'),
('55555555-5555-5555-5555-555555555555', now() + interval '3 days', now() + interval '3 days' + interval '2 hours', 8, 8, 'OPEN')
ON CONFLICT DO NOTHING;
EOF
```

---

#### Paso 5: Configurar y Levantar el Frontend PWA
Diríjase a la carpeta `frontend-pwa/`:

```bash
cd ../frontend-pwa
```

1. **Configurar el entorno frontend:**
   ```bash
   cp .env.example .env.development.local
   ```
   *(Verifique que `VITE_API_BASE_URL=/api` y `VITE_DEV_API_PROXY_TARGET=http://localhost:8080`).*

2. **Instalar dependencias y arrancar:**
   ```bash
   npm install
   npm run dev
   ```

* **Qué ocurre:** Vite compilará la aplicación y la levantará en `http://localhost:5173`.
* El servidor de desarrollo incluye un proxy transparente: todas las peticiones que la PWA realiza a `/api/*` se reenvían al backend en `localhost:8080`, manteniendo las cookies seguras sin problemas de CORS.

---

### 4. Cómo Probar la Búsqueda en el Navegador

1. Abra su explorador web en **`http://localhost:5173`**.
2. **Recomendación de visualización (Mobile First):**
   * Presione `F12` para abrir las Herramientas de Desarrollo y active el modo emulación de dispositivos móviles (seleccione **iPhone 14 / Pixel 7**, o una resolución de entre **360 px y 430 px**).
   * En pantallas de escritorio la interfaz se mantendrá confinada en un contenedor móvil centrado para preservar la ergonomía táctil.
3. **Flujos de Búsqueda a Evaluar:**
   * **Búsqueda por texto libre:** En la barra de búsqueda superior, escriba *"Café"* o *"Sendero"* y presione Enter o el ícono de buscar. Se filtrarán en vivo los resultados.
   * **Filtros Avanzados (Bottom Sheet):** Haga clic en el botón **"Filtros"**:
     * Seleccione **Departamento** (ej. *Antioquia*) y **Municipio** (ej. *Medellín*).
     * Seleccione una o varias **Categorías** (ej. *Agroturismo*).
     * Elija un nivel de **Intensidad física** (*Baja*, *Media* o *Alta*).
     * Presione *"Aplicar filtros"*. La URL se actualizará automáticamente con los parámetros (`?departamento=05&municipio=05001...`).
   * **Chips de filtros activos:** Podrá remover filtros individuales haciendo clic en la "X" de cada chip o en *"Limpiar filtros"*.
   * **Detalle del Servicio:** Haga clic en una tarjeta de experiencia para ver su itinerario, tarifa por persona, punto de encuentro y recomendaciones.
   * **Disponibilidad:** Haga clic en *"Ver fechas y reservar"* para explorar el calendario semanal interactivo y los cupos disponibles por franja horaria.

---

### 5. Problemas Frecuentes y Soluciones (Troubleshooting)

| Síntoma / Error | Causa Probable | Solución |
| :--- | :--- | :--- |
| **El Backend falla al arrancar con `IllegalArgumentException` o error de propiedad faltante** | Falta alguna variable obligatoria en `infra/env/.env.development` (*fail-fast*). | Revise que `JWT_SIGNING_KEY`, `VOUCHER_SIGNING_KEY`, `SENSITIVE_DATA_KEY_V1` y `PAYMENT_WEBHOOK_SECRET_MOCK` no tengan placeholders vacíos. |
| **`Web server failed to start. Port 8080 was already in use`** | Hay otra aplicación usando el puerto 8080 (e.g., Steam, IIS, Jenkins). | En `infra/env/.env.development` cambie `SERVER_PORT=8081`. En `frontend-pwa/.env.development.local` cambie `VITE_DEV_API_PROXY_TARGET=http://localhost:8081` y reinicie ambos servicios. |
| **El contenedor de PostgreSQL se detiene o rechaza la contraseña** | La carpeta de volumen de Docker se creó antes con otras contraseñas. | Ejecute `docker compose --env-file ../env/.env.development down -v` para limpiar el volumen de datos e inicie nuevamente con `up -d`. |
| **El catálogo muestra "No encontramos experiencias con esos filtros"** | La base de datos es nueva y no se han aprobado experiencias. | Ejecute el script SQL del **Paso 4** para sembrar las experiencias de prueba con estado `APPROVED`. |
| **Error en `npm install` en el frontend** | Versión antigua de Node.js instalada en el sistema. | Intégrate requiere Node.js `>= 22.12`. Actualice Node con `nvm use 22` o descargue la versión LTS reciente. |
