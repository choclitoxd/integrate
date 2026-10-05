# Graph Report - integrate  (2026-10-05)

## Corpus Check
- 140 files · ~73,835 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 9 file(s) not represented in the graph (top: (none) 4, .example 2, .properties 1)

## Summary
- 1491 nodes · 3027 edges · 101 communities (68 shown, 33 thin omitted)
- Extraction: 91% EXTRACTED · 9% INFERRED · 0% AMBIGUOUS · INFERRED: 275 edges (avg confidence: 0.86)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `25f2a6f4`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- RF-01 Autenticación y Control de Sesión (JWT)
- Esquema Catálogo (V3)
- RefreshToken
- V5__payments.sql
- 2.3 Fichas Técnicas de los Object Stores
- POST /api/v1/bookings/lock (lockBookingSlots)
- payments.disbursements (85% en custodia)
- GlobalExceptionHandler
- RF-17 Validación Offline de Voucher
- V2__auth.sql
- RF-09 Bloqueo Pesimista de Aforo
- V7__offline.sql
- V4__booking.sql
- V8__audit_notification.sql
- V6__voucher.sql
- V10__api_contract_alignment.sql
- Contrato de API y DTOs – Núcleo Transaccional Intégrate
- DIRECTIVA DE SEGURIDAD Y GESTIÓN DE SECRETOS (SEC-DIR-004)
- Actor: Administrador de la Plataforma
- 3. Backlog Detallado de Tareas Técnicas
- ADR-0001: Orquestar la infraestructura local con Docker Compose y variables de entorno segregadas por plantilla
- OfflineSyncItemResult
- Actor: Anfitrión Comunitario
- RNF-07 Accesibilidad WCAG 2.1 AA / NTC 5854
- Actor: Turista
- Procedimiento de Ejecución en 2 Pasos
- 01_create_bucket.sh
- Integrate API OpenAPI 3.1 spec
- RF-04 Consulta de Catálogo y Aforos
- ADR-0002: Definición del Stack Base de Construcción y Dependencias del Backend (Spring Boot 4.1.x, Java 21, Maven)
- com.integrate:backend-api
- routes.tsx
- ErrorCode
- GlobalExceptionHandlerTest.java
- org.junit.jupiter.api.Test
- TraceIdFilter.java
- 4. Bitácora de Avances
- booking.bookings
- compilerOptions
- RegistrationServiceImpl.java
- ConsentPurpose
- PostgresIntegrationTest.java
- Ley 1581 de 2012 (Habeas Data)
- Política Determinista de Protección Comunitaria (85% irrenunciable)
- Session
- ConsentAndBootstrapIntegrationTest.java
- BusinessRuleException
- devDependencies
- Sistema Intégrate (Plataforma de Turismo Comunitario)
- compilerOptions
- GlobalExceptionHandler.java
- package.json
- Actor: Gestor de Enlace Comunitario (Proxy Digital)
- org.springframework.data.jpa.repository.JpaRepository
- frontend-pwa · Intégrate
- SessionTransactions.java
- scripts
- catalog.host_profiles
- RegistrationService
- org.springframework.context.annotation.Bean
- PaymentGatewayAdapter (Mock / Wompi)
- 7. Sincronización offline – `POST /offline/sync`
- dependencies
- .prettierrc.json
- routes.test.tsx
- vite-env.d.ts
- tsconfig.json
- ConsentLedger
- main.tsx
- RsaSigningKeysTest.java
- RsaSigningKeys.java
- .load
- uuid
- .title
- JwtProperties
- RouteErrorPage.tsx
- CommonArchitectureTest.java
- ADR-0004: Boilerplate de `frontend-pwa` — Vite 8 + React 19 + TypeScript 6 + Tailwind CSS 4, estructura por *features*
- SessionTransactions
- PROTOCOLO DE FLUJO DE TRABAJO EN GIT Y GOBERNANZA DE CAMBIOS
- .requiresConsentUpdate
- Decisión
- DocType
- errors.ts
- LoginFailureReason
- Proyecto heredado FoodExpress (a erradicar)
- IntegrateApplication
- ADR-0005: Autenticación JWT RS256, sesiones con refresh rotativo y RBAC del módulo `auth`
- Emails
- 3. Autenticación
- AuthTokenResponse
- PgDomains

