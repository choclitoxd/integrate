# Graph Report - integrate  (2026-10-09)

## Corpus Check
- 371 files · ~149,276 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 9 file(s) not represented in the graph (top: (none) 4, .example 2, .properties 1)

## Summary
- 3128 nodes · 9300 edges · 165 communities (117 shown, 48 thin omitted)
- Extraction: 91% EXTRACTED · 9% INFERRED · 0% AMBIGUOUS · INFERRED: 810 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `0865fcea`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- RF-01 Autenticación y Control de Sesión (JWT)
- Esquema Catálogo (V3)
- jakarta.persistence.Entity
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
- 4. Bitácora de Avances
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
- HostExperienceResponse
- .uniqueEmail
- TraceIdFilter
- httpClient.ts
- booking.bookings
- compilerOptions
- ClientInfo
- .provisionHost
- PostgresIntegrationTest
- PaymentApiIntegrationTest
- Política Determinista de Protección Comunitaria (85% irrenunciable)
- BookingApiIntegrationTest
- VoucherApiIntegrationTest.java
- BusinessRuleException
- devDependencies
- Sistema Intégrate (Plataforma de Turismo Comunitario)
- compilerOptions
- package.json
- Actor: Gestor de Enlace Comunitario (Proxy Digital)
- org.springframework.data.jpa.repository.JpaRepository
- ADR-0004: Boilerplate de `frontend-pwa` — Vite 8 + React 19 + TypeScript 6 + Tailwind CSS 4, estructura por *features*
- list
- scripts
- catalog.host_profiles
- ConsentAndBootstrapIntegrationTest.java
- org.springframework.context.annotation.Configuration
- PaymentGatewayAdapter (Mock / Wompi)
- AssistedEnrollmentApiIntegrationTest
- dependencies
- .prettierrc.json
- routes.test.tsx
- vite-env.d.ts
- tsconfig.json
- Booking
- main.tsx
- StorageConfig.java
- EnrollmentDocuments.java
- SigningKeyRegistrar
- lombok.RequiredArgsConstructor
- .title
- AesGcmSensitiveDataCipher
- RouteErrorPage.tsx
- CommonArchitectureTest.java
- LinkManagerResponse
- RoleCode
- PROTOCOLO DE FLUJO DE TRABAJO EN GIT Y GOBERNANZA DE CAMBIOS
- Decisión
- org.junit.jupiter.api.Test
- AssistedEnrollmentServiceImpl.java
- CatalogAdminServiceImpl.java
- Proyecto heredado FoodExpress (a erradicar)
- IntegrateApplication
- .register
- PaymentWebhookServiceImpl.java
- ExperienceSpecifications.java
- CatalogQueryServiceImpl.java
- org.springframework.http.ResponseEntity
- CatalogApiIntegrationTest
- LoginRequest
- .grant
- Experience
- ExperienceStatus
- org.springframework.transaction.annotation.Transactional
- VoucherPdfRenderer.java
- ConsentLedger.java
- PaymentEventProcessor.java
- SecurityConfig.java
- map
- Directrices de Arquitectura Backend, Estructura Modular y Gobernanza Técnica
- ADR-0006: Catálogo territorial — experiencias, franjas, moderación y consulta pública
- duration
- BookingPayments
- BookingStatus
- org.springframework.stereotype.Component
- Decisión
- MockPaymentGatewayAdapter
- PaymentProvider
- 3. Autenticación
- BookingLockRequest
- .register
- SlotInventory
- DocumentKind
- Ficha Técnica y Delimitación del Esquema de Datos Offline en Cliente (IndexedDB)
- S3DocumentStorage.java
- jakarta.servlet.http.HttpServletRequest
- VoucherDocumentStorage
- BookingRequestHasher
- CatalogQueryServiceImpl
- org.springframework.web.bind.annotation.GetMapping
- Decisión
- org.springframework.stereotype.Service
- ProblemDetail
- VoucherQueryServiceImpl.java
- VoucherApiIntegrationTest
- org.springframework.context.annotation.Bean
- uuid
- PaymentMethod
- HostExperienceServiceImpl.java
- BookingExpirationServiceImpl.java
- RefreshToken
- ProblemAuthenticationEntryPoint.java
- .process
- org.springframework.web.bind.annotation.PostMapping
- WompiPaymentGatewayAdapter
- .activate
- clock
- jakarta.persistence.EntityManager
- BookingLockServiceImpl.java
- WompiPaymentGatewayAdapterTest
- BookingVouchersImpl.java
- Ley 1581 de 2012 (Habeas Data)
- Decisión
- errors.ts
- frontend-pwa · Intégrate
- LoginFailureReason
- ADR-0005: Autenticación JWT RS256, sesiones con refresh rotativo y RBAC del módulo `auth`
- java.net.InetAddress

## God Nodes (most connected - your core abstractions)
1. `BusinessRuleException` - 73 edges
2. `ErrorCode` - 61 edges
3. `ClientInfo` - 54 edges
4. `Experience` - 47 edges
5. `RoleCode` - 46 edges
6. `ConsentPurpose` - 45 edges
7. `HostExperienceResponse` - 45 edges
8. `BookingApiIntegrationTest` - 44 edges
9. `PaymentApiIntegrationTest` - 44 edges
10. `VoucherApiIntegrationTest` - 41 edges

