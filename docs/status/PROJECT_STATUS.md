# Estado del Proyecto Intégrate (PROJECT_STATUS.md)

Última actualización: 2026-10-05  
Rol: Tech Lead, Arquitecto de Software y PM Técnico  
Ubicación: `docs/status/PROJECT_STATUS.md`  
Fuente de verdad operativa del proyecto.

---

## 1. Estado y Arquitectura Detectada

### 1.1 Diagnóstico de Artefactos Existentes
- **Documentación de Especificación y Requerimientos:**
  - `docs/especificacion_tecnica.md`: 20 Requerimientos Funcionales (RF-01 a RF-20 / CU-01 a CU-20) y No Funcionales (RNF-01 a RNF-20). Modelo de roles RBAC (Turista, Anfitrión, Gestor de Enlace, Administrador), políticas de Habeas Data (Ley 1581 / Ley 1448), split payment (85% comunidad / 15% plataforma) y política de cancelación (72h).
  - `docs/modelo_relacional.md`: Diseño de base de datos relacional PostgreSQL 16 con separación física en 8 esquemas de negocio (`auth`, `catalog`, `booking`, `payments`, `voucher`, `offline`, `audit`, `notification`), llaves compuestas, funciones trigger y políticas RLS.
  - `docs/roadmap_ejecucion.md`: Cronograma de 5 fases estructuradas por orden de dependencia técnica.
  - `docs/architecture/backend_guidelines.md`: Estructura monolítica modular bajo Arquitectura Hexagonal / Clean Architecture (`com.integrate.*`), reglas de aislamiento de dependencias entre capas, convención de desacoplamiento escalar de entidades JPA.
  - `docs/architecture/offline_data_indexeddb.md`: Especificación de persistencia offline cliente con IndexedDB (`IntegrateOfflineDB`) y sincronización diferida.
  - `docs/api/openapi.yaml` y `docs/api/dtos_contrato_api.md`: Contratos OpenAPI 3.0 consolidados y estructura de DTOs Java.
  - `docs/rules/adr-workflow.md`: Protocolo obligatorio de registro de decisiones de arquitectura (ADR).
  - `docs/rules/git-workflow.md`: Protocolo obligatorio de flujo de trabajo en Git, ciclo de vida de estados, momentos de push y directrices de PR (`GIT-WF-001`).
  - `docs/security/security_secrets_policy.md`: Gobernanza de secretos y restricciones de seguridad para repositorios y flujos CI/CD.

- **Infraestructura y Persistencia Actual:**
  - `infra/postgres/init/00_bootstrap.sql`: Script de aprovisionamiento de clúster PostgreSQL 16 (roles `integrate_owner`, `integrate_app`, `integrate_readonly`, base de datos `integrate`, timeouts de sesión y bloqueo pesimista).
  - `backend-api/src/main/resources/db/migration/`: Migraciones Flyway existentes de `V1` a `V10` cubriendo extensiones, schemas, tablas, permisos y alineación con contrato OpenAPI.

- **Brechas Identificadas (Lo que falta por construir):**
  - ~~**Infraestructura:** Falta `docker-compose.yml` y plantillas `.env`~~ → resuelto con ADR-0001 (2026-10-05).
  - **Backend:** ~~`pom.xml` y Maven Wrapper~~ → resuelto con ADR-0002 (2026-10-05). ~~Clase principal y configuración DataSource/Flyway/HikariCP~~ → resuelto en T-0.4. Faltan los paquetes de arquitectura hexagonal (T-0.6).
  - **Frontend:** ~~No existe el directorio `frontend-pwa/`~~ → boilerplate resuelto con ADR-0004 (T-0.7). Faltan IndexedDB / Service Workers (T-2.1).

---

## 2. Roadmap por Módulos (Backend y Frontend)