## God Nodes (most connected - your core abstractions)
1. `ErrorCode` - 37 edges
2. `GlobalExceptionHandler` - 30 edges
3. `BusinessRuleException` - 29 edges
4. `ClientInfo` - 28 edges
5. `User` - 27 edges
6. `ConsentPurpose` - 26 edges
7. `RoleCode` - 26 edges
8. `AuthServiceIntegrationTest` - 25 edges
9. `LoginRequest` - 24 edges
10. `compilerOptions` - 22 edges

## Surprising Connections (you probably didn't know these)
- `4.1 Ubicación Exacta de Componentes Técnicos` --references--> `GlobalExceptionHandler`  [INFERRED]
  docs/architecture/backend_guidelines.md → backend-api/src/main/java/com/integrate/common/exception/GlobalExceptionHandler.java
- `3.1 `LoginRequest` → `LoginRequest.java`` --references--> `LoginRequest`  [INFERRED]
  docs/api/dtos_contrato_api.md → backend-api/src/main/java/com/integrate/modules/auth/dto/request/LoginRequest.java
- `Alternativas Descartadas` --references--> `ProblemDetail`  [INFERRED]
  docs/architecture/adr/ADR-0003-estructura-comun-errores-rfc9457-trazabilidad.md → frontend-pwa/src/shared/api/errors.ts
- `4. Bitácora de Avances` --references--> `IntegrateApplication`  [INFERRED]
  docs/status/PROJECT_STATUS.md → backend-api/src/main/java/com/integrate/IntegrateApplication.java
- `2. Formato transversal de errores (RFC 9457)` --references--> `GlobalExceptionHandler`  [INFERRED]
  docs/api/dtos_contrato_api.md → backend-api/src/main/java/com/integrate/common/exception/GlobalExceptionHandler.java

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Desnormalizaciones controladas D1–D8** — docs_modelo_relacional_d1_available_slots, docs_modelo_relacional_d3_assignment_association, docs_modelo_relacional_d4_booking_price, docs_modelo_relacional_d5_hours_before_start, docs_modelo_relacional_d7_disbursement_association [EXTRACTED 1.00]
- **Operación territorial offline: padrón → validación → sync → conflicto** — docs_especificacion_tecnica_rf_16_descarga_padron, docs_especificacion_tecnica_rf_17_validacion_offline, docs_especificacion_tecnica_rf_18_background_sync, docs_especificacion_tecnica_indexeddb_stores, docs_modelo_relacional_sync_batches, docs_modelo_relacional_offline_validations, docs_modelo_relacional_sync_incidents [EXTRACTED 1.00]
- **Flujo transaccional reserva → pago → voucher** — docs_especificacion_tecnica_rf_09_bloqueo_pesimista, docs_especificacion_tecnica_rf_10_datos_medicos_reserva, docs_especificacion_tecnica_rf_12_pago_split, docs_especificacion_tecnica_rf_15_emision_voucher, docs_especificacion_tecnica_rf_11_expiracion_ttl_worker [INFERRED 0.85]
- **Salvaguarda del 85% comunitario** — docs_especificacion_tecnica_split_payment_85_15, docs_especificacion_tecnica_community_protection_policy, docs_especificacion_tecnica_cancellation_policy_72h, docs_modelo_relacional_disbursements, docs_modelo_relacional_disbursement_state_machine, docs_modelo_relacional_commission_policies, docs_roadmap_ejecucion_t_3_3_conflict_resolution [INFERRED 0.85]
- **Adaptadores de pasarela que producen CanonicalPaymentEvent** — docs_api_openapi_mockpaymentgatewayadapter, docs_api_openapi_wompipaymentgatewayadapter, docs_api_openapi_canonicalpaymentevent, docs_api_openapi_wompievent [INFERRED 0.85]
- **Operación territorial offline: padrón, validación, sincronización** — docs_especificacion_tecnica_rf_16_descarga_padron, docs_especificacion_tecnica_rf_17_validacion_offline, docs_especificacion_tecnica_rf_18_background_sync_conflictos, docs_especificacion_tecnica_integrateofflinedb, docs_modelo_relacional_offline_validations, docs_modelo_relacional_sync_incidents [INFERRED 0.85]

## Communities (101 total, 33 thin omitted)

### Community 0 - "RF-01 Autenticación y Control de Sesión (JWT)"
Cohesion: 0.22
Nodes (10): POST /api/v1/auth/login & refresh, RF-01 Autenticación y Control de Sesión (JWT), RF-02 Captura y Auditoría de Consentimiento Habeas Data, RF-03 Gestión y Asignación de Roles RBAC, Esquema auth (users, roles, user_roles, privacy_policies, habeas_data_consents, login_attempts, refresh_tokens), auth.habeas_data_consents (append-only), auth.refresh_tokens / login_attempts, auth.user_roles / auth.roles (RBAC N:M) (+2 more)