## Surprising Connections (you probably didn't know these)
- `4. Cifrado AES-256-GCM de datos de víctima (`common/crypto`)` --references--> `SensitiveDataCipher`  [INFERRED]
  docs/architecture/adr/ADR-0007-enrolamiento-asistido-gestor-enlace.md → backend-api/src/main/java/com/integrate/common/crypto/SensitiveDataCipher.java
- `1. Alcance y rutas` --references--> `PageResponse`  [INFERRED]
  docs/architecture/adr/ADR-0007-enrolamiento-asistido-gestor-enlace.md → backend-api/src/main/java/com/integrate/common/payload/PageResponse.java
- `5. Documentos en S3: subida prefirmada (`common/storage`)` --references--> `DocumentStorage`  [INFERRED]
  docs/architecture/adr/ADR-0007-enrolamiento-asistido-gestor-enlace.md → backend-api/src/main/java/com/integrate/common/storage/DocumentStorage.java
- `3.1 `LoginRequest` → `LoginRequest.java`` --references--> `LoginRequest`  [INFERRED]
  docs/api/dtos_contrato_api.md → backend-api/src/main/java/com/integrate/modules/auth/dto/request/LoginRequest.java
- `3.2.1 `RegisterRequest` → `RegisterRequest.java` (`POST /api/v1/auth/register`)` --references--> `RegisterRequest`  [INFERRED]
  docs/api/dtos_contrato_api.md → backend-api/src/main/java/com/integrate/modules/auth/dto/request/RegisterRequest.java

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Desnormalizaciones controladas D1–D8** — docs_modelo_relacional_d1_available_slots, docs_modelo_relacional_d3_assignment_association, docs_modelo_relacional_d4_booking_price, docs_modelo_relacional_d5_hours_before_start, docs_modelo_relacional_d7_disbursement_association [EXTRACTED 1.00]
- **Operación territorial offline: padrón → validación → sync → conflicto** — docs_especificacion_tecnica_rf_16_descarga_padron, docs_especificacion_tecnica_rf_17_validacion_offline, docs_especificacion_tecnica_rf_18_background_sync, docs_especificacion_tecnica_indexeddb_stores, docs_modelo_relacional_sync_batches, docs_modelo_relacional_offline_validations, docs_modelo_relacional_sync_incidents [EXTRACTED 1.00]
- **Flujo transaccional reserva → pago → voucher** — docs_especificacion_tecnica_rf_09_bloqueo_pesimista, docs_especificacion_tecnica_rf_10_datos_medicos_reserva, docs_especificacion_tecnica_rf_12_pago_split, docs_especificacion_tecnica_rf_15_emision_voucher, docs_especificacion_tecnica_rf_11_expiracion_ttl_worker [INFERRED 0.85]
- **Salvaguarda del 85% comunitario** — docs_especificacion_tecnica_split_payment_85_15, docs_especificacion_tecnica_community_protection_policy, docs_especificacion_tecnica_cancellation_policy_72h, docs_modelo_relacional_disbursements, docs_modelo_relacional_disbursement_state_machine, docs_modelo_relacional_commission_policies, docs_roadmap_ejecucion_t_3_3_conflict_resolution [INFERRED 0.85]
- **Adaptadores de pasarela que producen CanonicalPaymentEvent** — docs_api_openapi_mockpaymentgatewayadapter, docs_api_openapi_wompipaymentgatewayadapter, docs_api_openapi_canonicalpaymentevent, docs_api_openapi_wompievent [INFERRED 0.85]
- **Operación territorial offline: padrón, validación, sincronización** — docs_especificacion_tecnica_rf_16_descarga_padron, docs_especificacion_tecnica_rf_17_validacion_offline, docs_especificacion_tecnica_rf_18_background_sync_conflictos, docs_especificacion_tecnica_integrateofflinedb, docs_modelo_relacional_offline_validations, docs_modelo_relacional_sync_incidents [INFERRED 0.85]

## Communities (165 total, 48 thin omitted)

### Community 0 - "RF-01 Autenticación y Control de Sesión (JWT)"
Cohesion: 0.22
Nodes (10): POST /api/v1/auth/login & refresh, RF-01 Autenticación y Control de Sesión (JWT), RF-02 Captura y Auditoría de Consentimiento Habeas Data, RF-03 Gestión y Asignación de Roles RBAC, Esquema auth (users, roles, user_roles, privacy_policies, habeas_data_consents, login_attempts, refresh_tokens), auth.habeas_data_consents (append-only), auth.refresh_tokens / login_attempts, auth.user_roles / auth.roles (RBAC N:M) (+2 more)

### Community 1 - "Esquema Catálogo (V3)"
Cohesion: 0.09
Nodes (39): catalog.categories, catalog.community_associations, catalog.departments, catalog.experience_categories, catalog.experience_images, catalog.experience_status_history, catalog.experiences, catalog.fn_experience_guard() (+31 more)

### Community 2 - "jakarta.persistence.Entity"
Cohesion: 0.22
Nodes (13): PgDomains, HabeasDataConsent, Role, UserRole, Category, Department, HostManagerAssignment, Municipality (+5 more)

### Community 3 - "V5__payments.sql"
Cohesion: 0.10
Nodes (36): ix_disbursements_association, ix_disbursements_batch, ix_disbursements_due, ix_disbursements_ready, ix_payment_transactions_booking, ix_payment_transactions_open, ix_payment_transactions_policy, ix_payment_webhook_events_payload (+28 more)