### Módulos Backend (Monolito Modular Hexagonal - Java 21 / Spring Boot 4.1)
1. **Infraestructura & Core Foundation:** Docker Compose, Bootstrap BD, Maven Multi-layer, Configuración JPA/Flyway, Manejador Global de Excepciones y Criptografía (AES-256 / Firmas asimétricas).
2. **Módulo Auth & Security (`auth`):** Entidades de usuario, credenciales, tokens de refresh, control de intentos, autenticación JWT stateless y filtros RBAC.
3. **Módulo Catálogo Territorial (`catalog`):** Experiencias turísticas comunitarias, rutas, anfitriones, franjas horarias y control de cupos ecológicos máximos.
4. **Módulo Reservas & Motor Concurrente (`booking`):** Bloqueo pesimista (`SELECT FOR UPDATE`), retención de cupos con TTL (15 min) y worker asíncrono de expiración.
5. **Módulo Pagos & Split Payment (`payments`):** Adaptador de pasarela de pago, simulación/webhook, cálculo de dispersión 85/15 y custodia temporal (*escrow*).
6. **Módulo Voucher Criptográfico (`voucher`):** Emisión de JWT firmado con RSA/Ed25519, empaquetado en código QR de alta densidad y generación de PDF.
7. **Módulo Offline Server & Sincronización (`offline`):** Endpoints de descarga preventiva de padrón, lista de revocación y recepción idempotente de sincronización en segundo plano.
8. **Módulo Auditoría & Notificaciones (`audit`, `notification`):** Auditoría inmutable de accesos sensibles (Habeas Data) y despachos transaccionales por email.

### Módulos Frontend PWA (Offline-First - React / Vite / TypeScript / Tailwind)
1. **Boilerplate & Core Layout:** Configuración de Vite, TypeScript, Tailwind CSS, React Router, Layout Responsive y Design System comunitario.
2. **Almacenamiento Local IndexedDB & Service Worker:** Configuración de `IntegrateOfflineDB`, Workbox y estrategias de caché estático/dinámico.
3. **Portal Turista (Online):** Catálogo de experiencias, selector de franjas, formulario de reserva con datos médicos/contacto y flujo de checkout/pago.
4. **Billetera de Vouchers (Turista):** Visualización de voucher offline, renderizado de código QR firmado y descarga de PDF.
5. **Portal Gestor de Enlace (Offline-First):** Descarga preventiva de padrón de la jornada, escáner de QR por cámara web/móvil, validador criptográfico offline y marcación anti-doble canje.
6. **Sincronización en Segundo Plano:** Motor de sincronización automática al detectar reconexión a Internet y visor de auditoría/conflictos.

---

## 3. Backlog Detallado de Tareas Técnicas

### Fase 0: Entorno Base, Infraestructura y Persistencia
- [x] `T-0.1`: Crear `infra/docker/docker-compose.yml` y plantillas de variables de entorno `infra/env/.env.example` y `infra/env/.env.development` (local, ignorado por Git) para levantar PostgreSQL 16 con el script `00_bootstrap.sql` (ADR-0001).
- [x] `T-0.2`: Validar ejecución del contenedor PostgreSQL, creación de roles (`integrate_owner`, `integrate_api`) y base de datos `integrate`.
- [x] `T-0.3`: Crear archivo `backend-api/pom.xml` con dependencias requeridas (Spring Boot 4.1.1, Java 21, Spring Data JPA, Flyway, PostgreSQL Driver, Validation, Lombok, Nimbus JOSE / JWT, Bouncy Castle) y Maven Wrapper (ADR-0002).
- [x] `T-0.4`: Configurar clase de entrada `IntegrateApplication.java` y archivos de configuración de propiedades `application.yml` y `application-dev.yml` en `backend-api/src/main/resources/`.
- [x] `T-0.5`: Ejecutar y verificar migración Flyway completa (`V1` a `V10`) contra la base de datos PostgreSQL local.
- [x] `T-0.6`: Configurar estructura base común (`common/exception`, `common/payload`, `config/`) en `backend-api`.
- [x] `T-0.7`: Inicializar proyecto `frontend-pwa` con Vite + React + TypeScript + Tailwind CSS y estructura base de directorios (Cierre de Fase 0).