### Community 1 - "Esquema Catálogo (V3)"
Cohesion: 0.09
Nodes (39): catalog.categories, catalog.community_associations, catalog.departments, catalog.experience_categories, catalog.experience_images, catalog.experience_status_history, catalog.experiences, catalog.fn_experience_guard() (+31 more)

### Community 2 - "RefreshToken"
Cohesion: 0.13
Nodes (12): ConsentAction, GRANTED, REVOKED, ConsentChannel, ASSISTED_PROXY, SELF_SERVICE, HabeasDataConsent, LoginAttempt (+4 more)

### Community 3 - "V5__payments.sql"
Cohesion: 0.10
Nodes (36): ix_disbursements_association, ix_disbursements_batch, ix_disbursements_due, ix_disbursements_ready, ix_payment_transactions_booking, ix_payment_transactions_open, ix_payment_transactions_policy, ix_payment_webhook_events_payload (+28 more)

### Community 4 - "2.3 Fichas Técnicas de los Object Stores"
Cohesion: 0.06
Nodes (31): 1. Matriz de Delimitación de Alcance de Datos (Inclusión vs. Exclusión), 2.1 Identificación de la Base Local, 2.2 Estructura de Almacenes Locales (6 Object Stores), 2.3 Fichas Técnicas de los Object Stores, 2. Especificación Arquitectónica de los Object Stores, 3.1 Metadatos de Sincronización por Registro, 3.2 Especificación Conceptual de la Cola de Sincronización (`sync_outbox`), 3.3 Matriz de Políticas de Resolución de Conflictos (+23 more)

### Community 5 - "POST /api/v1/bookings/lock (lockBookingSlots)"
Cohesion: 0.07
Nodes (36): AccountLockedProblem, AssistedConsent, AssistedHostRegistrationRequest, AssistedHostRegistrationResponse, AuthenticatedUser, AuthTokenResponse, bearerAuth (JWT RS256, TTL 900 s), BookingConflictProblem (+28 more)

### Community 6 - "payments.disbursements (85% en custodia)"
Cohesion: 0.17
Nodes (19): POST /api/v1/payments/webhook/{mock|wompi}, Actor: Asociación Comunitaria de Turismo, NotificationDispatcherService (Email/WhatsApp adapters), RF-12 Procesamiento de Pago y Split Payment, RF-19 Conciliación y Dispersión Definitiva, RNF-06 Idempotencia en Pagos y Sincronización, Servicio de Notificaciones (Gmail/SES/Twilio), SplitPaymentService (+11 more)

### Community 8 - "RF-17 Validación Offline de Voucher"
Cohesion: 0.13
Nodes (22): GET /api/v1/offline/day-roster, Voucher Criptográfico JWT (RS256/Ed25519) en QR, Voucher Criptográfico JWT (RS256/Ed25519) en QR/PDF, IndexedDB Stores (crypto_keys, revoked_vouchers, local_consumed_vouchers), IntegrateOfflineDB (public_signing_keys, revocation_list, operational_roster, local_validations, sync_outbox), RF-15 Emisión de Voucher y Notificación, RF-16 Descarga Preventiva de Padrón y Revocación, RF-17 Validación Offline de Voucher (+14 more)

### Community 9 - "V2__auth.sql"
Cohesion: 0.16
Nodes (22): auth.habeas_data_consents, auth.login_attempts, auth.privacy_policies, auth.refresh_tokens, auth.roles, auth.user_roles, auth.users, auth.v_current_consents (+14 more)

### Community 10 - "RF-09 Bloqueo Pesimista de Aforo"
Cohesion: 0.18
Nodes (11): POST /api/v1/bookings/lock, POST /api/v1/bookings/lock-slots, AuditLogService, TTL de Reserva 15 minutos, RF-09 Bloqueo Pesimista de Aforo, RF-11 Worker de Expiración por TTL, RF-20 Bitácora Inmutable de Auditoría, RNF-09 Retención de Auditoría 10 años (+3 more)

### Community 11 - "V7__offline.sql"
Cohesion: 0.17
Nodes (16): ix_devices_manager, ix_offline_validations_batch, ix_offline_validations_device, ix_offline_validations_validated_by, ix_offline_validations_voucher, ix_roster_downloads_association, ix_roster_downloads_device, ix_roster_downloads_signing_key (+8 more)