### Community 4 - "2.3 Fichas Técnicas de los Object Stores"
Cohesion: 0.12
Nodes (16): 2.3 Fichas Técnicas de los Object Stores, B. Object Store: `operational_roster`, C. Object Store: `revocation_list`, D. Object Store: `public_signing_keys`, Delimitación de Atributos Persistidos, Delimitación de Atributos Persistidos, Delimitación de Atributos Persistidos, Delimitación de Atributos Persistidos (+8 more)

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
Cohesion: 0.15
Nodes (13): 1. Endpoints, 4.1 Catálogo público, panel del anfitrión y moderación (ADR-0006), 6.1 `CanonicalPaymentEvent` (Mock, y salida del adaptador Wompi), 6.2 `WebhookAck` (200), 6.3 Modelo de estados, 6. Webhook de pasarela – `POST /payments/webhook/{mock|wompi}`, 7.1 `OfflineSyncBatchRequest`, 7.2 `OfflineSyncBatchResponse` (siempre 200 si el lote es válido) (+5 more)

### Community 20 - "DIRECTIVA DE SEGURIDAD Y GESTIÓN DE SECRETOS (SEC-DIR-004)"
Cohesion: 0.11
Nodes (17): 1. Principio Fundamental: Tolerancia Cero al "Hardcoding", 2.1 Configuración Mandatoria en `.gitignore`, 2.2 Plantillas `.env.example`, 2. Gestión de Entornos Locales y `.gitignore`, 3.1 GitHub Encrypted Secrets y Environments, 3.2 GitHub Secret Scanning y Push Protection, 3.3 Barrido en GitHub Actions (PR Gatekeeper), 3. Gobernanza de Secretos en GitHub y CI/CD (+9 more)

### Community 22 - "4. Bitácora de Avances"
Cohesion: 0.20
Nodes (9): 1.1 Diagnóstico de Artefactos Existentes, 1.2 Delimitación Formal de Plataforma: Aplicación Móvil (PWA Smartphone), 1. Estado y Arquitectura Detectada, 2.1 Cobertura de Módulos por Etapa, 2. Estructuración del Proyecto en Dos Etapas, 4. Bitácora de Avances, Estado del Proyecto Intégrate (PROJECT_STATUS.md), **ETAPA 1: Procesos 1 y 2 (Núcleo de Identidades, Reserva Concurrente y Checkout)** (+1 more)

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
Cohesion: 0.05
Nodes (33): ErrorCode, AUTH_ACCOUNT_LOCKED, AUTH_ACCOUNT_SUSPENDED, AUTH_ACTIVATION_INVALID, AUTH_INVALID_CREDENTIALS, AUTH_REFRESH_TOKEN_INVALID, AUTH_TOKEN_INVALID, AUTH_USER_ALREADY_EXISTS (+25 more)

### Community 39 - "HostExperienceResponse"
Cohesion: 0.12
Nodes (4): PageResponse, HostExperienceResponse, ManagedHostResponse, CatalogModerationService

### Community 40 - ".uniqueEmail"
Cohesion: 0.18
Nodes (3): ConsentAcceptance, ConsentAndBootstrapIntegrationTest, RegistrationServiceIntegrationTest

### Community 42 - "httpClient.ts"
Cohesion: 0.22
Nodes (7): NetworkError, createHttpClient(), HttpClient, HttpClientOptions, HttpMethod, RequestOptions, clientReturning()

### Community 43 - "booking.bookings"
Cohesion: 0.13
Nodes (13): BookingExpirationWorker (@Scheduled 60s), RF-10 Reserva y Captura de Datos Médicos, RF-11 Expiración Automática de Cupos por TTL, RF-13 Cancelación con Reembolso (>72h), RF-14 Retención Comunitaria (<=72h / No-Show), booking.booking_cancellations, booking.booking_participants (EPS cifrada), Esquema booking (+5 more)

### Community 44 - "compilerOptions"
Cohesion: 0.08
Nodes (23): compilerOptions, allowImportingTsExtensions, erasableSyntaxOnly, exactOptionalPropertyTypes, jsx, lib, module, moduleDetection (+15 more)

### Community 45 - "ClientInfo"
Cohesion: 0.15
Nodes (7): User, AuthErrors, IssuedSession, SessionIssuer, AuthenticatedUser, ClientInfo, Session

### Community 46 - ".provisionHost"
Cohesion: 0.12
Nodes (4): AssistedAccountServiceImpl, Emails, PasswordPolicy, PasswordPolicyTest

### Community 49 - "Política Determinista de Protección Comunitaria (85% irrenunciable)"
Cohesion: 0.18
Nodes (10): POST /api/v1/offline/sync, OfflineSyncService / ConflictResolutionService, RF-08 Moderación y Suspensión Preventiva, RF-18 Background Sync y Resolución Determinista de Conflictos, RF-18 Background Sync y Resolución de Conflictos, Máquina de estados de Dispersión (CANCELLED→RELEASED solo en SYNC_CONFLICT), Máquina de estados de Experiencia, R-04 Reclamaciones por cancelación con discrepancia offline (+2 more)

### Community 51 - "VoucherApiIntegrationTest.java"
Cohesion: 0.08
Nodes (3): SensitiveDataException, RoleAssignmentService, AuthTestData

### Community 52 - "BusinessRuleException"
Cohesion: 0.13
Nodes (3): BusinessRuleException, BookingErrors, PaymentErrors

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