### Fase 1: MVP Core Transaccional (Backend & Frontend)
- [x] `T-1.1`: [Backend] Implementar entidades JPA, repositorios, DTOs y servicios de autenticación y RBAC (`modules/auth/`) (ADR-0005).
- [x] `T-1.2`: [Backend] Implementar endpoints de autenticación (`/api/v1/auth/login`, `register`, `refresh`, `logout`, `/api/v1/me/consents`) con JWT y Spring Security (ADR-0005).
- [ ] `T-1.3`: [Backend] Implementar módulo de catálogo (`modules/catalog/`) para visualización y administración de experiencias y franjas horarias.
- [ ] `T-1.4`: [Backend] Implementar motor de reserva y concurrencia con bloqueo pesimista `SELECT ... FOR UPDATE` (`modules/booking/`).
- [ ] `T-1.5`: [Backend] Implementar worker asíncrono `@Scheduled` para liberación de reservas vencidas (TTL 15 min).
- [ ] `T-1.6`: [Backend] Implementar adaptador de pagos desacoplado con split payment (85% anfitrión / 15% plataforma) y webhook de confirmación (`modules/payment/`).
- [ ] `T-1.7`: [Backend] Implementar generador criptográfico de vouchers con firma asimétrica y renderizado de código QR/PDF (`modules/voucher/`).
- [ ] `T-1.8`: [Frontend] Implementar catálogo y flujo de reserva en la PWA (vistas de experiencia, selección de franja, checkout).

### Fase 2: PWA Offline-First y Validación Territorial
- [ ] `T-2.1`: [Frontend] Configurar Service Workers (Workbox) y base de datos IndexedDB (`IntegrateOfflineDB`) para almacenamiento desconectado.
- [ ] `T-2.2`: [Backend] Implementar endpoints de descarga preventiva de padrón de vouchers y lista delta de revocación (`modules/offline/`).
- [ ] `T-2.3`: [Frontend] Implementar módulo de descarga preventiva y almacenamiento de padrón local en IndexedDB.
- [ ] `T-2.4`: [Frontend] Integrar lector QR con cámara web/móvil y motor de validación criptográfica offline (Web Crypto API) con marcación local anti-doble canje.

### Fase 3: Sincronización Diferida y Gobernanza
- [ ] `T-3.1`: [Frontend] Implementar cola de sincronización en IndexedDB y disparador de Background Sync al recuperar conectividad.
- [ ] `T-3.2`: [Backend] Implementar endpoint idempotente de sincronización por lotes (`POST /api/v1/offline/sync`) con resolución determinista de colisiones.
- [ ] `T-3.3`: [Backend] Implementar políticas de cancelación automatizadas (regla de 72 horas).
- [ ] `T-3.4`: [Frontend/Backend] Implementar flujo de enrolamiento asistido de anfitriones por parte del Gestor de Enlace.

### Fase 4: Hardening, Auditoría y Producción
- [ ] `T-4.1`: [Backend] Implementar auditoría inmutable de accesos a datos sensibles de víctimas (Habeas Data Ley 1581 / Ley 1448).
- [ ] `T-4.2`: [Backend] Pruebas de carga y estrés concurrente (JMeter o k6) para verificar ausencia de overbooking bajo contención pesimista.
- [ ] `T-4.3`: [Frontend] Auditoría de accesibilidad WCAG 2.1 AA / NTC 5854.

---

## 4. Bitácora de Avances