### Community 12 - "V4__booking.sql"
Cohesion: 0.19
Nodes (15): booking.booking_cancellations, booking.booking_participants, booking.booking_status_history, booking.bookings, booking.fn_participant_guard(), booking.v_schedule_slot_reconciliation, ix_booking_status_history_booking, ix_bookings_pending_expiry (+7 more)

### Community 13 - "V8__audit_notification.sql"
Cohesion: 0.19
Nodes (14): audit.audit_trail, audit.chain_head, audit.fn_chain_append(), audit.verify_chain(), ix_audit_trail_action, ix_audit_trail_actor, ix_audit_trail_details, ix_audit_trail_entity (+6 more)

### Community 14 - "V6__voucher.sql"
Cohesion: 0.33
Nodes (8): ix_vouchers_issued_open, ix_vouchers_revoked_at, ix_vouchers_signing_key, trg_voucher_guard, uq_signing_keys_one_active, voucher.fn_voucher_guard(), voucher.signing_keys, voucher.vouchers

### Community 15 - "V10__api_contract_alignment.sql"
Cohesion: 0.22
Nodes (3): ix_devices_host, uq_bookings_idempotency, uq_offline_validations_local_id

### Community 17 - "Contrato de API y DTOs – Núcleo Transaccional Intégrate"
Cohesion: 0.18
Nodes (11): 1. Endpoints, 5.1 `BookingLockRequest`, 5.2 `BookingLockResponse` (201, o 200 con `Idempotency-Replayed: true`), 5.3 Mecanismo transaccional (prevención de sobreventa), 5. Bloqueo pesimista – `POST /bookings/lock`, 6.1 `CanonicalPaymentEvent` (Mock, y salida del adaptador Wompi), 6.2 `WebhookAck` (200), 6.3 Modelo de estados (+3 more)

### Community 20 - "DIRECTIVA DE SEGURIDAD Y GESTIÓN DE SECRETOS (SEC-DIR-004)"
Cohesion: 0.11
Nodes (17): 1. Principio Fundamental: Tolerancia Cero al "Hardcoding", 2.1 Configuración Mandatoria en `.gitignore`, 2.2 Plantillas `.env.example`, 2. Gestión de Entornos Locales y `.gitignore`, 3.1 GitHub Encrypted Secrets y Environments, 3.2 GitHub Secret Scanning y Push Protection, 3.3 Barrido en GitHub Actions (PR Gatekeeper), 3. Gobernanza de Secretos en GitHub y CI/CD (+9 more)

### Community 22 - "3. Backlog Detallado de Tareas Técnicas"
Cohesion: 0.15
Nodes (12): 1.1 Diagnóstico de Artefactos Existentes, 1. Estado y Arquitectura Detectada, 2. Roadmap por Módulos (Backend y Frontend), 3. Backlog Detallado de Tareas Técnicas, Estado del Proyecto Intégrate (PROJECT_STATUS.md), Fase 0: Entorno Base, Infraestructura y Persistencia, Fase 1: MVP Core Transaccional (Backend & Frontend), Fase 2: PWA Offline-First y Validación Territorial (+4 more)

### Community 23 - "ADR-0001: Orquestar la infraestructura local con Docker Compose y variables de entorno segregadas por plantilla"
Cohesion: 0.15
Nodes (11): ADR-0001: Orquestar la infraestructura local con Docker Compose y variables de entorno segregadas por plantilla, Alternativas Descartadas, Consecuencias, Contexto, Contrato o Esquema Conceptual, Decisión, 1. Preparar variables (una vez), 2. Levantar (+3 more)

### Community 25 - "OfflineSyncItemResult"
Cohesion: 0.29
Nodes (7): IncidentType, OfflineSyncBatchRequest, OfflineSyncBatchResponse, OfflineSyncItemResult, OfflineValidationItem, ServerOutcome, POST /api/v1/offline/sync (syncOfflineValidations)

### Community 31 - "Procedimiento de Ejecución en 2 Pasos"
Cohesion: 0.33
Nodes (5): 1. Paso Previo (ADR), 2. Validación, Procedimiento de Ejecución en 2 Pasos, PROTOCOLO OBLIGATORIO: GENERACIÓN PREVIA DE ADR, Regla de Ejecución

### Community 35 - "ADR-0002: Definición del Stack Base de Construcción y Dependencias del Backend (Spring Boot 4.1.x, Java 21, Maven)"
Cohesion: 0.29
Nodes (6): ADR-0002: Definición del Stack Base de Construcción y Dependencias del Backend (Spring Boot 4.1.x, Java 21, Maven), Alternativas Descartadas, Consecuencias, Contexto, Contrato o Esquema Conceptual, Decisión