### Community 59 - "org.springframework.data.jpa.repository.JpaRepository"
Cohesion: 0.09
Nodes (9): CredentialActivationCode, CredentialActivationCodeRepository, PrivacyPolicyRepository, BookingRepository, Schedule, ExperienceCategoryCode, ExperienceCategoryRepository, ScheduleRepository (+1 more)

### Community 60 - "ADR-0004: Boilerplate de `frontend-pwa` — Vite 8 + React 19 + TypeScript 6 + Tailwind CSS 4, estructura por *features*"
Cohesion: 0.20
Nodes (9): ADR-0004: Boilerplate de `frontend-pwa` — Vite 8 + React 19 + TypeScript 6 + Tailwind CSS 4, estructura por *features*, Alternativas Descartadas, Consecuencias, Contexto, Contrato o Esquema Conceptual, Decisión, Enmienda (2026-10-05, implementación), Nota de renumeración (2026-10-07) (+1 more)

### Community 61 - "list"
Cohesion: 0.10
Nodes (16): RegisterRequest, ConsentPurpose, HEALTH_EMERGENCY, PERSONAL_DATA, SENSITIVE_VICTIM, DocType, CC, CE (+8 more)

### Community 62 - "scripts"
Cohesion: 0.20
Nodes (10): scripts, build, dev, format, format:check, lint, preview, test (+2 more)

### Community 63 - "catalog.host_profiles"
Cohesion: 0.16
Nodes (13): BookingConcurrencyEngine, RNF-02 Latencia Transaccional de Aforo < 200 ms, RNF-02 Latencia de Aforo < 200 ms, RNF-03 Consistencia ACID y 0% Overbooking, Esquema catalog (DIVIPOLA, asociaciones, perfiles, experiencias, franjas), catalog.departments / municipalities (DIVIPOLA), catalog.experiences, catalog.host_manager_assignments (+5 more)

### Community 64 - "ConsentAndBootstrapIntegrationTest.java"
Cohesion: 0.15
Nodes (7): AdminBootstrapRunner, AdminBootstrapCommand, BootstrapOutcome, ADMIN_ALREADY_EXISTS, CREATED, IDENTITY_TAKEN, RegistrationService

### Community 65 - "org.springframework.context.annotation.Configuration"
Cohesion: 0.21
Nodes (4): BookingSchedulingConfig, SweepScheduling, VoucherAsyncConfig, ActorPropagationConfig

### Community 66 - "PaymentGatewayAdapter (Mock / Wompi)"
Cohesion: 0.40
Nodes (5): Pasarela de Pagos / Split Payment (Wompi / ePayco), PaymentGatewayAdapter (Mock / Wompi), R-06 Caída del proveedor de pagos (circuit breaker Resilience4j), T-1.5 Adaptador de Pagos y Split Payment, T-1.6 Generador de Voucher Criptográfico PDF/QR

### Community 68 - "dependencies"
Cohesion: 0.50
Nodes (4): dependencies, react, react-dom, react-router

### Community 69 - ".prettierrc.json"
Cohesion: 0.50
Nodes (3): printWidth, singleQuote, trailingComma

### Community 70 - "routes.test.tsx"
Cohesion: 0.29
Nodes (5): routes, @testing-library/jest-dom, @testing-library/react, @testing-library/user-event, vitest

### Community 74 - "main.tsx"
Cohesion: 0.26
Nodes (9): ConfigErrorScreen(), ConfigErrorScreenProps, bootstrap(), container, AppConfig, ConfigError, getConfig(), parseConfig() (+1 more)

### Community 76 - "EnrollmentDocuments.java"
Cohesion: 0.16
Nodes (6): DocumentStorage, PresignedUpload, StoredObject, S3DocumentStorage, EnrollmentDocuments, InMemoryDocumentStorage

### Community 77 - "SigningKeyRegistrar"
Cohesion: 0.09
Nodes (7): KeyPair, RsaSigningKeys, SigningKeyRepository, ActiveSigningKey, SigningKeyRegistrar, JwtConfig, RsaSigningKeysTest

### Community 78 - "lombok.RequiredArgsConstructor"
Cohesion: 0.19
Nodes (4): PageRequests, AssistedRegistrationController, DevPaymentSimulatorController, PaymentCheckoutController

### Community 80 - ".title"
Cohesion: 0.17
Nodes (11): 2. Formato transversal de errores (RFC 9457), ADR-0003: Estructura base común — errores RFC 9457, catálogo de códigos y trazabilidad `X-Trace-Id`, Alternativas Descartadas, Consecuencias, Contexto, Contrato o Esquema Conceptual, Decisión, 4. Experiencias: autoría del anfitrión y máquina de estados (+3 more)

### Community 81 - "AesGcmSensitiveDataCipher"
Cohesion: 0.12
Nodes (5): AesGcmSensitiveDataCipher, ActivationCodes, ShortCodes, AesGcmSensitiveDataCipherTest, ActivationCodesTest

### Community 82 - "RouteErrorPage.tsx"
Cohesion: 0.23
Nodes (8): RouteErrorPage(), Button(), ButtonProps, Variant, VARIANT_CLASSES, SkipLink(), SkipLinkProps, react

### Community 83 - "CommonArchitectureTest.java"
Cohesion: 0.19
Nodes (5): AuthArchitectureTest, CommonArchitectureTest, ModuleBoundariesTest, Alternativas Descartadas, Contexto

### Community 84 - "LinkManagerResponse"
Cohesion: 0.14
Nodes (8): AdminLinkManagerController, LinkManagerRequest, LinkManagerResponse, AccreditationStatus, ACTIVE, REVOKED, SUSPENDED, LinkManagerAdminService