| Fecha | Tarea ID | Estado | Descripción del Avance | Archivos Afectados |
| :--- | :--- | :--- | :--- | :--- |
| 2026-10-05 | N/A | Completado | Inspección técnica inicial del repositorio, diagnóstico de dependencias y creación del archivo maestro `PROJECT_STATUS.md`. | `PROJECT_STATUS.md` |
| 2026-10-05 | T-0.1 | En desarrollo | Asignación de especificación técnica para Docker Compose y variables de entorno de base de datos. | `PROJECT_STATUS.md` |
| 2026-10-05 | N/A | Completado | Reubicación del archivo de seguimiento a carpeta dedicada `docs/status/`. | `docs/status/PROJECT_STATUS.md` |
| 2026-10-05 | T-0.1 | Completado | ADR-0001 aceptado. Docker Compose (PostgreSQL 16 por defecto; LocalStack S3 y Mailpit por perfiles), plantilla `.env.example`, `.gitignore` SEC-DIR-004, `.gitattributes` (LF), hook de bucket S3 y README de infra. Corrección de `backend_guidelines.md` §2 y §6.1. | `infra/docker/docker-compose.yml`, `infra/env/.env.example`, `infra/localstack/init/01_create_bucket.sh`, `infra/README.md`, `.gitignore`, `.gitattributes`, `docs/architecture/adr/ADR-0001-*.md` |
| 2026-10-05 | T-0.2 | Completado | Stack `--profile full` arriba y healthy. Bootstrap ejecutado: roles `integrate_owner/api/reporting` con login y timeouts, BD `integrate` (owner `integrate_owner`, UTC). Autenticación SCRAM verificada (contraseña errónea rechazada), bucket `integrate-vouchers-dev` creado, Mailpit 200, fail-fast sin env-file. | — |
| 2026-10-05 | T-0.3 | En desarrollo | Redacción de ADR-0002 para definición de stack base backend (Spring Boot 3.3.x, Java 21, Maven Wrapper, dependencias core/crypto/flyway/testing) y especificación técnica de `pom.xml`. | `docs/architecture/adr/ADR-0002-stack-base-backend-spring-boot.md`, `docs/status/PROJECT_STATUS.md` |
| 2026-10-05 | T-0.3 | Completado | ADR-0002 aceptado con cambio a Spring Boot 4.1.1 (3.3.x fuera de soporte OSS). `pom.xml` con starters modulares de Boot 4 (`webmvc`, `flyway`, `*-test`), Nimbus 10.10, `bcprov` 1.86 (solo Argon2, sin `bcpkix`), Testcontainers 2.0.5, ArchUnit 1.5.1, Lombok `optional` + `annotationProcessorPaths`. Maven Wrapper 3.3.4 `only-script` → Maven 3.9.16 con `distributionSha256Sum`. Verificado: `mvnw test-compile` BUILD SUCCESS y compilación de prueba (Lombok/Argon2/Nimbus) correcta. Referencias "Spring Boot 3.x" actualizadas en la documentación. | `backend-api/pom.xml`, `backend-api/mvnw`, `backend-api/mvnw.cmd`, `backend-api/.mvn/wrapper/maven-wrapper.properties`, `.gitignore`, `.gitattributes`, `docs/architecture/adr/ADR-0002-*.md`, `docs/architecture/backend_guidelines.md`, `docs/especificacion_tecnica.md`, `docs/roadmap_ejecucion.md` |
| 2026-10-05 | T-0.4 | En desarrollo | Asignación de especificación técnica para `IntegrateApplication.java` y perfiles de configuración `application.yml` y `application-dev.yml`. | `docs/status/PROJECT_STATUS.md` |
| 2026-10-05 | T-0.4 | Completado | `IntegrateApplication` en `com.integrate`. `application.yml`: DataSource `integrate_api` y Flyway `integrate_owner` por variables de entorno sin valores por defecto (fail-fast verificado: aborta sin `DATABASE_URL`), Hikari `integrate-api-pool` (connection-timeout 5s < statement_timeout 15s), Flyway con 9 esquemas (`common` + 8 de negocio; historial en `common`), `clean-disabled`, `ddl-auto: validate`, `open-in-view: false`, JDBC en UTC, errores HTTP sin stacktrace/mensaje. `application-dev.yml` importa `infra/env/.env.development` como properties. `.env.example` con `SERVER_PORT` y `DB_POOL_MAX_SIZE` opcionales. Verificado: `mvnw package` OK y arranque con perfil `dev` (Flyway desactivado, queda para T-0.5) en 3,5s con conexión a PostgreSQL como `integrate_api`; HTTP 401 por defecto (Spring Security). | `backend-api/src/main/java/com/integrate/IntegrateApplication.java`, `backend-api/src/main/resources/application.yml`, `backend-api/src/main/resources/application-dev.yml`, `infra/env/.env.example` |
| 2026-10-05 | T-0.5 | Completado | Migración Flyway `V1`→`V10` aplicada al arrancar con perfil `dev` (1,5s, 10/10 `success`; historial en `common.flyway_schema_history` con marcador `<< Flyway Schema Creation >>`). 49 tablas en 9 esquemas, todas propiedad de `integrate_owner`; extensiones `pg_trgm` y `btree_gist`. Privilegios verificados: `integrate_api` sin `CREATE` (DDL rechazado: `permission denied for schema auth`), con DML en `auth`, sin `UPDATE/DELETE/TRUNCATE` en `audit` (V9) y sin acceso a `flyway_schema_history`. Segundo arranque idempotente: 11 migraciones validadas, "No migration necessary". | — (sin cambios de código) |
| 2026-10-05 | T-0.6 | En desarrollo | Redacción de ADR-0003 (Propuesto): errores RFC 9457 con `ProblemDetail` nativo, catálogo `ErrorCode`, `GlobalExceptionHandler`, `TraceIdFilter` (`X-Trace-Id`), `Clock` UTC y reglas ArchUnit; eliminación del sobre `ApiResponse` por contradecir el contrato OpenAPI. | `docs/architecture/adr/ADR-0003-estructura-comun-errores-rfc9457-trazabilidad.md`, `docs/status/PROJECT_STATUS.md` |
| 2026-10-05 | T-0.6 | Completado | ADR-0003 aceptado. `ErrorCode` (21 códigos del catálogo §2; slugs nuevos `schedule-not-bookable` y `payment-provider-mismatch`, sin ejemplo en el contrato), `BusinessRuleException` con extensiones/cabeceras, `GlobalExceptionHandler` (422 validación con `invalidParams`, 400 `about:blank` sin reflejar la entrada, 404, 403 RBAC, 401, 503 + `Retry-After: 1` por lock/SQLState 55P03, 500 sin `detail`), `type` siempre presente (Spring 7 omitía `about:blank`), `InvalidParam`, `TraceIdFilter` (antes de Security; id entrante validado), `TimeConfig` (`Clock` UTC), `traceId` en el patrón de log. Pruebas: 18/18 (handler MVC, filtro, catálogo, ArchUnit). Verificado en ejecución real: 401 de Spring Security con `X-Trace-Id`. Guías y especificación sin `ApiResponse`. | `backend-api/src/main/java/com/integrate/common/**`, `backend-api/src/main/java/com/integrate/config/TimeConfig.java`, `backend-api/src/main/resources/application.yml`, `backend-api/src/test/java/com/integrate/**`, `docs/architecture/backend_guidelines.md`, `docs/especificacion_tecnica.md`, `docs/architecture/adr/ADR-0003-*.md` |
| 2026-10-05 | T-0.7 | En desarrollo | Asignación de especificación técnica para inicialización del frontend PWA (Vite, React, TypeScript, Tailwind CSS, estructura modular). Última tarea previa al hito de subida y cierre formal de Fase 0. | `docs/status/PROJECT_STATUS.md` |
| 2026-10-05 | T-0.7 | En desarrollo | Redacción de ADR-0004 (Propuesto): Vite 8, React 19, TypeScript 6 (TS 7 incompatible con `typescript-eslint`), Tailwind 4 CSS-first, React Router 8, ESLint 9 + `jsx-a11y` (ESLint 10 sin soporte), estructura por *features* con reglas de importación, cliente `fetch` con `ApiError` RFC 9457, `VITE_API_BASE_URL` fail-fast y proxy `/api` para la cookie `SameSite=Strict`. Service Worker/IndexedDB diferidos a T-2.1. | `docs/architecture/adr/ADR-0004-boilerplate-frontend-pwa-vite-react.md`, `docs/status/PROJECT_STATUS.md` |
| 2026-10-05 | T-0.7 | Completado | ADR-0004 aceptado, con enmienda: ESLint 10 + `eslint-plugin-jsx-a11y-x` (ESLint 9 marcado sin soporte por npm; `jsx-a11y` sin publicar desde 2024). `frontend-pwa/` con Vite 8.3, React 19.3, TS 6.0 estricto (`noUncheckedIndexedAccess`, `exactOptionalPropertyTypes`), Tailwind 4.3 con tokens AA/AAA y foco de doble anillo, React Router 8.4 (layouts Turista `/` y Gestor `/gestor`, 404, página de error con `traceId`), `createHttpClient` (`fetch`, `credentials: include`, token en memoria, `ApiError` RFC 9457 / `NetworkError`), `VITE_API_BASE_URL` fail-fast y proxy `/api`. Fronteras `shared`/`features`/`app` con ESLint (probadas con importaciones prohibidas). Verificado: `typecheck`, `lint` (0 avisos), `format:check`, 22/22 pruebas y `build` (100 KB gzip). En el navegador: pantalla de configuración incompleta sin `.env`, skip link como primer foco, navegación, 404 y vista móvil de 375 px sin scroll horizontal. Prueba de punta a punta contra el backend real (puerto 8081, porque Steam ocupa el 8080): 401 → `ApiError` con `X-Trace-Id`. `.gitignore` verificado en un repo temporal (`node_modules/`, `dist/` y `.env.development.local` excluidos; `.env.example` versionado). Sin commits (indicación del desarrollador). Cierre técnico de Fase 0. | `frontend-pwa/**`, `.claude/launch.json`, `docs/architecture/adr/ADR-0004-*.md`, `docs/status/PROJECT_STATUS.md` |
| 2026-10-05 | Fase 0 | Completado | Repositorio Git inicializado (`main`) y entrega de Fase 0 en un commit inicial con push a `origin`. Corrección de `.gitignore`: `ENV/` se anclaba a cualquier nivel y, con `core.ignorecase`, ignoraba `infra/env/` completo (incluida la plantilla `.env.example`); ahora `/ENV/`. Checklist SEC-DIR-004 verificado: `.env` reales ignorados y ninguna contraseña local presente en archivos versionados. | `.gitignore`, `docs/status/PROJECT_STATUS.md` |
| 2026-10-05 | T-1.1 / T-1.2 | En desarrollo | Inicio de Fase 1. Redacción de ADR-0005 (Propuesto): JWT RS256 validado con el resource server de Spring Security (sin filtro propio), llave de sesión por `JWT_SIGNING_KEY` (fail-fast), Argon2id directo (el prefijo `{argon2}` de `DelegatingPasswordEncoder` viola `ck_users_password_hash`), bloqueo 5/10 min con `FOR UPDATE`, refresh rotativo con detección de reutilización, 401/403 en RFC 9457. Brechas detectadas y propuestas: `/auth/register` (solo `TOURIST`), `/auth/logout` y `/auth/consents` ausentes del OpenAPI; `auth.privacy_policies` sin política vigente (→ `V11`); propagación de `integrate.actor_id`. Fuera de alcance: `register-assisted` y gestión de roles (T-3.4/T-3.5). | `docs/architecture/adr/ADR-0005-autenticacion-jwt-rbac-sesiones.md`, `docs/status/PROJECT_STATUS.md` |
| 2026-10-05 | T-1.1 / T-1.2 | En desarrollo | ADR-0005 aceptado con ajustes: `GET/POST /api/v1/me/consents` (en vez de `/auth/consents`); política de privacidad por seed repetible solo en `dev`/`test` (`v0.1` borrador), la `v1.0` aprobada llegará por migración antes de producción; primer `ADMIN` con `AdminBootstrapRunner` por variables `BOOTSTRAP_ADMIN_*`; ventana de gracia de 10 s en la detección de reutilización del refresh token. | `docs/architecture/adr/ADR-0005-*.md`, `docs/status/PROJECT_STATUS.md` |
| 2026-10-05 | T-1.1 | Completado | Dominio `auth`: 7 entidades (relaciones escalares; dominios `common.*` declarados en `columnDefinition` porque PostgreSQL los reporta como `Types.DISTINCT` y `ddl-auto: validate` los rechazaba), repositorios con `FOR UPDATE` (usuario y refresh token), DTOs del contrato y servicios: `AuthService` (verificación Argon2id fuera de transacción y hash ficticio para correos inexistentes; bloqueo 5 fallos/10 min → 15 min; rotación con `replaced_by_id`, gracia de 10 s y `REUSE_DETECTED` sobre todas las sesiones; logout idempotente; los rechazos confirman la transacción para no perder el intento), `RegistrationService` (solo `TOURIST`, 409 también ante la carrera del índice único, bootstrap del primer `ADMIN`), `ConsentService` (estado por finalidad y otorgamiento idempotente). Puerto `AccessTokenIssuer` implementado en `security/jwt` (RS256 + `kid`, `iss`, `jti`), llave por `JWT_SIGNING_KEY` con fail-fast (≥ 2048 bits, sin eco de la entrada), `Argon2PasswordEncoder` (19 MiB, t=2, p=1). Seed `R__dev_privacy_policy.sql` (v0.1) solo en `dev`/`test`. Starter `spring-boot-starter-security-oauth2-resource-server` agregado. Pruebas: 33 nuevas (Testcontainers con el mismo `00_bootstrap.sql`, la app como `integrate_api`); suite completa 51/51. |
| 2026-10-05 | T-1.2 | Completado | `AuthController` (`login`, `register` 201 + `Location`, `refresh`, `logout` 204) y `MeConsentController` (`GET/POST /api/v1/me/consents`). Cookie `refresh_token` `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`, borrada en logout y en todo 401 de refresh; `Cache-Control: no-store` en respuestas con token. `SecurityConfig` stateless y deny-by-default con el resource server (claim `roles` → authorities, `@EnableMethodSecurity`); en los `POST` públicos de `/api/v1/auth/**` se ignora `Authorization` para que un access token expirado no bloquee el refresh. 401/403 de la cadena en RFC 9457 con `traceId` (`ProblemAuthenticationEntryPoint` con `WWW-Authenticate: Bearer`, `ProblemAccessDeniedHandler`). CORS por `CORS_ALLOWED_ORIGINS` (vacío por defecto). Actor propagado a PostgreSQL con `ActorPropagatingTransactionManager` (`set_config(..., true)` por transacción). Contrato actualizado (`openapi.yaml`: `register`, `logout`, `me/consents` y esquemas, verificado con SnakeYAML: parsea y todas las `$ref` resuelven; `dtos_contrato_api.md`; `especificacion_tecnica.md` §5.1 sin `role`; `backend_guidelines.md` sin `JwtAuthenticationFilter`; notas de implementación en ADR-0005). `GlobalExceptionHandlerTest` acotado a su controlador de prueba. Pruebas: 18 nuevas (15 de API sobre la cadena real + 3 ArchUnit), suite 69/69. Verificado de punta a punta con `curl` contra el backend real (perfil `dev`, puerto 8081): registro, login con cookie, 401 RFC 9457, consentimientos, refresh con Bearer adjunto, logout y refresh revocado. Se agregó a `infra/env/.env.development` (local, ignorado) una llave RSA de desarrollo y la configuración `backend-api` a `.claude/launch.json`. | `backend-api/src/main/java/com/integrate/{modules/auth/controller/**,security/**}`, `backend-api/src/main/resources/application.yml`, `backend-api/src/test/**`, `docs/api/{openapi.yaml,dtos_contrato_api.md}`, `docs/especificacion_tecnica.md`, `docs/architecture/{backend_guidelines.md,adr/ADR-0005-*.md}`, `infra/env/.env.example`, `.claude/launch.json` | `backend-api/pom.xml`, `backend-api/src/main/java/com/integrate/{IntegrateApplication.java,modules/auth/**,security/**}`, `backend-api/src/main/resources/{application.yml,application-dev.yml,db/seed-dev/**}`, `backend-api/src/test/**`, `infra/env/.env.example` |