### Community 37 - "routes.tsx"
Cohesion: 0.18
Nodes (12): AppShell(), AppShellProps, MAIN_CONTENT_ID, NavItem, ManagerLayout(), NAV_ITEMS, NAV_ITEMS, TouristLayout() (+4 more)

### Community 38 - "ErrorCode"
Cohesion: 0.08
Nodes (22): ErrorCode, AUTH_ACCOUNT_LOCKED, AUTH_ACCOUNT_SUSPENDED, AUTH_INVALID_CREDENTIALS, AUTH_REFRESH_TOKEN_INVALID, AUTH_TOKEN_INVALID, AUTH_USER_ALREADY_EXISTS, BOOKING_INSUFFICIENT_SLOTS (+14 more)

### Community 40 - "org.junit.jupiter.api.Test"
Cohesion: 0.06
Nodes (13): ConsentAcceptance, LoginRequest, RegisterRequest, AuthService, PasswordPolicy, ErrorCodeTest, GlobalExceptionHandlerTest, Participant (+5 more)

### Community 41 - "TraceIdFilter.java"
Cohesion: 0.06
Nodes (16): TraceIdFilter, TraceIdFilterTest, 1.1 Patrón Arquitectónico, 1.2 Stack Tecnológico Confirmado, 1. Resumen Arquitectónico y Stack Confirmado, 2. Árbol Completo de Directorios (Formato ASCII), 3. Diccionario de Carpetas y Responsabilidades, 4.1 Ubicación Exacta de Componentes Técnicos (+8 more)

### Community 42 - "4. Bitácora de Avances"
Cohesion: 0.19
Nodes (10): 4. Bitácora de Avances, ApiError, NetworkError, ProblemDetail, createHttpClient(), HttpClient, HttpClientOptions, HttpMethod (+2 more)

### Community 43 - "booking.bookings"
Cohesion: 0.13
Nodes (13): BookingExpirationWorker (@Scheduled 60s), RF-10 Reserva y Captura de Datos Médicos, RF-11 Expiración Automática de Cupos por TTL, RF-13 Cancelación con Reembolso (>72h), RF-14 Retención Comunitaria (<=72h / No-Show), booking.booking_cancellations, booking.booking_participants (EPS cifrada), Esquema booking (+5 more)

### Community 44 - "compilerOptions"
Cohesion: 0.08
Nodes (23): compilerOptions, allowImportingTsExtensions, erasableSyntaxOnly, exactOptionalPropertyTypes, jsx, lib, module, moduleDetection (+15 more)

### Community 45 - "RegistrationServiceImpl.java"
Cohesion: 0.13
Nodes (8): RoleCode, ADMIN, HOST, LINK_MANAGER, TOURIST, RoleRepository, UserRoleRepository, RegistrationServiceImpl

### Community 46 - "ConsentPurpose"
Cohesion: 0.13
Nodes (15): ConsentStatusResponse, PurposeResponse, ConsentPurpose, HEALTH_EMERGENCY, PERSONAL_DATA, SENSITIVE_VICTIM, ConsentService, ConsentServiceImpl (+7 more)

### Community 47 - "PostgresIntegrationTest.java"
Cohesion: 0.11
Nodes (3): MutableClock, PostgresIntegrationTest, TestClockConfig

### Community 48 - "Ley 1581 de 2012 (Habeas Data)"
Cohesion: 0.28
Nodes (5): Ley 1581 de 2012 (Habeas Data), RF-10 Reserva y Captura de Datos Médicos/Emergencia, RNF-01 Seguridad: AES-256-GCM y BCrypt/Argon2id, R-05 Fuga de datos de víctimas (Ley 1581), T-4.1 Auditoría Inmutable Habeas Data y Cifrado AES-256

### Community 49 - "Política Determinista de Protección Comunitaria (85% irrenunciable)"
Cohesion: 0.18
Nodes (10): POST /api/v1/offline/sync, OfflineSyncService / ConflictResolutionService, RF-08 Moderación y Suspensión Preventiva, RF-18 Background Sync y Resolución Determinista de Conflictos, RF-18 Background Sync y Resolución de Conflictos, Máquina de estados de Dispersión (CANCELLED→RELEASED solo en SYNC_CONFLICT), Máquina de estados de Experiencia, R-04 Reclamaciones por cancelación con discrepancia offline (+2 more)