### Community 85 - "RoleCode"
Cohesion: 0.09
Nodes (20): RoleCode, ADMIN, HOST, LINK_MANAGER, TOURIST, UserStatus, ACTIVE, ANONYMIZED (+12 more)

### Community 86 - "PROTOCOLO DE FLUJO DE TRABAJO EN GIT Y GOBERNANZA DE CAMBIOS"
Cohesion: 0.20
Nodes (9): 1. Ciclo de Vida y Estados de un Cambio en Git, 2. ¿Cuándo Hacer Commit? (Reglas de Compromiso Local), 3. ¿Cuándo Hacer Push y Subir Cambios a GitHub? (Política por Fases), 4.1 Formato Estandarizado de Mensajes (Conventional Commits), 4.2 Anatomía del Diff: Qué SÍ y Qué NO debe contener un commit, 4. ¿Qué Esperar en Cada Cambio? (Estructura y Anatomía del Diff), Ciclo Operativo por Fases:, PROTOCOLO DE FLUJO DE TRABAJO EN GIT Y GOBERNANZA DE CAMBIOS (+1 more)

### Community 88 - "Decisión"
Cohesion: 0.14
Nodes (15): 3.2.2 Consentimientos del titular (`/api/v1/me/consents`), 3.2 `AuthTokenResponse` (login y refresh), 10. Actor de la transacción para PostgreSQL, 11. Estructura y entidades, 12. Pruebas (criterio de aceptación de T-1.1/T-1.2), 1. Validación del access token con el *resource server* de Spring Security (sin filtro JWT propio), 2. Llave de firma de sesión por variable de entorno (SEC-DIR-004), 3. Hash de contraseñas: Argon2id directo (+7 more)

### Community 89 - "org.junit.jupiter.api.Test"
Cohesion: 0.10
Nodes (3): ErrorCodeTest, GlobalExceptionHandlerTest, AuthApiIntegrationTest

### Community 90 - "AssistedEnrollmentServiceImpl.java"
Cohesion: 0.08
Nodes (16): ConsentChannel, ASSISTED_PROXY, SELF_SERVICE, CredentialActivationService, IssuedActivationCode, ConsentReceipt, CommunityAssociation, HostProfile (+8 more)

### Community 91 - "CatalogAdminServiceImpl.java"
Cohesion: 0.09
Nodes (14): AssociationRequest, HostProfileRequest, AssociationResponse, HostProfileResponse, AssociationStatus, ACTIVE, SUSPENDED, AssociationType (+6 more)

### Community 92 - "Proyecto heredado FoodExpress (a erradicar)"
Cohesion: 0.33
Nodes (6): Proyecto heredado FoodExpress (a erradicar), Fase 0: Setup e Infraestructura / DB, Fase 1: MVP Core Transaccional, Fase 2: PWA Offline-First y Validación Territorial, Fase 3: Sincronización, Enrolamiento Asistido y Finanzas, Fase 4: Auditoría, Hardening y Cloud AWS

### Community 93 - "IntegrateApplication"
Cohesion: 0.32
Nodes (3): IntegrateApplication, 5. Estrategia de Ramificación (Branching Model), Flujo de Integración:

### Community 94 - ".register"
Cohesion: 0.14
Nodes (8): Actors, GestorController, DocumentUploadRequest, ActivationCodeResponse, AssistedHostRegistrationResponse, DocumentUploadResponse, AssistedEnrollmentService, GestorHostService

### Community 95 - "PaymentWebhookServiceImpl.java"
Cohesion: 0.11
Nodes (14): PaymentWebhookController, PaymentSimulationRequest, WebhookAck, GatewayStatus, APPROVED, DECLINED, ERROR, PENDING (+6 more)

### Community 97 - "CatalogQueryServiceImpl.java"
Cohesion: 0.08
Nodes (14): PublicCatalogController, CategoryResponse, ExperienceAvailabilityResponse, ScheduleAvailability, ExperienceDetailResponse, ExperienceSummaryResponse, PlaceResponse, PhysicalIntensity (+6 more)

### Community 98 - "org.springframework.http.ResponseEntity"
Cohesion: 0.16
Nodes (5): AuthController, RefreshCookies, ActivationRequest, AuthTokenResponse, UserResponse

### Community 101 - "LoginRequest"
Cohesion: 0.21
Nodes (3): LoginRequest, AuthService, AuthServiceIntegrationTest

### Community 102 - ".grant"
Cohesion: 0.57
Nodes (3): MeConsentController, ConsentStatusResponse, PurposeResponse

### Community 103 - "Experience"
Cohesion: 0.20
Nodes (3): Experience, ExperienceContent, ExperienceSpecifications

### Community 104 - "ExperienceStatus"
Cohesion: 0.15
Nodes (8): ExperienceStatus, APPROVED, DRAFT, PENDING_REVIEW, REJECTED, RETIRED, SUSPENDED, CatalogModerationServiceImpl

### Community 105 - "org.springframework.transaction.annotation.Transactional"
Cohesion: 0.13
Nodes (5): InvalidParam, ExperienceRequest, CatalogErrors, HostExperienceServiceImpl, SlotInventoryImpl

### Community 108 - "ConsentLedger.java"
Cohesion: 0.10
Nodes (14): ConsentAction, GRANTED, REVOKED, PrivacyPolicy, HabeasDataConsentRepository, ConsentLedger, ConsentServiceImpl, ConsentStatus (+6 more)

### Community 109 - "PaymentEventProcessor.java"
Cohesion: 0.10
Nodes (15): AuditEntry, ScheduleSettlement, PaymentStatus, APPROVED, ERROR, INITIATED, PENDING, REJECTED (+7 more)

### Community 110 - "SecurityConfig.java"
Cohesion: 0.06
Nodes (9): AccessTokenIssuer, IssuedToken, ActorPropagatingTransactionManager, JwtAccessTokenIssuer, PublicAuthEndpoints, SecurityConfig, Notas de implementación (T-1.1 / T-1.2, 2026-10-05), 8. Seguridad, contrato y pruebas (+1 more)

### Community 112 - "Directrices de Arquitectura Backend, Estructura Modular y Gobernanza Técnica"
Cohesion: 0.14
Nodes (14): 1.1 Patrón Arquitectónico, 1.2 Stack Tecnológico Confirmado, 1. Resumen Arquitectónico y Stack Confirmado, 2. Árbol Completo de Directorios (Formato ASCII), 3. Diccionario de Carpetas y Responsabilidades, 4.1 Ubicación Exacta de Componentes Técnicos, 4. Mapeo de Componentes Técnicos y Aislamiento de Esquemas, 5.1 Matriz de Invocación entre Capas (+6 more)

### Community 113 - "ADR-0006: Catálogo territorial — experiencias, franjas, moderación y consulta pública"
Cohesion: 0.25
Nodes (7): ADR-0006: Catálogo territorial — experiencias, franjas, moderación y consulta pública, Alternativas Descartadas, Consecuencias, Contexto, Contrato o Esquema Conceptual, Nota de renumeración y enmienda de alcance (2026-10-07), Notas de implementación (T-1.3, 2026-10-07)

### Community 114 - "duration"
Cohesion: 0.13
Nodes (4): SensitiveDataProperties, AdminBootstrapProperties, CorsProperties, JwtProperties

### Community 115 - "BookingPayments"
Cohesion: 0.16
Nodes (6): BookingPayments, BookingPaymentsImpl, PayableBooking, 7. Worker de expiración (T-1.6), 2. Módulo `payment` y fronteras, 3. Inicio del pago (`POST /bookings/{id}/payment`)

### Community 116 - "BookingStatus"
Cohesion: 0.12
Nodes (15): BookingStatus, CANCELLED_BY_PLATFORM, CANCELLED_NO_REFUND, CANCELLED_REFUNDED, CONFIRMED, EXECUTED, EXPIRED, NO_SHOW (+7 more)

### Community 117 - "org.springframework.stereotype.Component"
Cohesion: 0.13
Nodes (11): BookingExpirationWorker, ScheduleDirectory, Executor, Sweep, VoucherProperties, VoucherRepository, BookingConfirmedListener, VoucherIssuer (+3 more)

### Community 118 - "Decisión"
Cohesion: 0.12
Nodes (14): 10. Fuera de alcance, 5. Mapeo de estados, 6. Disparo del voucher (T-1.8) sin acoplar módulos, 7. Secretos y configuración (SEC-DIR-004), 8. Errores nuevos (`ErrorCode`), 9. Pruebas, ADR-0009: Cobrar con un `PaymentGatewayAdapter` desacoplado y confirmar la reserva con un webhook firmado e idempotente que deja el 85% en custodia, Alternativas Descartadas (+6 more)

### Community 119 - "MockPaymentGatewayAdapter"
Cohesion: 0.18
Nodes (3): Mock, Digests, MockPaymentGatewayAdapter

### Community 120 - "PaymentProvider"
Cohesion: 0.16
Nodes (6): PaymentProvider, EPAYCO, MOCK, WOMPI, PaymentGatewayAdapter, PaymentGateways

### Community 121 - "3. Autenticación"
Cohesion: 0.29
Nodes (7): 3.1 `LoginRequest` → `LoginRequest.java`, 3.2.1 `RegisterRequest` → `RegisterRequest.java` (`POST /api/v1/auth/register`), 3.2.3 Logout, 3.3 `AssistedHostRegistrationRequest`, 3.4 `AssistedHostRegistrationResponse` (201), 3.5 Gestor de Enlace y activación asistida (ADR-0007), 3. Autenticación

### Community 122 - "BookingLockRequest"
Cohesion: 0.14
Nodes (5): BookingLockRequest, EmergencyContact, Participant, BookingLockService, LockOutcome

### Community 124 - ".register"
Cohesion: 0.17
Nodes (5): AssistedConsent, AssistedHostRegistrationRequest, VictimData, VictimCiphertexts, AssistedEnrollmentServiceImpl

### Community 125 - "SlotInventory"
Cohesion: 0.13
Nodes (16): BookingLockResponse, BookingQueryService, BookingQueryServiceImpl, SlotInventory, 10. Puntos de decisión resueltos con el desarrollador (2026-10-09), 11. Fuera de alcance, 1. Alcance y rutas, 2. Frontera entre módulos: `catalog` es el dueño del inventario (+8 more)

### Community 126 - "DocumentKind"
Cohesion: 0.24
Nodes (3): DocumentKind, PHYSICAL_CONSENT_FORM, VICTIM_SUPPORT_DOC