### Community 50 - "Session"
Cohesion: 0.13
Nodes (9): AccessTokenIssuer, IssuedToken, AuthProperties, Lockout, IssuedSession, SessionIssuer, AuthenticatedUser, Session (+1 more)

### Community 52 - "BusinessRuleException"
Cohesion: 0.20
Nodes (4): BusinessRuleException, User, AuthErrors, ClientInfo

### Community 53 - "devDependencies"
Cohesion: 0.09
Nodes (23): devDependencies, eslint, eslint-config-prettier, @eslint/js, eslint-plugin-jsx-a11y-x, eslint-plugin-react-hooks, eslint-plugin-react-refresh, globals (+15 more)

### Community 54 - "Sistema Intégrate (Plataforma de Turismo Comunitario)"
Cohesion: 0.08
Nodes (17): Especificación Técnica de Requerimientos Intégrate, Módulos com.integrate (auth, catalog, booking, payment, voucher, offline, audit), Esquema DDL inicial (spec §6), Intégrate - Plataforma Transaccional de Turismo Comunitario, Ley 1448 de 2011 (Víctimas), Pasarela de Pagos Wompi / ePayco, Sistema Intégrate (Plataforma de Turismo Comunitario), Stack: Java 21/Spring Boot 3, React 18 PWA, PostgreSQL 16, AWS (+9 more)

### Community 55 - "compilerOptions"
Cohesion: 0.12
Nodes (16): compilerOptions, allowImportingTsExtensions, lib, module, moduleDetection, moduleResolution, noEmit, noUnusedLocals (+8 more)

### Community 57 - "package.json"
Cohesion: 0.08
Nodes (28): ADR-0004, noDeepFeatureImports, description, engines, node, name, private, type (+20 more)

### Community 58 - "Actor: Gestor de Enlace Comunitario (Proxy Digital)"
Cohesion: 0.20
Nodes (8): Actor: Gestor de Enlace Comunitario (Proxy Digital), RF-04 Consulta de Catálogo Territorial y Aforos, RF-05 Registro Autónomo de Propuesta de Experiencia, RF-06 Registro Asistido por Gestor de Enlace, RF-07 Revisión y Autorización de Experiencia, RNF-04 Arquitectura Offline-First y Resiliencia, catalog.link_manager_profiles, T-3.5 Módulo de Gestión Asistida (Proxy Digital)

### Community 60 - "frontend-pwa · Intégrate"
Cohesion: 0.29
Nodes (5): 1. Preparar (una vez), 2. Desarrollo, 3. Calidad, 4. Estructura, frontend-pwa · Intégrate

### Community 61 - "SessionTransactions.java"
Cohesion: 0.11
Nodes (13): CredentialStatus, ACTIVE, PENDING_RESET, RevokeReason, ADMIN, LOGOUT, PASSWORD_CHANGE, REUSE_DETECTED (+5 more)

### Community 62 - "scripts"
Cohesion: 0.20
Nodes (10): scripts, build, dev, format, format:check, lint, preview, test (+2 more)

### Community 63 - "catalog.host_profiles"
Cohesion: 0.16
Nodes (13): BookingConcurrencyEngine, RNF-02 Latencia Transaccional de Aforo < 200 ms, RNF-02 Latencia de Aforo < 200 ms, RNF-03 Consistencia ACID y 0% Overbooking, Esquema catalog (DIVIPOLA, asociaciones, perfiles, experiencias, franjas), catalog.departments / municipalities (DIVIPOLA), catalog.experiences, catalog.host_manager_assignments (+5 more)

### Community 64 - "RegistrationService"
Cohesion: 0.17
Nodes (8): AdminBootstrapRunner, AdminBootstrapCommand, BootstrapOutcome, ADMIN_ALREADY_EXISTS, CREATED, IDENTITY_TAKEN, RegistrationService, 9. RBAC y cadena de seguridad

### Community 65 - "org.springframework.context.annotation.Bean"
Cohesion: 0.21
Nodes (3): TimeConfig, JwtConfig, PasswordHashingConfig

### Community 66 - "PaymentGatewayAdapter (Mock / Wompi)"
Cohesion: 0.40
Nodes (5): Pasarela de Pagos / Split Payment (Wompi / ePayco), PaymentGatewayAdapter (Mock / Wompi), R-06 Caída del proveedor de pagos (circuit breaker Resilience4j), T-1.5 Adaptador de Pagos y Split Payment, T-1.6 Generador de Voucher Criptográfico PDF/QR