### Community 127 - "Ficha Técnica y Delimitación del Esquema de Datos Offline en Cliente (IndexedDB)"
Cohesion: 0.14
Nodes (13): 1. Matriz de Delimitación de Alcance de Datos (Inclusión vs. Exclusión), 2.1 Identificación de la Base Local, 2.2 Estructura de Almacenes Locales (6 Object Stores), 2. Especificación Arquitectónica de los Object Stores, 3.1 Metadatos de Sincronización por Registro, 3.2 Especificación Conceptual de la Cola de Sincronización (`sync_outbox`), 3.3 Matriz de Políticas de Resolución de Conflictos, 3. Protocolo Conceptual de Sincronización y Outbox (+5 more)

### Community 130 - "jakarta.servlet.http.HttpServletRequest"
Cohesion: 0.14
Nodes (5): ClientInfos, RegisterResponse, BookingController, RequestClients, 6. Contrato: cuatro endpoints nuevos en `openapi.yaml` y `dtos_contrato_api.md`

### Community 131 - "VoucherDocumentStorage"
Cohesion: 0.08
Nodes (23): PresignedDownload, VoucherDocumentStorage, BookingVouchers, BookingConfirmedEvent, 10. Pruebas, 11. Fuera de alcance, 1. Alcance y rutas, 2. Token: JWT RS256 con claims mínimos (RF-15, RNF-05) (+15 more)

### Community 132 - "BookingRequestHasher"
Cohesion: 0.80
Nodes (3): BookingRequestHasher, Canonical, CanonicalParticipant

### Community 133 - "CatalogQueryServiceImpl"
Cohesion: 0.11
Nodes (7): CategoryRepository, DepartmentRepository, ExperienceRepository, MunicipalityRepository, CatalogQueryServiceImpl, ExperienceViews, ScheduleDirectoryImpl

### Community 134 - "org.springframework.web.bind.annotation.GetMapping"
Cohesion: 0.15
Nodes (7): VoucherController, VoucherResponse, VoucherQueryService, Participant, ProbeController, ProbeRequest, ProbeController

### Community 135 - "Decisión"
Cohesion: 0.22
Nodes (9): 1. Alcance y rutas, 2. Acreditación de gestores por el administrador (RF-03), 3. Registro asistido: una sola transacción, 4. Cifrado AES-256-GCM de datos de víctima (`common/crypto`), 5. Documentos en S3: subida prefirmada (`common/storage`), 6. Activación asistida de la cuenta (`PENDING_RESET → ACTIVE`), 7. Errores nuevos (`ErrorCode`), 9. Fuera de alcance (+1 more)

### Community 136 - "org.springframework.stereotype.Service"
Cohesion: 0.14
Nodes (13): CredentialStatus, ACTIVE, PENDING_RESET, LoginAttemptRepository, UserRepository, AuthProperties, Lockout, AuthServiceImpl (+5 more)

### Community 137 - "ProblemDetail"
Cohesion: 0.14
Nodes (14): 3. Backlog Detallado de Tareas Técnicas por Etapa, Cimiento Base: Infraestructura y Persistencia (Fase 0 - Completada), ETAPA 1: Identidades, Reserva Concurrente y Checkout Transaccional (Procesos 1 y 2), ETAPA 2: Validación en Campo y Dispersión Financiera (Proceso 3), Fase 1.1: Backend - Núcleo Transaccional e Identidades (Procesos 1 y 2), Fase 1.2: Frontend - Interfaces PWA Móvil y Portales de Usuario (Procesos 1 y 2), Fase 2.1: Backend - Sincronización Diferida, Dispersión y Auditoría (Proceso 3), Fase 2.2: Frontend - PWA Móvil Offline-First y Validación Territorial (Proceso 3) (+6 more)

### Community 138 - "VoucherQueryServiceImpl.java"
Cohesion: 0.15
Nodes (4): VoucherSubject, ScheduleSummary, VoucherClaims, VoucherSigner

### Community 141 - "org.springframework.context.annotation.Bean"
Cohesion: 0.13
Nodes (4): TimeConfig, PasswordHashingConfig, MutableClock, TestClockConfig

### Community 143 - "PaymentMethod"
Cohesion: 0.15
Nodes (12): PaymentStartRequest, PaymentCheckoutResponse, PaymentMethod, CARD, DAVIPLATA, NEQUI, PSE, PaymentCheckoutServiceImpl (+4 more)

### Community 144 - "HostExperienceServiceImpl.java"
Cohesion: 0.16
Nodes (9): StatusReasonContext, ScheduleBatchRequest, Slot, ScheduleResponse, ScheduleStatus, CANCELLED, CLOSED, OPEN (+1 more)

### Community 145 - "BookingExpirationServiceImpl.java"
Cohesion: 0.15
Nodes (10): Severity, CRITICAL, HIGH, INFO, WARNING, AuditTrail, BookingExpirationService, BookingExpirationServiceImpl (+2 more)

### Community 146 - "RefreshToken"
Cohesion: 0.15
Nodes (8): RefreshToken, RevokeReason, ADMIN, LOGOUT, PASSWORD_CHANGE, REUSE_DETECTED, ROTATED, RefreshTokenRepository

### Community 149 - "org.springframework.web.bind.annotation.PostMapping"
Cohesion: 0.32
Nodes (3): AdminCatalogController, HostExperienceController, ReasonRequest

### Community 154 - "BookingLockServiceImpl.java"
Cohesion: 0.26
Nodes (6): SensitiveDataCipher, ConsentService, BookingProperties, Expiration, BookingLockServiceImpl, 4. Cifrado de datos de salud y emergencia