### Community 67 - "7. Sincronización offline – `POST /offline/sync`"
Cohesion: 0.50
Nodes (4): 7.1 `OfflineSyncBatchRequest`, 7.2 `OfflineSyncBatchResponse` (siempre 200 si el lote es válido), 7.3 Reglas de reconciliación (se evalúan en este orden), 7. Sincronización offline – `POST /offline/sync`

### Community 68 - "dependencies"
Cohesion: 0.50
Nodes (4): dependencies, react, react-dom, react-router

### Community 69 - ".prettierrc.json"
Cohesion: 0.50
Nodes (3): printWidth, singleQuote, trailingComma

### Community 70 - "routes.test.tsx"
Cohesion: 0.29
Nodes (5): routes, @testing-library/jest-dom, @testing-library/react, @testing-library/user-event, vitest

### Community 73 - "ConsentLedger"
Cohesion: 0.23
Nodes (3): HabeasDataConsentRepository, PrivacyPolicyRepository, ConsentLedger

### Community 74 - "main.tsx"
Cohesion: 0.26
Nodes (9): ConfigErrorScreen(), ConfigErrorScreenProps, bootstrap(), container, AppConfig, ConfigError, getConfig(), parseConfig() (+1 more)

### Community 78 - "uuid"
Cohesion: 0.26
Nodes (3): RegisterResponse, RegisteredUser, 6. Contrato: cuatro endpoints nuevos en `openapi.yaml` y `dtos_contrato_api.md`

### Community 80 - ".title"
Cohesion: 0.20
Nodes (9): 2. Formato transversal de errores (RFC 9457), 4. Disponibilidad – `GET /experiences/{id}/availability`, ADR-0003: Estructura base común — errores RFC 9457, catálogo de códigos y trazabilidad `X-Trace-Id`, Alternativas Descartadas, Consecuencias, Contexto, Contrato o Esquema Conceptual, Decisión (+1 more)

### Community 82 - "RouteErrorPage.tsx"
Cohesion: 0.23
Nodes (8): RouteErrorPage(), Button(), ButtonProps, Variant, VARIANT_CLASSES, SkipLink(), SkipLinkProps, react

### Community 84 - "ADR-0004: Boilerplate de `frontend-pwa` — Vite 8 + React 19 + TypeScript 6 + Tailwind CSS 4, estructura por *features*"
Cohesion: 0.20
Nodes (9): ADR-0004: Boilerplate de `frontend-pwa` — Vite 8 + React 19 + TypeScript 6 + Tailwind CSS 4, estructura por *features*, Alternativas Descartadas, Consecuencias, Contexto, Contrato o Esquema Conceptual, Decisión, Enmienda (2026-10-05, implementación), 5. Estrategia de Ramificación (Branching Model) (+1 more)

### Community 85 - "SessionTransactions"
Cohesion: 0.36
Nodes (3): UserRepository, AuthServiceImpl, SessionTransactions

### Community 86 - "PROTOCOLO DE FLUJO DE TRABAJO EN GIT Y GOBERNANZA DE CAMBIOS"
Cohesion: 0.20
Nodes (9): 1. Ciclo de Vida y Estados de un Cambio en Git, 2. ¿Cuándo Hacer Commit? (Reglas de Compromiso Local), 3. ¿Cuándo Hacer Push y Subir Cambios a GitHub? (Política por Fases), 4.1 Formato Estandarizado de Mensajes (Conventional Commits), 4.2 Anatomía del Diff: Qué SÍ y Qué NO debe contener un commit, 4. ¿Qué Esperar en Cada Cambio? (Estructura y Anatomía del Diff), Ciclo Operativo por Fases:, PROTOCOLO DE FLUJO DE TRABAJO EN GIT Y GOBERNANZA DE CAMBIOS (+1 more)

### Community 87 - ".requiresConsentUpdate"
Cohesion: 0.36
Nodes (5): 3.2 `AuthTokenResponse` (login y refresh), 12. Pruebas (criterio de aceptación de T-1.1/T-1.2), 4. Flujo de login (RF-01), 7. Consentimiento en el login, Contexto

### Community 88 - "Decisión"
Cohesion: 0.25
Nodes (8): 10. Actor de la transacción para PostgreSQL, 11. Estructura y entidades, 1. Validación del access token con el *resource server* de Spring Security (sin filtro JWT propio), 2. Llave de firma de sesión por variable de entorno (SEC-DIR-004), 3. Hash de contraseñas: Argon2id directo, 5. Refresh token rotativo, 8. Política vigente: seed solo en dev/test; la `v1.0` real llega por migración, Decisión

### Community 89 - "DocType"
Cohesion: 0.29
Nodes (6): DocType, CC, CE, PA, PPT, TI