### Community 156 - "BookingVouchersImpl.java"
Cohesion: 0.33
Nodes (4): BookingParticipant, BookingParticipantRepository, BookingVouchersImpl, Holder

### Community 157 - "Ley 1581 de 2012 (Habeas Data)"
Cohesion: 0.28
Nodes (5): Ley 1581 de 2012 (Habeas Data), RF-10 Reserva y Captura de Datos Médicos/Emergencia, RNF-01 Seguridad: AES-256-GCM y BCrypt/Argon2id, R-05 Fuga de datos de víctimas (Ley 1581), T-4.1 Auditoría Inmutable Habeas Data y Cifrado AES-256

### Community 159 - "Decisión"
Cohesion: 0.25
Nodes (8): 1. Alcance de T-1.3: anfitrión autónomo + moderación del administrador, 2. Municipios DIVIPOLA por migración versionada (`V11__divipola_municipalities.sql`), 3. Alta de asociaciones y anfitriones por el administrador (RF-03, mínimo viable), 5. Franjas (`schedules`), 6. Consulta pública (RF-04) — sin JWT, 7. Errores, contrato y estructura, 8. Pruebas (criterio de aceptación de T-1.3), Decisión

### Community 160 - "errors.ts"
Cohesion: 0.43
Nodes (6): ADR-0003, RFC-9457, InvalidParam, isInvalidParam(), isRecord(), toProblemDetail()

### Community 161 - "frontend-pwa · Intégrate"
Cohesion: 0.29
Nodes (5): 1. Preparar (una vez), 2. Desarrollo, 3. Calidad, 4. Estructura, frontend-pwa · Intégrate

### Community 162 - "LoginFailureReason"
Cohesion: 0.33
Nodes (5): LoginFailureReason, ACCOUNT_LOCKED, ACCOUNT_SUSPENDED, BAD_CREDENTIALS, UNKNOWN_USER

### Community 163 - "ADR-0005: Autenticación JWT RS256, sesiones con refresh rotativo y RBAC del módulo `auth`"
Cohesion: 0.33
Nodes (5): ADR-0005: Autenticación JWT RS256, sesiones con refresh rotativo y RBAC del módulo `auth`, Alternativas Descartadas, Consecuencias, Contrato o Esquema Conceptual, Nota de renumeración y enmienda de alcance (2026-10-07)

## Ambiguous Edges - Review These
- `booking.bookings` → `Esquema DDL inicial (spec §6)`  [AMBIGUOUS]
  docs/modelo_relacional.md · relation: semantically_similar_to
- `Migraciones Flyway V1–V9` → `T-0.4 Migración inicial Flyway (V1__initial_schema.sql)`  [AMBIGUOUS]
  docs/roadmap_ejecucion.md · relation: conceptually_related_to

## Knowledge Gaps
- **429 isolated node(s):** `com.integrate:backend-api`, `INFO`, `WARNING`, `HIGH`, `CRITICAL` (+424 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 747 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **48 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **What is the exact relationship between `booking.bookings` and `Esquema DDL inicial (spec §6)`?**
  _Edge tagged AMBIGUOUS (relation: semantically_similar_to) - confidence is low._
- **What is the exact relationship between `Migraciones Flyway V1–V9` and `T-0.4 Migración inicial Flyway (V1__initial_schema.sql)`?**
  _Edge tagged AMBIGUOUS (relation: conceptually_related_to) - confidence is low._
- **Why does `Especificación Técnica de Requerimientos Intégrate` connect `Sistema Intégrate (Plataforma de Turismo Comunitario)` to `Ficha Técnica y Delimitación del Esquema de Datos Offline en Cliente (IndexedDB)`?**
  _High betweenness centrality (0.094) - this node is a cross-community bridge._
- **Why does `4. Bitácora de Avances` connect `4. Bitácora de Avances` to `VoucherDocumentStorage`, `org.springframework.web.bind.annotation.GetMapping`, `GlobalExceptionHandler`, `ProblemDetail`, `org.springframework.context.annotation.Bean`, `HostExperienceServiceImpl.java`, `BookingLockServiceImpl.java`, `java.net.InetAddress`, `HostExperienceResponse`, `TraceIdFilter`, `httpClient.ts`, `VoucherApiIntegrationTest.java`, `ADR-0004: Boilerplate de `frontend-pwa` — Vite 8 + React 19 + TypeScript 6 + Tailwind CSS 4, estructura por *features*`, `list`, `ConsentAndBootstrapIntegrationTest.java`, `org.junit.jupiter.api.Test`, `IntegrateApplication`, `PaymentWebhookServiceImpl.java`, `org.springframework.http.ResponseEntity`, `LoginRequest`, `.grant`, `SecurityConfig.java`, `BookingPayments`, `org.springframework.stereotype.Component`, `Decisión`, `PaymentProvider`, `SlotInventory`?**
  _High betweenness centrality (0.094) - this node is a cross-community bridge._
- **Why does `Intégrate - Plataforma Transaccional de Turismo Comunitario` connect `Sistema Intégrate (Plataforma de Turismo Comunitario)` to `RF-17 Validación Offline de Voucher`?**
  _High betweenness centrality (0.066) - this node is a cross-community bridge._
- **What connects `com.integrate:backend-api`, `INFO`, `WARNING` to the rest of the system?**
  _429 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Esquema Catálogo (V3)` be split into smaller, more focused modules?**
  _Cohesion score 0.09393939393939393 - nodes in this community are weakly interconnected._