### Community 90 - "errors.ts"
Cohesion: 0.43
Nodes (6): ADR-0003, RFC-9457, InvalidParam, isInvalidParam(), isRecord(), toProblemDetail()

### Community 91 - "LoginFailureReason"
Cohesion: 0.33
Nodes (5): LoginFailureReason, ACCOUNT_LOCKED, ACCOUNT_SUSPENDED, BAD_CREDENTIALS, UNKNOWN_USER

### Community 92 - "Proyecto heredado FoodExpress (a erradicar)"
Cohesion: 0.33
Nodes (6): Proyecto heredado FoodExpress (a erradicar), Fase 0: Setup e Infraestructura / DB, Fase 1: MVP Core Transaccional, Fase 2: PWA Offline-First y Validación Territorial, Fase 3: Sincronización, Enrolamiento Asistido y Finanzas, Fase 4: Auditoría, Hardening y Cloud AWS

### Community 94 - "ADR-0005: Autenticación JWT RS256, sesiones con refresh rotativo y RBAC del módulo `auth`"
Cohesion: 0.40
Nodes (4): ADR-0005: Autenticación JWT RS256, sesiones con refresh rotativo y RBAC del módulo `auth`, Alternativas Descartadas, Consecuencias, Contrato o Esquema Conceptual

### Community 97 - "3. Autenticación"
Cohesion: 0.50
Nodes (4): 3.1 `LoginRequest` → `LoginRequest.java`, 3.3 `AssistedHostRegistrationRequest`, 3.4 `AssistedHostRegistrationResponse` (201), 3. Autenticación

## Ambiguous Edges - Review These
- `Esquema DDL inicial (spec §6)` → `booking.bookings`  [AMBIGUOUS]
  docs/modelo_relacional.md · relation: semantically_similar_to
- `Migraciones Flyway V1–V9` → `T-0.4 Migración inicial Flyway (V1__initial_schema.sql)`  [AMBIGUOUS]
  docs/roadmap_ejecucion.md · relation: conceptually_related_to

## Knowledge Gaps
- **322 isolated node(s):** `com.integrate:backend-api`, `MALFORMED_REQUEST`, `VALIDATION_FAILED`, `RESOURCE_NOT_FOUND`, `RBAC_FORBIDDEN` (+317 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 492 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **33 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **What is the exact relationship between `Esquema DDL inicial (spec §6)` and `booking.bookings`?**
  _Edge tagged AMBIGUOUS (relation: semantically_similar_to) - confidence is low._
- **What is the exact relationship between `Migraciones Flyway V1–V9` and `T-0.4 Migración inicial Flyway (V1__initial_schema.sql)`?**
  _Edge tagged AMBIGUOUS (relation: conceptually_related_to) - confidence is low._
- **Why does `4. Bitácora de Avances` connect `4. Bitácora de Avances` to `RegistrationService`, `org.springframework.context.annotation.Bean`, `RefreshToken`, `GlobalExceptionHandler`, `org.junit.jupiter.api.Test`, `TraceIdFilter.java`, `ConsentPurpose`, `Session`, `ADR-0004: Boilerplate de `frontend-pwa` — Vite 8 + React 19 + TypeScript 6 + Tailwind CSS 4, estructura por *features*`, `3. Backlog Detallado de Tareas Técnicas`, `IntegrateApplication`?**
  _High betweenness centrality (0.147) - this node is a cross-community bridge._
- **Why does `Especificación Técnica de Requerimientos Intégrate` connect `Sistema Intégrate (Plataforma de Turismo Comunitario)` to `2.3 Fichas Técnicas de los Object Stores`?**
  _High betweenness centrality (0.143) - this node is a cross-community bridge._
- **Why does `Intégrate - Plataforma Transaccional de Turismo Comunitario` connect `Sistema Intégrate (Plataforma de Turismo Comunitario)` to `RF-17 Validación Offline de Voucher`?**
  _High betweenness centrality (0.115) - this node is a cross-community bridge._
- **Are the 6 inferred relationships involving `GlobalExceptionHandler` (e.g. with `2. Formato transversal de errores (RFC 9457)` and `Decisión`) actually correct?**
  _`GlobalExceptionHandler` has 6 INFERRED edges - model-reasoned connections that need verification._
- **What connects `com.integrate:backend-api`, `MALFORMED_REQUEST`, `VALIDATION_FAILED` to the rest of the system?**
  _322 weakly-connected nodes found - possible documentation gaps or missing edges._